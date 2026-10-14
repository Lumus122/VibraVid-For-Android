package it.vibravid.studio.tests;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Real native controls and cache-only adaptive export on the disposable QA emulator. */
public final class GallerySmoke extends Instrumentation {
    private Activity activity,video;private Object engine;private ClassLoader loader;private Class<?> media,service,playerType;private Bundle arguments;
    private final JSONArray checks=new JSONArray();private final List<Uri> exports=new ArrayList<>();
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);this.arguments=arguments;start();}
    @Override public void onStart(){new Thread(()->{Bundle result=new Bundle();try{
        activity=startActivitySync(new Intent().setClassName("it.vibravid.studio","it.vibravid.studio.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        loader=activity.getClassLoader();media=loader.loadClass("it.vibravid.studio.MediaEngine");service=loader.loadClass("it.vibravid.studio.GalleryExportService");playerType=loader.loadClass("androidx.media3.common.Player");engine=media.getMethod("get",Context.class).invoke(null,activity);
        if(Build.VERSION.SDK_INT<29)getUiAutomation().grantRuntimePermission("it.vibravid.studio","android.permission.WRITE_EXTERNAL_STORAGE");
        String hls=fixture("application/x-mpegURL","https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8","QA Gallery HLS");
        video=startActivitySync(new Intent().setClassName("it.vibravid.studio","it.vibravid.studio.VideoActivity").putExtra("download_id",hls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ready(video);check(true,"Cinematic player plays completed HLS with advancing offline video");
        runOnMainSync(()->{try{playerType.getMethod("pause").invoke(field(video,"player"));}catch(Exception e){throw new RuntimeException(e);}});
        if(arguments==null||!"export".equals(arguments.getString("mode")))controls();
        Uri hlsCopy=export(hls,null,"QA Gallery HLS");validate(hlsCopy);check(true,"Completed HLS becomes a readable MP4 with video and audio in MediaStore");
        try(android.database.Cursor details=activity.getContentResolver().query(hlsCopy,new String[]{android.provider.MediaStore.Video.Media.DURATION,android.provider.MediaStore.Video.Media.WIDTH,android.provider.MediaStore.Video.Media.HEIGHT},null,null,null)){
            check(details!=null&&details.moveToFirst()&&details.getLong(0)>1000&&details.getInt(1)>0&&details.getInt(2)>0,"Gallery entry contains usable duration and video dimensions");}
        Activity galleryPlayer=startActivitySync(new Intent().setClassName("it.vibravid.studio","it.vibravid.studio.VideoActivity").setData(hlsCopy).putExtra("name","QA MP4 dalla galleria").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{ready(galleryPlayer);check(true,"Exported gallery MP4 opens and actually advances in the native video player");}finally{runOnMainSync(()->galleryPlayer.finish());waitForIdleSync();}
        check(media.getMethod("completed",String.class).invoke(engine,hls)!=null,"Export preserves the completed download and its cache");
        Uri localCopy=export(null,hlsCopy,"QA Gallery local");validate(localCopy);
        check(size(hlsCopy)==size(localCopy)&&size(localCopy)>100000,"Device content video is copied byte-for-byte into the gallery");
        rollback();
        long previousToken=token();runOnMainSync(()->{try{service.getMethod("start",Context.class,String.class,Uri.class,String.class).invoke(null,video,null,hlsCopy,"QA Gallery cancelled");service.getMethod("cancel",Context.class).invoke(null,video);}catch(Exception e){throw new RuntimeException(e);}});
        long cancelledStart=System.currentTimeMillis()+10000;while(token()==previousToken&&System.currentTimeMillis()<cancelledStart)Thread.sleep(20);
        Object cancelled=waitState("position-"+hlsCopy.toString().hashCode(),30);
        check(field(cancelled,"uri")==null&&field(cancelled,"message").toString().contains("annullato"),"Cancelling an export reports cancellation without publishing a partial file");
        Uri missing=Uri.parse("content://media/external/video/media/9223372036854770000");Object failure=startAndWait(null,missing,"QA Gallery missing",30);
        check(field(failure,"uri")==null&&!field(failure,"message").toString().isEmpty(),"Missing device file reports a clear failure instead of crashing");
        runOnMainSync(()->video.finish());waitForIdleSync();video=null;
        String dash=fixture("application/dash+xml","https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd","QA Gallery DASH");
        Uri dashCopy=export(dash,null,"QA Gallery DASH");validate(dashCopy);check(true,"Completed DASH exports a real playable MP4 with video and audio");
        check(!activity.getSharedPreferences("gallery_pending",0).contains("path")&&!activity.getSharedPreferences("gallery_pending",0).contains("uri"),"Completed, failed and cancelled exports leave no pending gallery record");
        result.putString("status","passed");
    }catch(Throwable error){result.putString("status","failed");result.putString("error",android.util.Log.getStackTraceString(error));}
    finally{for(Uri uri:exports)try{activity.getContentResolver().delete(uri,null,null);}catch(RuntimeException ignored){}if(video!=null){runOnMainSync(()->video.finish());waitForIdleSync();}if(activity!=null){runOnMainSync(()->activity.finish());waitForIdleSync();}}
    result.putString("checks",checks.toString());finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);},"VibraVidGalleryQA").start();}
    private String fixture(String mime,String url,String name)throws Exception{
        JSONArray rows=(JSONArray)media.getMethod("rows").invoke(engine);
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.optInt("status")==8&&row.optString("name").equals(name)){
            Object request=media.getMethod("completed",String.class).invoke(engine,row.getString("id"));
            if(mime.equals(request.getClass().getField("mimeType").get(request))&&request.getClass().getField("uri").get(request).toString().startsWith(url))return row.getString("id");
        }}
        Class<?> callback=loader.loadClass("it.vibravid.studio.MediaEngine$Callback");String[] id={null},error={null};CountDownLatch latch=new CountDownLatch(1);
        Object receiver=Proxy.newProxyInstance(loader,new Class<?>[]{callback},(proxy,method,args)->{if(method.getName().equals("success")){id[0]=(String)args[0];latch.countDown();}if(method.getName().equals("failure")){error[0]=(String)args[0];latch.countDown();}return null;});
        JSONObject payload=new JSONObject().put("url",url+"?qa-gallery-260="+System.currentTimeMillis()).put("mime",mime).put("name",name).put("quality",480);
        runOnMainSync(()->{try{media.getMethod("enqueue",JSONObject.class,callback).invoke(engine,payload,receiver);}catch(Exception e){throw new RuntimeException(e);}});
        if(!latch.await(45,TimeUnit.SECONDS)||error[0]!=null)throw new AssertionError("Fixture preparation: "+error[0]);
        long until=System.currentTimeMillis()+150000;while(System.currentTimeMillis()<until){rows=(JSONArray)media.getMethod("rows").invoke(engine);for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(id[0].equals(row.optString("id"))){if(row.optInt("status")==16)throw new AssertionError(row.optString("error"));if(row.optInt("status")==8)return id[0];}}Thread.sleep(250);}throw new AssertionError("Fixture did not complete");
    }
    private void controls()throws Exception{
        runOnMainSync(()->{try{playerType.getMethod("pause").invoke(field(video,"player"));video.getClass().getMethod("seek",long.class).invoke(video,20000L);}catch(Exception e){throw new RuntimeException(e);}});
        click("player-rewind");check(currentPosition()<11500,"Rewind control moves playback back by ten seconds");click("player-forward");check(currentPosition()>18500,"Forward control moves playback ahead by ten seconds");
        click("player-fit");check((Boolean)field(video,"fill"),"Screen fit action switches to fill mode");click("player-fit");
        click("player-speed");runOnMainSync(()->{try{AlertDialog dialog=(AlertDialog)field(video,"optionsDialog");ListView list=dialog.getListView();list.performItemClick(list.getChildAt(4),4,list.getAdapter().getItemId(4));}catch(Exception e){throw new RuntimeException(e);}});
        float[] speed={0};runOnMainSync(()->{try{Object parameters=playerType.getMethod("getPlaybackParameters").invoke(field(video,"player"));speed[0]=parameters.getClass().getField("speed").getFloat(parameters);}catch(Exception e){throw new RuntimeException(e);}});check(Math.abs(speed[0]-1.5f)<.01f,"Speed menu changes actual playback to 1.5x");
        click("player-lock");check(((View)field(field(video,"controls"),"chrome")).getVisibility()==View.GONE||!(Boolean)field(field(video,"controls"),"visible"),"Screen lock hides interactive player chrome");click("player-unlock");
        click("player-tracks");runOnMainSync(()->{try{AlertDialog dialog=(AlertDialog)field(video,"optionsDialog");ListView list=dialog.getListView();list.performItemClick(list.getChildAt(0),0,list.getAdapter().getItemId(0));}catch(Exception e){throw new RuntimeException(e);}});
        check(((AlertDialog)field(video,"optionsDialog")).getListView().getAdapter().getCount()>1,"Audio menu contains the video's actual track selection");runOnMainSync(()->{try{((AlertDialog)field(video,"optionsDialog")).dismiss();}catch(Exception e){throw new RuntimeException(e);}});
        capture("player-portrait-260.png");
        runOnMainSync(()->video.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));waitOrientation(true);Thread.sleep(500);capture("player-landscape-260.png");
        View root=video.getWindow().getDecorView();for(String tag:new String[]{"player-play","player-back","player-speed","player-gallery","player-lock"}){View button=root.findViewWithTag(tag);check(button!=null&&button.getWidth()>0&&button.getHeight()>=48*video.getResources().getDisplayMetrics().density-1,"Landscape control remains accessible: "+tag);}
        runOnMainSync(()->video.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));waitOrientation(false);
        click("player-play");Thread.sleep(4200);check(!(Boolean)field(field(video,"controls"),"visible"),"Playing video automatically hides controls");
        runOnMainSync(()->{try{video.getClass().getMethod("play").invoke(video);}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(250);check((Boolean)field(field(video,"controls"),"visible"),"Paused video restores playback controls");
    }
    private long currentPosition(){long[] result={0};runOnMainSync(()->{try{result[0]=(Long)playerType.getMethod("getCurrentPosition").invoke(field(video,"player"));}catch(Exception e){throw new RuntimeException(e);}});return result[0];}
    private void click(String tag){runOnMainSync(()->{View control=video.getWindow().getDecorView().findViewWithTag(tag);if(control==null)throw new AssertionError("Missing control "+tag);control.performClick();});waitForIdleSync();}
    private void waitOrientation(boolean landscape)throws Exception{long until=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<until){if((video.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)==landscape)return;Thread.sleep(200);}throw new AssertionError("Orientation did not change");}
    private void capture(String name)throws Exception{Bitmap image=getUiAutomation().takeScreenshot();if(image==null)throw new AssertionError("Screenshot unavailable");File file=new File(activity.getExternalFilesDir(null),name);try(OutputStream out=new FileOutputStream(file)){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    private void ready(Activity owner)throws Exception{long until=System.currentTimeMillis()+60000;boolean[] ready={false};String[] failure={null};while(System.currentTimeMillis()<until){runOnMainSync(()->{try{Object player=field(owner,"player");if(player!=null){Object error=playerType.getMethod("getPlayerError").invoke(player);if(error!=null)failure[0]=error.toString();ready[0]=(Integer)playerType.getMethod("getPlaybackState").invoke(player)==3&&(Long)playerType.getMethod("getCurrentPosition").invoke(player)>1000;}}catch(Exception e){throw new RuntimeException(e);}});if(failure[0]!=null)throw new AssertionError(failure[0]);if(ready[0])return;Thread.sleep(200);}throw new AssertionError("Offline player not ready");}
    private Uri export(String id,Uri uri,String title)throws Exception{Object result=startAndWait(id,uri,title,150);Uri saved=(Uri)field(result,"uri");if(saved==null)throw new AssertionError(field(result,"message"));exports.add(saved);return saved;}
    private Object startAndWait(String id,Uri uri,String title,int seconds)throws Exception{
        long old=token();runOnMainSync(()->{try{service.getMethod("start",Context.class,String.class,Uri.class,String.class).invoke(null,activity,id,uri,title);}catch(Exception e){throw new RuntimeException(e);}});
        long until=System.currentTimeMillis()+10000;while(token()==old&&System.currentTimeMillis()<until)Thread.sleep(20);if(token()==old)throw new AssertionError("Export never started");return waitState(id!=null?"offline-"+id:"position-"+uri.toString().hashCode(),seconds);
    }
    private long token()throws Exception{return ((Number)field(service.getMethod("state").invoke(null),"token")).longValue();}
    private Object waitState(String key,int seconds)throws Exception{long until=System.currentTimeMillis()+seconds*1000L,next=0;boolean dumped=false;while(System.currentTimeMillis()<until){Object state=service.getMethod("state").invoke(null);if(key.equals(field(state,"key"))&&!(Boolean)field(state,"active")){Thread.sleep(200);return state;}
        if(System.currentTimeMillis()>=next){Bundle update=new Bundle();update.putString("export",field(state,"message")+" · "+field(state,"progress"));sendStatus(0,update);next=System.currentTimeMillis()+15000;}
        if(!dumped&&System.currentTimeMillis()>until-seconds*1000L+30000){dumped=true;StringBuilder stacks=new StringBuilder();for(Map.Entry<Thread,StackTraceElement[]> item:Thread.getAllStackTraces().entrySet())if(item.getKey().getName().contains("Transformer")||item.getKey().getName().contains("ExoPlayer")||item.getKey().getName().equals("main")){stacks.append(item.getKey().getName()).append(':').append(item.getKey().getState()).append('\n');for(StackTraceElement frame:item.getValue())stacks.append(frame).append('\n');}Bundle debug=new Bundle();debug.putString("pipeline",stacks.toString());sendStatus(0,debug);}
        Thread.sleep(100);}Object state=service.getMethod("state").invoke(null);throw new AssertionError("Export timeout: "+field(state,"message")+" · "+field(state,"progress"));}
    private long size(Uri uri)throws IOException{try(android.os.ParcelFileDescriptor file=activity.getContentResolver().openFileDescriptor(uri,"r")){if(file==null)throw new IOException("No video");return file.getStatSize();}}
    private void validate(Uri uri)throws Exception{MediaExtractor extractor=new MediaExtractor();try{extractor.setDataSource(activity,uri,null);boolean audio=false,video=false;for(int i=0;i<extractor.getTrackCount();i++){MediaFormat format=extractor.getTrackFormat(i);String mime=format.getString(MediaFormat.KEY_MIME);if(mime.startsWith("audio/"))audio=true;if(mime.startsWith("video/")){extractor.selectTrack(i);video=extractor.getSampleTime()>=0;}}if(!audio||!video)throw new AssertionError("Gallery video lacks readable audio/video");}finally{extractor.release();}}
    private void rollback()throws Exception{
        Class<?> writerClass=loader.loadClass("it.vibravid.studio.GalleryWriter");Constructor<?> constructor=writerClass.getDeclaredConstructor(Context.class,String.class,String.class,String.class);constructor.setAccessible(true);Object writer=constructor.newInstance(activity,"QA Gallery invalid","video/mp4",".mp4");
        Method open=writerClass.getDeclaredMethod("open"),publish=writerClass.getDeclaredMethod("publish"),close=writerClass.getDeclaredMethod("close");for(Method method:new Method[]{open,publish,close})method.setAccessible(true);
        Object path=field(writer,"file");Uri uri=(Uri)field(writer,"uri");try(OutputStream out=(OutputStream)open.invoke(writer)){out.write(new byte[512]);}
        boolean rejected=false;try{publish.invoke(writer);}catch(InvocationTargetException error){rejected=error.getCause() instanceof IOException;}finally{close.invoke(writer);}
        check(rejected&&(path==null||!((File)path).exists()),"Invalid output is rejected and rolled back before publication");
        if(uri!=null)try(android.database.Cursor cursor=activity.getContentResolver().query(uri,null,null,null,null)){check(cursor==null||!cursor.moveToFirst(),"Failed scoped-storage output leaves no pending MediaStore entry");}
    }
    private Object field(Object owner,String name)throws Exception{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private void check(boolean condition,String name)throws Exception{if(!condition)throw new AssertionError(name);checks.put(name);Bundle status=new Bundle();status.putString("check",name);sendStatus(0,status);}
}
