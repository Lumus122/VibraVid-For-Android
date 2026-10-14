package it.vibravid.studio;

import android.Manifest;
import android.content.*;
import android.database.Cursor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.util.UUID;

/** A gallery entry becomes visible only after the complete video has been validated. */
final class GalleryWriter implements AutoCloseable {
    private final Context context;
    private final String mime, filename;
    private Uri uri;
    private File file;
    private boolean published;
    private long duration;
    private int width,height;

    GalleryWriter(Context context,String title,String mime,String extension)throws IOException {
        this.context=context.getApplicationContext();this.mime=mime;
        if(Build.VERSION.SDK_INT<29&&context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Consenti la scrittura dei video per salvare in galleria.");
        String clean=title==null?"Video":title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();
        clean=clean.replaceAll("^\\.+|\\.+$","").trim();if(clean.isEmpty())clean="Video";if(clean.length()>90)clean=clean.substring(0,90);
        filename=clean+"_"+System.currentTimeMillis()+"_"+UUID.randomUUID().toString().substring(0,6)+extension;
        try {
            if(Build.VERSION.SDK_INT>=29){
                ContentValues values=values();values.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/VibraVid");
                values.put(MediaStore.Video.Media.IS_PENDING,1);
                uri=context.getContentResolver().insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),values);
                if(uri==null)throw new IOException("La galleria non ha creato il file. Controlla lo spazio libero.");
            }else{
                File directory=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),"VibraVid");
                if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("La cartella Filmati/VibraVid non è accessibile.");
                file=new File(directory,filename);
                if(!file.createNewFile())throw new IOException("Non è stato possibile creare il video.");
            }
            synchronized(GalleryWriter.class){
                if(!preferences().edit().putString("uri",uri==null?"":uri.toString()).putString("path",file==null?"":file.getAbsolutePath()).commit())
                    throw new IOException("Non è stato possibile registrare il salvataggio.");
            }
        }catch(IOException|RuntimeException error){close();throw error;}
    }
    private android.content.SharedPreferences preferences(){return context.getSharedPreferences("gallery_pending",0);}
    private ContentValues values(){ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.DISPLAY_NAME,filename);values.put(MediaStore.Video.Media.TITLE,filename);values.put(MediaStore.Video.Media.MIME_TYPE,mime);values.put(MediaStore.Video.Media.DATE_ADDED,System.currentTimeMillis()/1000);return values;}
    OutputStream open()throws IOException {
        OutputStream out=file!=null?new FileOutputStream(file):context.getContentResolver().openOutputStream(uri,"w");
        if(out==null)throw new IOException("Il file della galleria non è scrivibile.");return out;
    }
    void validate()throws IOException {
        MediaExtractor extractor=new MediaExtractor();boolean video=false;
        try{
            if(file!=null)extractor.setDataSource(file.getAbsolutePath());else extractor.setDataSource(context,uri,null);
            for(int index=0;index<extractor.getTrackCount();index++){
                MediaFormat format=extractor.getTrackFormat(index);String type=format.getString(MediaFormat.KEY_MIME);
                if(type!=null&&type.startsWith("video/")){extractor.selectTrack(index);if(extractor.getSampleTime()>=0)video=true;extractor.unselectTrack(index);
                    if(format.containsKey(MediaFormat.KEY_DURATION))duration=Math.max(duration,format.getLong(MediaFormat.KEY_DURATION)/1000);
                    if(format.containsKey(MediaFormat.KEY_WIDTH))width=format.getInteger(MediaFormat.KEY_WIDTH);
                    if(format.containsKey(MediaFormat.KEY_HEIGHT))height=format.getInteger(MediaFormat.KEY_HEIGHT);
                }
            }
        }catch(RuntimeException error){throw new IOException("Il file non contiene un video leggibile. Riprova il salvataggio.",error);}
        finally{extractor.release();}
        if(!video)throw new IOException("Il file salvato non contiene una traccia video completa.");
    }
    Uri publish()throws IOException {validate();return publishValidated();}
    Uri publishValidated()throws IOException {
        ContentValues values=new ContentValues();
        if(Build.VERSION.SDK_INT>=29){
            // Duration/dimensions are read-only on modern MediaStore. Publishing triggers scanning.
            values.put(MediaStore.Video.Media.IS_PENDING,0);
            if(context.getContentResolver().update(uri,values,null,null)!=1)throw new IOException("La galleria non ha confermato il salvataggio.");
        }else{
            if(duration>0)values.put(MediaStore.Video.Media.DURATION,duration);
            if(width>0)values.put(MediaStore.Video.Media.WIDTH,width);
            if(height>0)values.put(MediaStore.Video.Media.HEIGHT,height);
            values.put(MediaStore.Video.Media.DATE_TAKEN,System.currentTimeMillis());
            values.putAll(values());values.put(MediaStore.Video.Media.DATA,file.getAbsolutePath());values.put(MediaStore.Video.Media.SIZE,file.length());
            uri=context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);
            if(uri==null)throw new IOException("Il video non è stato registrato nella galleria.");
        }
        published=true;clearOwnRecord();return uri;
    }
    private void clearOwnRecord(){synchronized(GalleryWriter.class){
        String recordedUri=preferences().getString("uri",""),recordedPath=preferences().getString("path","");
        if((file!=null&&file.getAbsolutePath().equals(recordedPath))||(uri!=null&&uri.toString().equals(recordedUri)))preferences().edit().clear().commit();
    }}
    @Override public void close(){
        boolean cleaned=true;
        if(!published){if(uri!=null)try{context.getContentResolver().delete(uri,null,null);}catch(RuntimeException error){cleaned=false;android.util.Log.w("VibraVid","Rimozione del video incompleto",error);}
            if(file!=null&&file.exists()&&!file.delete()){cleaned=false;android.util.Log.w("VibraVid","File incompleto della galleria ancora presente");}}
        if(cleaned)clearOwnRecord();
    }
    static synchronized void recover(Context context){
        android.content.SharedPreferences prefs=context.getSharedPreferences("gallery_pending",0);
        String uri=prefs.getString("uri",""),path=prefs.getString("path","");
        try{
            if(!uri.isEmpty()&&Build.VERSION.SDK_INT>=29){
                Uri pending=Uri.parse(uri);
                if(!"content".equals(pending.getScheme())||!"media".equals(pending.getAuthority()))return;
                try(Cursor cursor=context.getContentResolver().query(pending,new String[]{MediaStore.Video.Media.IS_PENDING},null,null,null)){
                    if(cursor!=null&&cursor.moveToFirst()&&cursor.getInt(0)==1&&context.getContentResolver().delete(pending,null,null)<=0)throw new IOException("Rimozione del salvataggio incompleto non riuscita.");
                }
            }else if(!path.isEmpty()&&Build.VERSION.SDK_INT<29){
                File file=new File(path),directory=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),"VibraVid");
                if(!file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile()))return;
                boolean registered=false;
                try(Cursor cursor=context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Video.Media._ID},MediaStore.Video.Media.DATA+"=?",new String[]{path},null)){registered=cursor!=null&&cursor.moveToFirst();}
                if(!registered&&file.exists()&&!file.delete())throw new IOException("Rimozione del salvataggio incompleto non riuscita.");
            }
        }catch(IOException|RuntimeException error){android.util.Log.w("VibraVid","Recupero del salvataggio interrotto",error);return;}
        prefs.edit().clear().commit();
    }
}
