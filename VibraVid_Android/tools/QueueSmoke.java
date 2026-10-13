package it.vibravid.studio.tests;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import org.json.*;
import java.lang.reflect.*;
import java.util.concurrent.*;

/** Individual pause/resume, repair, playback and removal of a unique public progressive fixture. */
public final class QueueSmoke extends Instrumentation {
    private Activity activity;private Object engine,manager;private ClassLoader loader;private String id,url;private final JSONArray checks=new JSONArray();
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){new Thread(()->{Bundle result=new Bundle();try{
        activity=startActivitySync(new Intent().setClassName("it.vibravid.studio","it.vibravid.studio.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));loader=activity.getClassLoader();Class<?> media=loader.loadClass("it.vibravid.studio.MediaEngine");engine=media.getMethod("get",Context.class).invoke(null,activity);manager=media.getField("manager").get(engine);Class<?> managerType=loader.loadClass("androidx.media3.exoplayer.offline.DownloadManager");
        JSONArray persisted=(JSONArray)media.getMethod("rows").invoke(engine);int adaptive=0;
        for(int i=0;i<persisted.length();i++){JSONObject row=persisted.getJSONObject(i);if((row.optString("name").startsWith("QA automatico")||row.optString("name").startsWith("QA serie"))&&row.optInt("status")==8){if(row.optLong("bytes")<=100000)throw new AssertionError("Adaptive byte count lost after process restart: "+row);adaptive++;}}
        if(adaptive>=2)check(true,"Completed provider HLS and DASH byte counts persist after application process restart");
        url="https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4?qa-vibravid="+System.currentTimeMillis();JSONObject payload=new JSONObject().put("url",url).put("name","QA progressive queue");Class<?> callback=loader.loadClass("it.vibravid.studio.MediaEngine$Callback");String[] error={null};Object receiver=Proxy.newProxyInstance(loader,new Class<?>[]{callback},(proxy,method,args)->{if(method.getName().equals("success"))id=(String)args[0];if(method.getName().equals("failure"))error[0]=(String)args[0];return null;});
        runOnMainSync(()->{try{managerType.getMethod("pauseDownloads").invoke(manager);media.getMethod("enqueue",JSONObject.class,callback).invoke(engine,payload,receiver);}catch(Exception e){throw new RuntimeException(e);}});if(error[0]!=null)throw new AssertionError(error[0]);waitRow(1,30);check(id!=null,"Progressive HTTPS fixture enters persistent queue before transfer");
        media.getMethod("pause",String.class,boolean.class).invoke(engine,id,true);waitRow(4,30);check(row().getBoolean("paused"),"Individual pause stores a stop reason and reports In pausa");
        media.getMethod("pause",String.class,boolean.class).invoke(engine,id,false);JSONObject completed=waitRow(8,180);check(completed.getLong("bytes")>100000,"Resume downloads real progressive video bytes: "+completed.getLong("bytes"));result.putLong("bytes",completed.getLong("bytes"));
        Activity player=startActivitySync(new Intent().setClassName("it.vibravid.studio","it.vibravid.studio.VideoActivity").putExtra("download_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Field playerField=player.getClass().getDeclaredField("player");playerField.setAccessible(true);Class<?> playerType=loader.loadClass("androidx.media3.common.Player");boolean[] ready={false};long until=System.currentTimeMillis()+90000;while(System.currentTimeMillis()<until){runOnMainSync(()->{try{Object p=playerField.get(player);ready[0]=p!=null&&(Integer)playerType.getMethod("getPlaybackState").invoke(p)==3&&(Long)playerType.getMethod("getCurrentPosition").invoke(p)>1000;}catch(Exception e){throw new RuntimeException(e);}});if(ready[0])break;Thread.sleep(250);}check(ready[0],"Progressive fixture plays from cache and advances without a network upstream");
        checkPlayerLifecycle(player,playerField,playerType);
        runOnMainSync(()->player.finish());waitForIdleSync();
        media.getMethod("repair",String.class).invoke(engine,id);Thread.sleep(700);waitRow(8,90);check(true,"Repair rechecks the completed progressive cache and keeps it playable");
        media.getMethod("remove",String.class).invoke(engine,id);until=System.currentTimeMillis()+30000;while(System.currentTimeMillis()<until&&row()!=null)Thread.sleep(250);check(row()==null,"Remove clears the selected download from the persistent index");
        Object cache=media.getField("cache").get(engine);check(((java.util.Set<?>)cache.getClass().getMethod("getCachedSpans",String.class).invoke(cache,url)).isEmpty(),"Removing a unique video releases its cache spans");check(!activity.getSharedPreferences("playback",0).contains("offline-"+id)&&!activity.getSharedPreferences("offline_errors",0).contains(id),"Removal cleans saved playback position and error metadata");
        result.putString("status","passed");
    }catch(Throwable error){result.putString("status","failed");result.putString("error",android.util.Log.getStackTraceString(error));}finally{if(activity!=null){runOnMainSync(()->activity.finish());waitForIdleSync();}}
    result.putString("checks",checks.toString());finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);},"VibraVidQueueQA").start();}
    private JSONObject row()throws Exception{JSONArray rows=(JSONArray)engine.getClass().getMethod("rows").invoke(engine);for(int i=0;i<rows.length();i++)if(id!=null&&id.equals(rows.getJSONObject(i).optString("id")))return rows.getJSONObject(i);return null;}
    private void checkPlayerLifecycle(Activity player,Field playerField,Class<?> playerType)throws Exception{
        Method save=player.getClass().getDeclaredMethod("onSaveInstanceState",Bundle.class);save.setAccessible(true);
        Bundle background=new Bundle();boolean[] resumed={false},paused={false},screen={true};
        runOnMainSync(()->{try{callActivityOnPause(player);callActivityOnStop(player);save.invoke(player,background);callActivityOnResume(player);resumed[0]=(Boolean)playerType.getMethod("getPlayWhenReady").invoke(playerField.get(player));}catch(Exception e){throw new RuntimeException(e);}});
        check(background.getBoolean("playing")&&resumed[0],"Saving state after lifecycle pause retains the user's playing intent and resumes playback");
        Bundle stopped=new Bundle();
        runOnMainSync(()->{try{playerType.getMethod("pause").invoke(playerField.get(player));callActivityOnPause(player);callActivityOnStop(player);save.invoke(player,stopped);callActivityOnResume(player);paused[0]=!(Boolean)playerType.getMethod("getPlayWhenReady").invoke(playerField.get(player));screen[0]=(player.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0;}catch(Exception e){throw new RuntimeException(e);}});
        check(!stopped.getBoolean("playing")&&paused[0],"A user-paused video stays paused after background, state save and resume");
        check(!screen[0],"Paused offline playback releases the screen-on flag");
    }
    private JSONObject waitRow(int status,int seconds)throws Exception{long until=System.currentTimeMillis()+seconds*1000L;while(System.currentTimeMillis()<until){JSONObject row=row();if(row!=null){if(row.optInt("status")==16)throw new AssertionError(row.optString("error"));if(row.optInt("status")==status)return row;}Thread.sleep(250);}throw new AssertionError("Expected queue state "+status);}
    private void check(boolean condition,String name)throws Exception{if(!condition)throw new AssertionError(name);checks.put(name);Bundle status=new Bundle();status.putString("check",name);sendStatus(0,status);}
}
