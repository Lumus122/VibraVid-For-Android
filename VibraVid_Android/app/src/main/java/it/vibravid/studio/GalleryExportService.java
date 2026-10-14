package it.vibravid.studio;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import androidx.media3.common.*;
import androidx.media3.common.util.Clock;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.transformer.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-started exports continue outside the player; no upstream is used for offline media. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class GalleryExportService extends Service {
    private static final int NOTIFICATION=4201;
    private static final String CHANNEL="vibravid_gallery",CANCEL="it.vibravid.studio.CANCEL_GALLERY";
    public interface Listener{void changed(State state);}
    public static final class State {
        public final String key,message;public final int progress;public final boolean active;public final Uri uri;final long token;
        State(long token,String key,String message,int progress,boolean active,Uri uri){this.token=token;this.key=key;this.message=message;this.progress=progress;this.active=active;this.uri=uri;}
    }
    private static volatile State state=new State(0,"","",0,false,null);
    private static final java.util.Set<Listener> listeners=new CopyOnWriteArraySet<>();
    public static State state(){return state;}
    public static void addListener(Listener listener){listeners.add(listener);listener.changed(state);}
    public static void removeListener(Listener listener){listeners.remove(listener);}
    public static void start(Context context,String id,Uri uri,String name){
        Intent intent=new Intent(context,GalleryExportService.class).putExtra("download_id",id).putExtra("name",name).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startForegroundService(intent);
    }
    public static void cancel(Context context){context.startService(new Intent(context,GalleryExportService.class).setAction(CANCEL));}

    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private static final Object FILE_LOCK=new Object();
    private final Object commitLock=new Object();
    private volatile Uri committedUri;
    private Transformer transformer;
    private volatile GalleryWriter writer;
    private volatile File temporary;
    private android.os.PowerManager.WakeLock wake;
    private RuntimeException startupError;
    private long token;private String jobKey="",title="Video";private boolean busy,ended;private volatile boolean assembling;
    private final Runnable poll=new Runnable(){@Override public void run(){
        if(transformer==null||ended||cancelled.get())return;
        ProgressHolder progress=new ProgressHolder();
        if(transformer.getProgress(progress)==Transformer.PROGRESS_STATE_AVAILABLE)update("Creazione MP4",Math.min(90,progress.progress*9/10));
        main.postDelayed(this,500);
    }};
    @Override public void onCreate(){super.onCreate();
        try{
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,AppLanguage.text(this,"Salvataggio in galleria"),NotificationManager.IMPORTANCE_LOW));
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        if(power!=null){wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"VibraVid:gallery");wake.setReferenceCounted(false);}
        }catch(RuntimeException error){startupError=error;}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&CANCEL.equals(intent.getAction())){abort();return START_NOT_STICKY;}
        if(busy){if(ended)stopSelf(startId);return START_NOT_STICKY;}
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        String id=intent.getStringExtra("download_id");Uri uri=intent.getData();
        if(id==null&&(uri==null||!"content".equals(uri.getScheme()))){finish("Scarica prima il video nell’app per salvarlo in galleria.",null);return START_NOT_STICKY;}
        busy=true;token=System.nanoTime();jobKey=id!=null?"offline-"+id:"position-"+uri.toString().hashCode();
        title=intent.getStringExtra("name");if(title==null||title.trim().isEmpty())title="Video";
        try{
            if(startupError!=null)throw startupError;
            Notification notification=notification("Preparazione del video",-1);
            if(Build.VERSION.SDK_INT>=35)startForeground(NOTIFICATION,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
            else if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTIFICATION,notification);
        if(wake!=null)wake.acquire(6*60*60*1000L);update("Preparazione del video",-1);
        }catch(RuntimeException error){fail(error);return START_NOT_STICKY;}
        worker.execute(()->{
            try{
                synchronized(FILE_LOCK){checkCancelled();GalleryWriter.recover(this);
                    File[] abandoned=getCacheDir().listFiles((directory,name)->name.startsWith("vibravid-gallery-")&&name.endsWith(".mp4"));
                    if(abandoned!=null)for(File file:abandoned)file.delete();}
                checkCancelled();
                if(id!=null){
                    MediaEngine engine=MediaEngine.get(this);DownloadRequest request=engine.completed(id);
                    boolean adaptive="application/x-mpegURL".equals(request.mimeType)||"application/dash+xml".equals(request.mimeType);
                    if(adaptive){
                        assembling=true;
                        temporary=File.createTempFile("vibravid-gallery-",".mp4",getCacheDir());
                        main.post(()->transform(request,engine));
                    }else{
                        DataSource source=engine.offlineFactory().createDataSource();
                        try{long length=source.open(new DataSpec.Builder().setUri(request.uri).build());save(source,length,request.mimeType);}
                        finally{source.close();}
                    }
                }else{
                    String mime=getContentResolver().getType(uri);
                    try(InputStream stream=getContentResolver().openInputStream(uri)){
                        if(stream==null)throw new FileNotFoundException("Il video del dispositivo non è accessibile.");
                        save(stream,mime);
                    }
                }
            }catch(Exception error){fail(error);}
        });return START_NOT_STICKY;
    }
    private void transform(DownloadRequest request,MediaEngine engine){
        if(cancelled.get()||ended){removeTemporary();return;}
        try{
            transformer=new Transformer.Builder(this)
                .setMuxerFactory(new InAppMuxer.Factory.Builder().build())
                .setAssetLoaderFactory(new ExoPlayerAssetLoader.Factory(this,new DefaultDecoderFactory.Builder(this).build(),Clock.DEFAULT,new DefaultMediaSourceFactory(engine.offlineFactory())))
                .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(new Transformer.Listener(){
                    @Override public void onCompleted(Composition composition,ExportResult result){
                        main.removeCallbacks(poll);transformer=null;
                        if(cancelled.get()||ended){removeTemporary();return;}
                        try{worker.execute(()->{try(InputStream stream=new FileInputStream(temporary)){save(stream,"video/mp4");}catch(Exception error){fail(error);}finally{removeTemporary();}});}
                        catch(RejectedExecutionException error){if(!cancelled.get())fail(error);}
                    }
                    @Override public void onError(Composition composition,ExportResult result,ExportException error){main.removeCallbacks(poll);transformer=null;fail(error);}
                }).build();
            update("Creazione MP4",0);transformer.start(new EditedMediaItem.Builder(request.toMediaItem()).build(),temporary.getAbsolutePath());main.post(poll);
        }catch(RuntimeException error){fail(error);}
    }
    private void save(DataSource source,long length,String mime)throws IOException {
        saveVideo((buffer)->source.read(buffer,0,buffer.length),length,mime);
    }
    private void save(InputStream stream,String mime)throws IOException{
        long length=temporary!=null?temporary.length():-1;saveVideo(stream::read,length,mime);
    }
    private interface Reader{int read(byte[] buffer)throws IOException;}
    private void saveVideo(Reader input,long length,String mime)throws IOException{
        synchronized(FILE_LOCK){
        checkCancelled();if(mime==null||!mime.startsWith("video/"))mime="video/mp4";
        String extension=mime.equals("video/webm")?".webm":mime.contains("matroska")?".mkv":mime.equals("video/3gpp")?".3gp":mime.equals("video/quicktime")?".mov":mime.equals("video/mp2t")?".ts":".mp4";
        GalleryWriter sink=new GalleryWriter(this,title,mime,extension);writer=sink;
        try{
            byte[] buffer=new byte[256*1024];long bytes=0,nextUpdate=0;
            try(OutputStream output=sink.open()){
                int count;while((count=input.read(buffer))!=-1){checkCancelled();output.write(buffer,0,count);bytes+=count;
                    if(SystemClock.elapsedRealtime()>=nextUpdate){int percent=length>0?(int)Math.min(99,bytes*100/length):-1;if(assembling&&percent>=0)percent=90+percent/10;postUpdate("Salvataggio in galleria",percent);nextUpdate=SystemClock.elapsedRealtime()+400;}
                }output.flush();
            }
            checkCancelled();if(bytes<=0)throw new IOException("Il video è vuoto. Ripara il download e riprova.");
            sink.validate();
            synchronized(commitLock){checkCancelled();committedUri=sink.publishValidated();}
            main.post(()->finish("Salvato in galleria · Filmati/VibraVid",committedUri));
        }finally{sink.close();writer=null;}
        }
    }
    private void checkCancelled()throws InterruptedIOException{if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Salvataggio annullato.");}
    private void postUpdate(String message,int percent){main.post(()->{if(!ended&&!cancelled.get())update(message,percent);});}
    private void update(String message,int percent){
        if(ended)return;state=new State(token,jobKey,message,percent,true,null);notifyListeners();
        try{getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(message,percent));}catch(RuntimeException error){android.util.Log.w("VibraVid","Notifica del salvataggio",error);}
    }
    private static void notifyListeners(){for(Listener listener:listeners)try{listener.changed(state);}catch(RuntimeException error){android.util.Log.w("VibraVid","Aggiornamento del salvataggio",error);}}
    private void fail(Exception error){main.post(()->{
        if(ended||cancelled.get())return;
        android.util.Log.e("VibraVid","Salvataggio in galleria non riuscito",error);
        String message=MediaEngine.isStorageFull(error)?"Spazio esaurito. Libera spazio e riprova.":error instanceof SecurityException?"Consenti l’accesso al video per salvarlo in galleria. Selezionalo di nuovo se il permesso è scaduto.":error instanceof FileNotFoundException?"Il video del dispositivo non è più accessibile. Selezionalo nuovamente e riprova.":error instanceof ExportException?"Non è stato possibile creare il file MP4. Il formato potrebbe non essere compatibile oppure mancano parti del download.":error.getMessage();
        if(error instanceof IllegalStateException)message="Android non ha avviato il salvataggio. Riapri il lettore e riprova; il download originale è conservato.";
        if(message==null||message.length()>300)message="Il salvataggio non è riuscito. Controlla spazio, permessi e integrità del video.";
        finish(message,null);
    });}
    private Notification notification(String message,int percent){
        PendingIntent cancel=PendingIntent.getService(this,4201,new Intent(this,GalleryExportService.class).setAction(CANCEL),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open=PendingIntent.getActivity(this,4202,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_download).setContentTitle(title).setContentText(AppLanguage.text(this,message)).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(100,Math.max(0,percent),percent<0).addAction(new Notification.Action.Builder(null,AppLanguage.text(this,"Annulla"),cancel).build()).build();
    }
    private void finish(String message,Uri uri){
        if(ended)return;ended=true;main.removeCallbacks(poll);
        state=new State(token,jobKey,message,uri==null?0:100,false,uri);notifyListeners();
        releaseWake();try{stopForeground(true);}catch(RuntimeException error){android.util.Log.w("VibraVid","Chiusura del salvataggio",error);}
        if(busy&&!cancelled.get())try{
            PendingIntent open=PendingIntent.getActivity(this,4204,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            getSystemService(NotificationManager.class).notify(4203,new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_download).setContentTitle(uri==null?AppLanguage.text(this,"Salvataggio non riuscito"):title).setContentText(AppLanguage.text(this,message)).setContentIntent(open).setAutoCancel(true).build());
        }catch(RuntimeException error){android.util.Log.w("VibraVid","Notifica di completamento",error);}
        stopSelf();
    }
    private void abort(){
        synchronized(commitLock){if(committedUri!=null){finish("Salvato in galleria · Filmati/VibraVid",committedUri);return;}cancelled.set(true);}
        if(transformer!=null){transformer.cancel();transformer=null;}worker.shutdownNow();finish("Salvataggio annullato",null);
    }
    private void removeTemporary(){File file=temporary;if(file!=null){file.delete();temporary=null;}}
    private void releaseWake(){try{if(wake!=null&&wake.isHeld())wake.release();}catch(RuntimeException error){android.util.Log.w("VibraVid","Rilascio del salvataggio",error);}}
    @Override public void onTimeout(int startId,int type){abort();}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){
        synchronized(commitLock){cancelled.set(true);}main.removeCallbacksAndMessages(null);if(transformer!=null){transformer.cancel();transformer=null;}
        worker.shutdownNow();removeTemporary();releaseWake();
        if(!ended&&busy){state=new State(token,jobKey,"Salvataggio interrotto. Puoi riprovare.",0,false,null);notifyListeners();}
        ended=true;super.onDestroy();
    }
}
