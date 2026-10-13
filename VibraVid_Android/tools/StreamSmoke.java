package it.vibravid.studio.tests;
import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import org.json.*;
import java.lang.reflect.*;
import java.util.concurrent.*;

/** Runs against the signed release; no Media3 code is copied into the test APK. */
public final class StreamSmoke extends Instrumentation {
    private Activity activity;
    private WebView web;
    private Bundle arguments;
    private final JSONArray checks=new JSONArray();
    private Object engine;
    private String downloadId;
    private long downloaded;
    private boolean dash;
    private String testName;
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
    @Override public void onStart(){new Thread(()->{
        Bundle result=new Bundle();
        try{
            dash=arguments!=null&&"dash".equals(arguments.getString("mode"));testName=(dash?"QA DASH offline":"QA HLS offline")+(arguments!=null&&("resume".equals(arguments.getString("mode"))||"repair".equals(arguments.getString("mode")))?"":" "+System.currentTimeMillis());
            launch();waitFor("window.VibraApp&&window.VibraApp.diagnostics().ready",120);
            check("true".equals(evaluate("!!document.querySelector('#source-form')",false)),"Actual APK opens sources rather than metadata search");
            evaluate("document.querySelector('[data-action=manage-sources]').click()",false);waitFor("!!document.querySelector('#source-list')",30);
            check("true".equals(evaluate("document.querySelectorAll('.source-card').length===27",false)),"27 PC-config source addresses rendered in source management");
            evaluate("document.querySelector('[data-action=close-sheet]').click()",false);waitFor("!document.querySelector('#sheet').open",30);
            check("true".equals(evaluate("document.documentElement.scrollWidth<=window.innerWidth",false)),"No horizontal overflow in source view");
            ClassLoader loader=activity.getClassLoader();
            Class<?> detector=loader.loadClass("it.vibravid.studio.MediaDetector");Method type=detector.getMethod("type",String.class,String.class);
            check("application/x-mpegURL".equals(type.invoke(null,"https://example.com/master.m3u8?token=abc",null)),"Signed HLS URL detected");
            check("application/dash+xml".equals(type.invoke(null,"https://example.com/video.mpd",null)),"DASH URL detected");
            check(type.invoke(null,"https://example.com/seg.m4s","video/mp4")==null&&type.invoke(null,"https://example.com/init.mp4","video/mp4")==null,"Segments and init files never offered as whole movies");
            check(type.invoke(null,"https://example.com/page.html",null)==null&&type.invoke(null,"http://example.com/video.mp4",null)==null,"Pages and insecure URLs rejected");
            engine=loader.loadClass("it.vibravid.studio.MediaEngine").getMethod("get",android.content.Context.class).invoke(null,activity);
            if(arguments!=null&&"diagnostic".equals(arguments.getString("mode"))){
                Object cache=engine.getClass().getField("cache").get(engine);JSONArray spans=new JSONArray();
                for(Object key:(java.util.Set<?>)cache.getClass().getMethod("getKeys").invoke(cache)){
                    JSONObject entry=new JSONObject().put("key",key);JSONArray ranges=new JSONArray();
                    for(Object span:(java.util.Set<?>)cache.getClass().getMethod("getCachedSpans",String.class).invoke(cache,key))ranges.put(new JSONArray().put(span.getClass().getField("position").getLong(span)).put(span.getClass().getField("length").getLong(span)));
                    entry.put("ranges",ranges);spans.put(entry);
                }
                Object manager=engine.getClass().getField("manager").get(engine);Object index=loader.loadClass("androidx.media3.exoplayer.offline.DownloadManager").getMethod("getDownloadIndex").invoke(manager);
                JSONArray requests=new JSONArray();JSONArray queue=rows();for(int i=0;i<queue.length();i++){
                    String id=queue.getJSONObject(i).getString("id");Object download=loader.loadClass("androidx.media3.exoplayer.offline.DownloadIndex").getMethod("getDownload",String.class).invoke(index,id);Object request=download.getClass().getField("request").get(download);
                    requests.put(new JSONObject().put("row",queue.getJSONObject(i)).put("uri",request.getClass().getField("uri").get(request).toString()).put("keys",request.getClass().getField("streamKeys").get(request).toString()));
                }
                result.putString("cache",spans.toString());result.putString("requests",requests.toString());result.putString("status","passed");finish(Activity.RESULT_OK,result);return;
            }
            if(arguments!=null&&("resume".equals(arguments.getString("mode"))||"repair".equals(arguments.getString("mode")))){
                JSONArray rows=rows();for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).optString("name").startsWith("QA HLS offline"))downloadId=rows.getJSONObject(i).getString("id");
                check(downloadId!=null,"Download index survives process restart");
            }else{
                // Real WebView request interception without loading a user's account or film.
                Class<?> source=loader.loadClass("it.vibravid.studio.SourceCatalog");
                source.getMethod("update",android.content.Context.class,String.class,String.class).invoke(null,activity,"streamingcommunity","https://storage.googleapis.com/shaka-demo-assets/");
                Activity browserActivity=startActivitySync(new Intent().setClassName("it.vibravid.studio","it.vibravid.studio.SourceActivity").putExtra("source","streamingcommunity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Field browserField=browserActivity.getClass().getDeclaredField("browser");browserField.setAccessible(true);WebView sourceWeb=(WebView)browserField.get(browserActivity);
                String fixture=dash?"https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd":"https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8";
                String html="<html><title>QA Capture</title><script>fetch('"+fixture+"').catch(function(){});</script></html>";
                runOnMainSync(()->sourceWeb.loadDataWithBaseURL("https://storage.googleapis.com/",html,"text/html","UTF-8",null));
                Field candidates=browserActivity.getClass().getDeclaredField("videos");candidates.setAccessible(true);
                long end=System.currentTimeMillis()+60000;boolean[] captured={false};
                while(System.currentTimeMillis()<end){runOnMainSync(()->{try{captured[0]=((java.util.Map<?,?>)candidates.get(browserActivity)).containsKey(fixture);}catch(Exception e){throw new RuntimeException(e);}});if(captured[0])break;Thread.sleep(300);}
                check(captured[0],"Isolated source WebView captures real HLS network request");
                CountDownLatch bridgeLatch=new CountDownLatch(1);String[] bridge={null};runOnMainSync(()->sourceWeb.evaluateJavascript("typeof window.VibraAndroid",v->{bridge[0]=v;bridgeLatch.countDown();}));
                check(bridgeLatch.await(30,TimeUnit.SECONDS)&&"\"undefined\"".equals(bridge[0]),"Remote source browser has no application JavaScript bridge");
                runOnMainSync(()->browserActivity.finish());waitForIdleSync();
                source.getMethod("update",android.content.Context.class,String.class,String.class).invoke(null,activity,"streamingcommunity","https://streamingcommunityz.rip/");
                evaluate("location.hash='files'",false);waitFor("!!document.querySelector('[data-action=new-download]')",30);
                evaluate("document.querySelector('[data-action=new-download]').click()",false);waitFor("!!document.querySelector('#download-form')",30);
                evaluate("(function(){var f=document.querySelector('#download-form');f.elements.url.value='"+fixture+"';f.elements.name.value='"+testName+"';f.elements.quality.value='480';f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));})()",false);
                end=System.currentTimeMillis()+90000;
                while(System.currentTimeMillis()<end){JSONArray rows=rows();for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).optString("name").equals(testName))downloadId=rows.getJSONObject(i).getString("id");if(downloadId!=null)break;Thread.sleep(400);}
                check(downloadId!=null,"UI starts real "+(dash?"DASH":"HLS")+" download through native bridge");
            }
            if(dash){
                engine.getClass().getMethod("pause",String.class,boolean.class).invoke(engine,downloadId,true);
                long until=System.currentTimeMillis()+30000;boolean paused=false;
                while(System.currentTimeMillis()<until){JSONArray queue=rows();for(int i=0;i<queue.length();i++)if(queue.getJSONObject(i).optString("id").equals(downloadId))paused=queue.getJSONObject(i).optBoolean("paused");if(paused)break;Thread.sleep(300);}
                check(paused,"DASH transfer can be paused with state persisted");
                engine.getClass().getMethod("pause",String.class,boolean.class).invoke(engine,downloadId,false);
                check(true,"DASH transfer resumed through download service");
            }
            if(arguments!=null&&"repair".equals(arguments.getString("mode"))){engine.getClass().getMethod("repair",String.class).invoke(engine,downloadId);Thread.sleep(1500);}
            JSONObject row=null;long end=System.currentTimeMillis()+240000;
            while(System.currentTimeMillis()<end){JSONArray rows=rows();for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(downloadId))row=rows.getJSONObject(i);if(row!=null&&(row.optInt("status")==8||row.optInt("status")==16))break;Thread.sleep(1000);}
            check(row!=null&&row.getInt("status")==8,"Selected video and audio tracks downloaded completely: "+(row==null?"missing":row.optString("error")));
            Object request=engine.getClass().getMethod("completed",String.class).invoke(engine,downloadId);
            check(((java.util.List<?>)request.getClass().getField("streamKeys").get(request)).size()>=2,"Offline request retains selected video and audio stream keys");
            downloaded=row.getLong("bytes");check(downloaded>100000,"Real video payload saved: "+downloaded+" bytes");
            Object cache=engine.getClass().getField("cache").get(engine);
            check(((Long)cache.getClass().getMethod("getCacheSpace").invoke(cache))>100000,"Persistent offline cache contains media bytes");
            evaluate("location.hash='files'",false);waitFor("!!document.querySelector('[data-action=media-play]')",30);
            check("true".equals(evaluate("document.querySelector('#downloads').textContent.includes('Disponibile offline')",false)),"Completed download rendered as offline video, separate from library");
            Activity playerActivity=startActivitySync(new Intent().setClassName("it.vibravid.studio","it.vibravid.studio.VideoActivity").putExtra("download_id",downloadId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Field playerField=playerActivity.getClass().getDeclaredField("player");playerField.setAccessible(true);
            int[] playerState={0};String[] playerError={null};long[] duration={0},position={0};end=System.currentTimeMillis()+90000;
            while(System.currentTimeMillis()<end){runOnMainSync(()->{try{Object player=playerField.get(playerActivity);if(player==null)return;Class<?> p=loader.loadClass("androidx.media3.common.Player");playerState[0]=(Integer)p.getMethod("getPlaybackState").invoke(player);Object error=p.getMethod("getPlayerError").invoke(player);playerError[0]=error==null?null:error.toString();duration[0]=(Long)p.getMethod("getDuration").invoke(player);position[0]=(Long)p.getMethod("getCurrentPosition").invoke(player);}catch(Exception e){throw new RuntimeException(e);}});if(playerError[0]!=null||playerState[0]==3&&position[0]>1000)break;Thread.sleep(500);}
            check(playerError[0]==null&&playerState[0]==3&&duration[0]>1000&&position[0]>1000,"Cache-only offline player reaches READY and advances: "+playerError[0]);
            boolean[] selected={false,false};
            runOnMainSync(()->{try{
                Object player=playerField.get(playerActivity);Object tracks=loader.loadClass("androidx.media3.common.Player").getMethod("getCurrentTracks").invoke(player);
                for(Object group:(java.util.List<?>)tracks.getClass().getMethod("getGroups").invoke(tracks)){
                    int kind=(Integer)group.getClass().getMethod("getType").invoke(group);boolean active=(Boolean)group.getClass().getMethod("isSelected").invoke(group);
                    if(active&&kind==2)selected[0]=true;if(active&&kind==1)selected[1]=true;
                }
            }catch(Exception e){throw new RuntimeException(e);}});
            check(selected[0]&&selected[1],"Offline playback has both video and audio selected");

            runOnMainSync(()->playerActivity.finish());waitForIdleSync();
            result.putString("download_id",downloadId);result.putLong("bytes",downloaded);result.putString("checks",checks.toString());result.putString("status","passed");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("status","failed");result.putString("error",android.util.Log.getStackTraceString(error));result.putString("checks",checks.toString());finish(Activity.RESULT_CANCELED,result);}
    },"VibraVidStreamSmoke").start();}
    private JSONArray rows()throws Exception{return (JSONArray)engine.getClass().getMethod("rows").invoke(engine);}
    private void launch() throws Exception {
        activity=startActivitySync(new Intent().setClassName("it.vibravid.studio","it.vibravid.studio.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Field field=activity.getClass().getDeclaredField("web");field.setAccessible(true);web=(WebView)field.get(activity);
        Field loaded=activity.getClass().getDeclaredField("ready");loaded.setAccessible(true);
        long deadline=System.currentTimeMillis()+120000;
        while(System.currentTimeMillis()<deadline){
            boolean[] ready={false};runOnMainSync(()->{try{ready[0]=loaded.getBoolean(activity);}catch(Exception e){throw new RuntimeException(e);}});
            if(ready[0])return;Thread.sleep(250);
        }
        throw new AssertionError("Packaged document did not finish loading");
    }
    private void getInstrumentationFinish(){runOnMainSync(()->activity.finish());waitForIdleSync();}
    private Object invoke(String name,Class<?>[] types,Object... args)throws Exception{Method method=activity.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,args);}
    private String evaluate(String script,boolean string)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);String[] result={null};
        runOnMainSync(()->web.evaluateJavascript(script,value->{result[0]=value;latch.countDown();}));
        if(!latch.await(30,TimeUnit.SECONDS))throw new AssertionError("WebView callback timed out");
        if(string&&result[0]!=null&&!result[0].equals("null"))return new JSONArray("["+result[0]+"]").getString(0);
        return result[0];
    }
    private void waitFor(String expression,int seconds)throws Exception{
        long until=System.currentTimeMillis()+seconds*1000L;
        while(System.currentTimeMillis()<until){if("true".equals(evaluate("!!("+expression+")",false)))return;Thread.sleep(250);}
        throw new AssertionError("Condition timed out: "+expression);
    }
    private void check(boolean condition,String name)throws Exception{if(!condition)throw new AssertionError(name);checks.put(name);Bundle status=new Bundle();status.putString("check",name);sendStatus(0,status);}
}
