package it.vibravid.studio.tests;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.webkit.*;
import org.json.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the signed APK's real source WebView; never fetches an advert. */
public final class AdBlockSmoke extends Instrumentation {
    private Activity activity;
    private WebView web;
    private final JSONArray checks=new JSONArray();
    private final String sourceId="streamingcommunity",secondId="7movies";
    private final String media="https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8";
    private final String advert="https://doubleclick.net/qa-vibravid-advert.mp4";
    private SharedPreferences prefs,sources;
    private Map<String,?> savedPrefs,savedSources;
    private ClassLoader loader;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){new Thread(()->{
        Bundle result=new Bundle();
        try{
            Context context=getTargetContext();loader=context.getClassLoader();
            prefs=context.getSharedPreferences("source_browser",Context.MODE_PRIVATE);savedPrefs=new HashMap<>(prefs.getAll());
            sources=context.getSharedPreferences("sources",Context.MODE_PRIVATE);savedSources=new HashMap<>(sources.getAll());
            prefs.edit().remove("adblock:"+sourceId).remove("adblock:"+secondId).commit();
            sources.edit().putString(sourceId,"https://storage.googleapis.com/shaka-demo-assets/").putString(secondId,"https://storage.googleapis.com/shaka-demo-assets/").commit();
            Class<?> ruleClass=loader.loadClass("it.vibravid.studio.AdBlocker");
            Object rules=ruleClass.getMethod("get",Context.class).invoke(null,context);Method blocks=ruleClass.getMethod("blocks",String.class);
            check((Boolean)blocks.invoke(rules,advert),"Known ad domain blocked");
            check((Boolean)blocks.invoke(rules,"https://nested.test.doubleclick.net/ad.js"),"Ad subdomains blocked");
            check((Boolean)blocks.invoke(rules,"https://DOUBLECLICK.NET./ad.js"),"Case and DNS trailing dot normalized");
            check(!(Boolean)blocks.invoke(rules,"https://doubleclick.net.example.com/ad.js")&&!(Boolean)blocks.invoke(rules,"https://notdoubleclick.net/ad.js"),"Domain boundaries protect unrelated lookalike hosts");
            check(!(Boolean)blocks.invoke(rules,"https://www3.doubleclick.net/"),"Upstream exact allow exception overrides parent block");
            check(!(Boolean)blocks.invoke(rules,media)&&!(Boolean)blocks.invoke(rules,"https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd")&&!(Boolean)blocks.invoke(rules,"https://challenges.cloudflare.com/"),"Video CDN, HLS, DASH and challenge host remain allowed");
            launch(sourceId);
            check(flag("adBlockEnabled"),"Adblock enabled by default in actual source activity");
            String html="<html><head><title>VibraVid adblock QA</title><meta http-equiv='Content-Security-Policy' content=\"style-src 'none'\"></head><body>"
                +"<div class='ad-banner' id='qa-ad'>Advert</div><div id='qa-content'>Movie title</div><video id='qa-video'></video>"
                +"<script>window.qaLoaded=true;fetch('"+advert+"').then(function(r){window.qaAdStatus=r.status;return r.text()}).then(function(t){window.qaAdBytes=t.length}).catch(function(e){window.qaError=String(e)});"
                +"fetch('"+media+"').catch(function(){});</script></body></html>";
            load(html);
            waitFor("window.qaLoaded&&window.qaAdStatus===204&&window.qaAdBytes===0",60);
            check(true,"Actual WebView ad request receives empty local response before any network fetch");
            waitFor("getComputedStyle(document.getElementById('qa-ad')).display==='none'",30);
            check(true,"Banner hidden even with restrictive stylesheet CSP");
            check("true".equals(evaluate("getComputedStyle(document.getElementById('qa-content')).display!=='none'&&getComputedStyle(document.getElementById('qa-video')).display!=='none'")),"Movie content and video element remain visible");
            evaluate("var a=document.createElement('div');a.className='ad-container';a.id='qa-dynamic';document.body.appendChild(a)");
            waitFor("getComputedStyle(document.getElementById('qa-dynamic')).display==='none'",30);
            check(true,"Dynamically inserted ad containers hidden");
            evaluate("var b=document.createElement('div');b.id='qa-attribute';document.body.appendChild(b);b.className='advertisement'");
            waitFor("getComputedStyle(document.getElementById('qa-attribute')).display==='none'",30);
            check(true,"Ad containers assigned a class after insertion hidden under restrictive CSP");
            long until=System.currentTimeMillis()+60000;boolean[] captured={false,false};
            while(System.currentTimeMillis()<until){runOnMainSync(()->{try{Map<?,?> candidates=(Map<?,?>)field("videos");captured[0]=candidates.containsKey(media);captured[1]=candidates.containsKey(advert);}catch(Exception e){throw new RuntimeException(e);}});if(captured[0])break;Thread.sleep(250);}
            check(captured[0]&&!captured[1],"Real HLS request captured; ad MP4 excluded from movie candidates");
            check("\"undefined\"".equals(evaluate("typeof window.VibraAndroid")),"Remote source still has no application bridge");
            AtomicInteger count=(AtomicInteger)field("blockedRequests");int before=count.get();
            boolean[] popupAllowed={true};runOnMainSync(()->popupAllowed[0]=web.getWebChromeClient().onCreateWindow(web,false,true,new android.os.Message()));
            check(!popupAllowed[0]&&webSettingsMultiple()&&count.get()==before+1,"New-window popup denied and counted by native browser");
            Thread.sleep(300);
            String[] description={null};runOnMainSync(()->{try{description[0]=((android.widget.Button)field("adblock")).getContentDescription().toString();}catch(Exception e){throw new RuntimeException(e);}});
            check(description[0].contains("attivo"),"AD button reports enabled protection and blocked count");
            toggle(false);
            check(!flag("adBlockEnabled")&&!webSettingsMultiple()&&!prefs.getBoolean("adblock:"+sourceId,true),"Per-source disable saves preference and adjusts popup handling");
            // Load a local fixture with no ad URL: the off test performs no ad-network request.
            load("<html><body><div class='ad-banner' id='qa-off'>Off fixture</div></body></html>");
            waitFor("!!document.getElementById('qa-off')",30);Thread.sleep(500);
            check("true".equals(evaluate("getComputedStyle(document.getElementById('qa-off')).display!=='none'&&!document.getElementById('vibravid-adblock-style')")),"Disabling adblock removes cosmetic filtering after reload");
            WebResourceRequest fake=new WebResourceRequest(){
                public android.net.Uri getUrl(){return android.net.Uri.parse(advert);}public boolean isForMainFrame(){return false;}public boolean isRedirect(){return false;}public boolean hasGesture(){return false;}public String getMethod(){return "GET";}public Map<String,String> getRequestHeaders(){return Collections.emptyMap();}
            };
            WebViewClient[] clients={null};runOnMainSync(()->clients[0]=web.getWebViewClient());
            check(clients[0].shouldInterceptRequest(web,fake)==null,"Disabled source bypasses network ad filter");
            close();launch(sourceId);check(!flag("adBlockEnabled"),"Source exception survives activity recreation");
            close();launch(secondId);check(flag("adBlockEnabled"),"Other sources retain adblock enabled");
            close();launch(sourceId);toggle(true);check(flag("adBlockEnabled")&&prefs.getBoolean("adblock:"+sourceId,false),"Source protection can be re-enabled");
            boolean[] recovered={false};runOnMainSync(()->recovered[0]=web.getWebViewClient().onRenderProcessGone(web,new RenderProcessGoneDetail(){public boolean didCrash(){return true;}public int rendererPriorityAtExit(){return 2;}}));check(recovered[0]&&field("browser")==null,"Source renderer loss leaves a recovery explanation and safe lifecycle");
            result.putString("status","passed");
        }catch(Throwable error){result.putString("status","failed");result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{
            try{close();restore(prefs,savedPrefs,"adblock:"+sourceId,"adblock:"+secondId);restore(sources,savedSources,sourceId,secondId);}catch(Exception error){result.putString("status","failed");result.putString("cleanup_error",error.toString());}
        }
        result.putString("checks",checks.toString());finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    },"VibraVidAdBlockQA").start();}
    private void launch(String id)throws Exception{
        activity=startActivitySync(new Intent().setClassName("it.vibravid.studio","it.vibravid.studio.SourceActivity").putExtra("source",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        web=(WebView)field("browser");long until=System.currentTimeMillis()+120000;
        while(System.currentTimeMillis()<until){if(flag("protectionReady"))return;Thread.sleep(250);}throw new AssertionError("Bundled adblock rules did not load");
    }
    private Object field(String name)throws Exception{Field f=activity.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private boolean flag(String name)throws Exception{boolean[] value={false};runOnMainSync(()->{try{value[0]=(Boolean)field(name);}catch(Exception e){throw new RuntimeException(e);}});return value[0];}
    private boolean webSettingsMultiple(){boolean[] value={false};runOnMainSync(()->value[0]=web.getSettings().supportMultipleWindows());return value[0];}
    private void toggle(boolean enabled){runOnMainSync(()->{try{Method method=activity.getClass().getDeclaredMethod("setAdBlockEnabled",boolean.class);method.setAccessible(true);method.invoke(activity,enabled);}catch(Exception e){throw new RuntimeException(e);}});}
    private void load(String html){runOnMainSync(()->web.loadDataWithBaseURL("https://storage.googleapis.com/",html,"text/html","UTF-8",null));}
    private String evaluate(String script)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);String[] value={null};runOnMainSync(()->web.evaluateJavascript(script,result->{value[0]=result;latch.countDown();}));
        if(!latch.await(30,TimeUnit.SECONDS))throw new AssertionError("WebView callback timed out");return value[0];
    }
    private void waitFor(String expression,int seconds)throws Exception{
        long until=System.currentTimeMillis()+seconds*1000L;while(System.currentTimeMillis()<until){if("true".equals(evaluate("!!("+expression+")")))return;Thread.sleep(250);}
        throw new AssertionError("Condition timed out: "+expression+"; fixture="+evaluate("JSON.stringify({body:document.body&&document.body.innerText,adStatus:window.qaAdStatus,error:window.qaError})"));
    }
    private void close(){if(activity!=null){runOnMainSync(()->activity.finish());waitForIdleSync();activity=null;}}
    private void restore(SharedPreferences store,Map<String,?> original,String... keys){if(store==null||original==null)return;SharedPreferences.Editor edit=store.edit();for(String key:keys){Object value=original.get(key);if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else edit.remove(key);}edit.commit();}
    private void check(boolean condition,String name)throws Exception{if(!condition)throw new AssertionError(name);checks.put(name);Bundle status=new Bundle();status.putString("check",name);sendStatus(0,status);}
}
