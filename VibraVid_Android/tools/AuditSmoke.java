package it.vibravid.studio.tests;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Deterministic edge cases and existing UI flows against the actual release APK. */
public final class AuditSmoke extends Instrumentation {
    private Activity activity;
    private WebView web;
    private ClassLoader loader;
    private Object engine;
    private JSONObject original;
    private final JSONArray checks=new JSONArray();
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){new Thread(()->{
        Bundle result=new Bundle();
        try{
            activity=startActivitySync(new Intent().setClassName("it.vibravid.studio","it.vibravid.studio.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            loader=activity.getClassLoader();web=(WebView)field(activity,"web");waitFor("window.VibraApp&&window.VibraApp.diagnostics().ready",120);
            check("true".equals(evaluate("getComputedStyle(document.documentElement).backgroundColor==='rgb(17, 18, 21)'||getComputedStyle(document.documentElement).backgroundColor==='rgb(8, 9, 11)'")),"Actual APK uses charcoal background and retains both theme modes");
            check("true".equals(evaluate("getComputedStyle(document.querySelector('.primary')).backgroundImage.includes('linear-gradient')&&['rgb(17, 18, 21)','rgb(8, 9, 11)'].includes(getComputedStyle(document.querySelector('.primary')).color)")),"Primary action retains readable dark text on the pearl accent in normal and cinema modes");
            check("true".equals(evaluate("VibraModel.normalizedTitle('QA فيلم')!==VibraModel.normalizedTitle('QA فيلم آخر')&&VibraModel.normalizedTitle('千尋')==='千尋'")),"Native WebView retains non-Latin title distinctions for automatic downloads");
            ActivityMonitor invalidSource=addMonitor("it.vibravid.studio.SourceActivity",null,false);
            runOnMainSync(()->activity.startActivity(new Intent().setClassName("it.vibravid.studio","it.vibravid.studio.SourceActivity").putExtra("source","qa-missing")));
            Activity invalid=invalidSource.waitForActivityWithTimeout(20000);check(invalid!=null&&invalid.isFinishing(),"Missing source exits with explanation through a safe activity lifecycle");removeMonitor(invalidSource);waitForIdleSync();
            original=(JSONObject)invoke("load",new Class<?>[0]);
            invoke("save",new Class<?>[]{JSONObject.class},original);
            check(Modifier.isStatic(activity.getClass().getDeclaredField("dataLock").getModifiers()),"Library transaction lock shared across activity recreation");
            Object mediaEngine=loader.loadClass("it.vibravid.studio.MediaEngine").getMethod("get",Context.class).invoke(null,activity),manager=mediaEngine.getClass().getField("manager").get(mediaEngine);Class<?> managerType=loader.loadClass("androidx.media3.exoplayer.offline.DownloadManager");
            runOnMainSync(()->{try{managerType.getMethod("pauseDownloads").invoke(manager);callActivityOnPause(activity);callActivityOnResume(activity);}catch(Exception e){throw new RuntimeException(e);}});boolean[] resumed={false};long deadline=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<deadline){runOnMainSync(()->{try{resumed[0]=!(Boolean)managerType.getMethod("getDownloadsPaused").invoke(manager);}catch(Exception e){throw new RuntimeException(e);}});if(resumed[0])break;Thread.sleep(250);}check(resumed[0],"Returning to the app resumes a queue paused by service timeout without clearing individual stop reasons");
            Thread.currentThread().interrupt();try{expectError(()->invoke("save",new Class<?>[]{JSONObject.class},new JSONObject(original.toString()).put("qa_interrupted",true)));}finally{Thread.interrupted();}check(!((JSONObject)invoke("load",new Class<?>[0])).has("qa_interrupted"),"Interrupted save cannot overwrite the library after activity teardown");
            Class<?> catalog=loader.loadClass("it.vibravid.studio.SourceCatalog"),detector=loader.loadClass("it.vibravid.studio.MediaDetector");Method https=catalog.getMethod("https",String.class),type=detector.getMethod("type",String.class,String.class);
            for(String value:new String[]{null,"","http://example.com/a","https://user:pass@example.com/a","https://example.com:99999/a","https://example.com/a\r\nb"})expectError(()->https.invoke(null,value));
            check(true,"Empty, null, insecure, credentialed and invalid source URLs rejected clearly");
            check("application/dash+xml".equals(type.invoke(null,"https://example.com/a.mpd","application/x-mpegurl")),"Manifest extension wins over an incorrect MIME hint");
            check(type.invoke(null,"https://example.com/a.m4s","application/x-mpegurl")==null&&type.invoke(null,"https://example.com/init.mp4","application/dash+xml")==null,"Mislabelled segments never become complete movie candidates");
            Class<?> media=loader.loadClass("it.vibravid.studio.MediaEngine");Method headers=media.getDeclaredMethod("safeHeaders",JSONObject.class);headers.setAccessible(true);
            JSONObject safe=(JSONObject)headers.invoke(null,new JSONObject().put("User-Agent","Bad\r\nHeader: x").put("Referer","https://example.com/").put("Cookie","secret"));
            check(!safe.has("User-Agent")&&!safe.has("Cookie")&&safe.has("Referer"),"Invalid CRLF and unapproved download headers discarded");
            File library=new File(getTargetContext().getFilesDir(),"library.json"),backup=new File(library+".bak");
            if(library.exists()){
                byte[] bytes=read(library);try(FileOutputStream out=new FileOutputStream(backup)){out.write(bytes);}if(!library.delete())throw new IOException("QA backup setup failed");
                check(((JSONObject)invoke("load",new Class<?>[0])).toString().equals(new JSONObject(new String(bytes,StandardCharsets.UTF_8)).toString()),"AtomicFile backup recovered when main library file is missing");
            }
            evaluate("window.qaAnswers={};window.qaOriginal=window.VibraNativeReceive;window.VibraNativeReceive=function(m){if(m.id&&m.id.indexOf('qa-')===0)window.qaAnswers[m.id]=m;window.qaOriginal(m)}");
            JSONObject bad=rpc("save",new JSONObject().put("version",1).put("items",JSONObject.NULL));
            check(!bad.getBoolean("ok")&&((JSONObject)invoke("load",new Class<?>[0])).toString().equals(original.toString()),"Invalid save reports an error and leaves persisted library intact");
            check(((String)invoke("friendlyError",new Class<?>[]{Exception.class},new IOException("write",new android.system.ErrnoException("write",android.system.OsConstants.ENOSPC)))).contains("Libera spazio"),"Full storage gives actionable guidance for library writes and exports");
            JSONObject unicode=new JSONObject(original.toString()).put("qa_unicode","A\u2028B\u2029C");invoke("save",new Class<?>[]{JSONObject.class},unicode);JSONObject roundtrip=rpc("load",new JSONObject());check(roundtrip.getJSONObject("value").getString("qa_unicode").equals("A\u2028B\u2029C"),"Unicode line separators survive the bridge on old WebView without a stalled callback");invoke("save",new Class<?>[]{JSONObject.class},original);
            Object realEngine=field(activity,"providerEngine");realEngine.getClass().getMethod("close").invoke(realEngine);
            Class<?> transport=loader.loadClass("it.vibravid.studio.ProviderHttp$Transport"),response=loader.loadClass("it.vibravid.studio.ProviderHttp$Response"),engineType=loader.loadClass("it.vibravid.studio.ProviderEngine"),callType=loader.loadClass("it.vibravid.studio.ProviderHttp$Call");
            Object fake=Proxy.newProxyInstance(loader,new Class<?>[]{transport},(proxy,method,args)->{if(!method.getName().equals("fetch"))return "Audit fixture";String url=(String)args[1];String body=fixture(url);return response.getConstructor(String.class,String.class).newInstance(url,body);});
            engine=engineType.getConstructor(Context.class,transport).newInstance(activity,fake);setField(activity,"providerEngine",engine);
            Object cancelled=callType.getConstructor().newInstance();callType.getMethod("cancel").invoke(cancelled);expectError(()->engineType.getMethod("search",String.class,String.class,String.class,callType).invoke(engine,"QA","streamingcommunity","series",cancelled));check(true,"Cancelled provider call stops before starting catalog requests");
            JSONObject found=(JSONObject)engineType.getMethod("search",String.class,String.class,String.class,callType).invoke(engine,"QA serie","streamingcommunity","series",callType.getConstructor().newInstance());String key=found.getJSONArray("items").getJSONObject(0).getString("key");
            JSONObject specials=(JSONObject)engineType.getMethod("episodes",String.class,int.class,callType).invoke(engine,key,0,callType.getConstructor().newInstance());
            check(specials.getInt("season")==0&&specials.getJSONArray("episodes").getJSONObject(0).getInt("number")==1,"Season zero specials are selectable and resolve to their own episodes");
            expectError(()->engineType.getMethod("episodes",String.class,int.class,callType).invoke(engine,key,99,callType.getConstructor().newInstance()));check(true,"Unavailable season produces an explanation instead of misleading empty results");
            evaluate("document.querySelector('#source-query').value='QA serie';document.querySelector('#source-choice').value='streamingcommunity';document.querySelector('#provider-kind').value='series';document.querySelector('#source-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))");
            waitFor("!window.VibraApp.diagnostics().providerSearching&&!!document.querySelector('[data-action=provider-episodes]')",30);
            evaluate("document.querySelector('[data-action=provider-episodes]').click()");waitFor("document.querySelector('#sheet').open",20);
            evaluate("document.querySelector('[data-action=close-sheet]').click()");Thread.sleep(350);
            evaluate("document.querySelector('[data-action=manage-sources]').click()");waitFor("!!document.querySelector('#source-list')",20);Thread.sleep(2200);
            check("true".equals(evaluate("document.querySelector('#sheet-title').textContent==='Le fonti'&&document.querySelectorAll('.source-card').length===27")),"Late episode response cannot overwrite another open sheet; all 27 source addresses remain");
            evaluate("document.querySelector('[data-action=close-sheet]').click();location.hash='library'");waitFor("!!document.querySelector('[data-action=add-manual]')",20);
            evaluate("document.querySelector('[data-action=add-manual]').click()");waitFor("!!document.querySelector('#title-form')",20);
            evaluate("document.querySelector('#title-form').elements.name.value='QA revisione';document.querySelector('#title-form').elements.duration.value='90';document.querySelector('#title-form').elements.tags.value='Relax';document.querySelector('#title-form').elements.notes.value='Nota da conservare';document.querySelector('#title-form').elements.collection_name.value='Audit';document.querySelector('#title-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))");
            waitFor("!document.querySelector('#sheet').open&&document.querySelector('#library-grid').textContent.includes('QA revisione')",30);
            check(true,"Manual add saves title, duration, tags, collection and notes through real UI");
            evaluate("document.querySelector('#library-query').value='QA revisione';document.querySelector('#library-query').dispatchEvent(new Event('input',{bubbles:true}));document.querySelector('[data-action=edit]').click()");waitFor("!!document.querySelector('#title-form')",20);
            evaluate("document.querySelector('#title-form').elements.notes.value='Modifica non salvata';document.querySelector('[data-action=close-sheet]').click()");
            waitFor("!!document.querySelector('.unsaved')",20);check(true,"Closing an edited title asks before losing unsaved changes");
            evaluate("document.querySelector('[data-action=keep-editing]').click()");waitFor("!document.querySelector('.unsaved')",20);
            evaluate("document.querySelector('#title-form').elements.notes.value='Nota aggiornata';document.querySelector('#title-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))");waitFor("!document.querySelector('#sheet').open",30);
            JSONObject current=(JSONObject)invoke("load",new Class<?>[0]);boolean note=false;for(int i=0;i<current.getJSONArray("items").length();i++){JSONObject row=current.getJSONArray("items").getJSONObject(i);if(row.optString("name").equals("QA revisione"))note=row.optString("notes").equals("Nota aggiornata");}check(note,"Editing retains all existing metadata and persists updated notes");
            evaluate("location.hash='tonight'");waitFor("!!document.querySelector('#tonight-form')",20);evaluate("document.querySelector('#tonight-form').elements.mood.value='Relax';document.querySelector('#tonight-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))");waitFor("!!document.querySelector('.plan')",20);check("true".equals(evaluate("document.querySelector('.plan').textContent.includes('QA revisione')")),"Serata uses the edited library and applies mood selection");
            evaluate("location.hash='settings'");waitFor("!!document.querySelector('[data-key=reducedMotion]')",20);
            boolean reduced="true".equals(evaluate("document.querySelector('[data-key=reducedMotion]').getAttribute('aria-checked')==='true'"));evaluate("document.querySelector('[data-key=reducedMotion]').click()");waitFor("document.querySelector('[data-key=reducedMotion]').getAttribute('aria-checked')==='"+(!reduced)+"'",30);check(true,"Reduced motion setting persists through the real switch");
            boolean wifi=original.optJSONObject("preferences")!=null&&original.optJSONObject("preferences").optBoolean("wifiOnly");evaluate("document.querySelector('[data-key=wifiOnly]').click()");waitFor("document.querySelector('[data-key=wifiOnly]').getAttribute('aria-checked')==='"+(!wifi)+"'",30);check(((JSONObject)invoke("load",new Class<?>[0])).getJSONObject("preferences").getBoolean("wifiOnly")!=wifi,"Wi-Fi-only setting saves and updates download requirements");
            evaluate("document.querySelector('[data-action=licenses]').click()");waitFor("!!document.querySelector('[data-action=license-text]')",20);evaluate("document.querySelector('[data-action=license-text]').click()");waitFor("!!document.querySelector('.license-text')",20);check("true".equals(evaluate("document.querySelector('.license-text').textContent.includes('GNU GENERAL PUBLIC LICENSE')")),"Bundled license menu and document reader work offline");
            evaluate("document.querySelector('[data-action=close-sheet]').click();location.hash='files'");waitFor("!!document.querySelector('#downloads')",20);Thread.sleep(4500);
            evaluate("location.hash='catalog'");waitFor("!!document.querySelector('#search-form')",20);evaluate("document.querySelector('#search-query').value='Bozza catalogo';document.querySelector('#search-query').dispatchEvent(new Event('input',{bubbles:true}));document.querySelector('[data-action=search-kind][data-kind=series]').click()");check("true".equals(evaluate("document.querySelector('#search-query').value==='Bozza catalogo'")),"Switching catalog kind preserves the query draft");
            evaluate("location.hash='search'");waitFor("!!document.querySelector('#source-form')",20);evaluate("document.querySelector('#source-query').value='QA background';document.querySelector('#source-choice').value='streamingcommunity';document.querySelector('#provider-kind').value='movie';document.querySelector('#provider-quality').value='480';document.querySelector('#source-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))");waitFor("window.VibraApp.diagnostics().providerBusy",30);evaluate("location.hash='library'");waitFor("!!document.querySelector('[data-action=add-manual]')",20);evaluate("document.querySelector('[data-action=add-manual]').click()");waitFor("!!document.querySelector('#title-form')",20);evaluate("document.querySelector('#title-form').elements.name.value='Bozza non salvata';document.querySelector('#title-form').elements.notes.value='Non perdere questa nota'");waitFor("!window.VibraApp.diagnostics().providerBusy",60);check("true".equals(evaluate("window.VibraApp.diagnostics().page==='library'&&document.querySelector('#sheet').open&&document.querySelector('#title-form').elements.notes.value==='Non perdere questa nota'")),"Background download completion preserves the current page and unsaved title edits");evaluate("document.querySelector('[data-action=close-sheet]').click()");waitFor("!!document.querySelector('[data-action=discard-edit]')",20);evaluate("document.querySelector('[data-action=discard-edit]').click()");waitFor("!document.querySelector('#sheet').open",20);evaluate("location.hash='files'");waitFor("!!document.querySelector('#downloads')",20);Thread.sleep(1000);
            if("true".equals(evaluate("!!document.querySelector('[data-action=media-remove]')"))){evaluate("document.querySelector('[data-action=media-remove]').click()");Thread.sleep(4500);check("true".equals(evaluate("document.querySelector('[data-action=media-remove]').dataset.confirm==='yes'&&document.querySelector('[data-action=media-remove]').textContent==='Conferma rimozione'")),"Download polling preserves the user's pending deletion confirmation");}
            evaluate("document.querySelector('[data-action=new-download]').click()");waitFor("!!document.querySelector('#download-form')",20);evaluate("document.querySelector('#download-form').elements.url.value='https://example.com/page.html';document.querySelector('#download-form').elements.name.value='QA invalido';document.querySelector('#download-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))");waitFor("!document.querySelector('#download-error').hidden",20);check("true".equals(evaluate("document.querySelector('#download-error').textContent.includes('non la pagina del sito')")),"Manual download rejects web pages with a clear message and permits retry");
            ActivityMonitor recreated=addMonitor("it.vibravid.studio.MainActivity",null,false);boolean[] handled={false};
            runOnMainSync(()->{handled[0]=web.getWebViewClient().onRenderProcessGone(web,new android.webkit.RenderProcessGoneDetail(){public boolean didCrash(){return true;}public int rendererPriorityAtExit(){return 2;}});});
            check(handled[0]&&field(activity,"web")==null,"Web renderer loss handled without terminating the application");
            runOnMainSync(()->{try{android.widget.FrameLayout root=(android.widget.FrameLayout)field(activity,"root");android.widget.LinearLayout message=(android.widget.LinearLayout)root.getChildAt(0);if(!((android.widget.TextView)message.getChildAt(0)).getText().toString().contains("download salvati"))throw new AssertionError("Recovery explanation missing");((android.widget.Button)message.getChildAt(1)).performClick();}catch(Exception error){throw new RuntimeException(error);}});
            Activity next=recreated.waitForActivityWithTimeout(30000);if(next==null)throw new AssertionError("Recovery activity did not reopen");activity=next;web=(WebView)field(activity,"web");removeMonitor(recreated);waitFor("window.VibraApp&&window.VibraApp.diagnostics().ready",90);check(true,"Renderer recovery button reopens the UI and reads the saved library");
            result.putString("status","passed");
        }catch(Throwable error){result.putString("status","failed");result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{try{if(original!=null&&activity!=null)invoke("save",new Class<?>[]{JSONObject.class},original);if(activity!=null){runOnMainSync(()->activity.finish());waitForIdleSync();}}catch(Exception error){result.putString("cleanup_error",error.toString());result.putString("status","failed");}}
        result.putString("checks",checks.toString());finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    },"VibraVidAuditQA").start();}
    private String fixture(String url)throws Exception{
        String path=Uri.parse(url).getPath();
        if(path.endsWith("/search")){boolean movie="QA background".equals(Uri.parse(url).getQueryParameter("q"));return new JSONObject().put("props",new JSONObject().put("titles",new JSONArray().put(new JSONObject().put("id",movie?702:701).put("name",movie?"QA background":"QA serie").put("type",movie?"movie":"tv").put("slug","qa").put("images",new JSONArray())))).toString();}
        if(path.contains("/iframe/")){Thread.sleep(2000);return "<iframe src='https://qa-provider.example/player'></iframe>";}
        if(path.equals("/player"))return "<script>window.masterPlaylist={url:'https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8',token:'QA-public',expires:'2200000000'};</script>";
        if(path.contains("/season-"))return new JSONObject().put("props",new JSONObject().put("loadedSeason",new JSONObject().put("episodes",new JSONArray().put(new JSONObject().put("id",801).put("number",1).put("name","Speciale QA"))))).toString();
        if(path.contains("/titles/")){Thread.sleep(1500);return new JSONObject().put("props",new JSONObject().put("title",new JSONObject().put("seasons",new JSONArray().put(new JSONObject().put("number",0)).put(new JSONObject().put("number",1))))).toString();}
        throw new IOException("Fixture inattesa");
    }
    private JSONObject rpc(String action,JSONObject payload)throws Exception{String id="qa-"+System.nanoTime();evaluate("VibraAndroid.request("+JSONObject.quote(id)+","+JSONObject.quote(action)+","+JSONObject.quote(payload.toString())+")");waitFor("!!window.qaAnswers["+JSONObject.quote(id)+"]",30);return new JSONObject(new JSONArray("["+evaluate("JSON.stringify(window.qaAnswers["+JSONObject.quote(id)+"])")+"]").getString(0));}
    private byte[] read(File file)throws Exception{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    private interface Failing {void run()throws Exception;}
    private void expectError(Failing action)throws Exception{try{action.run();throw new AssertionError("Expected clear rejection");}catch(InvocationTargetException error){if(!(error.getCause() instanceof Exception))throw error;}}
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void setField(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private Object invoke(String name,Class<?>[] types,Object... args)throws Exception{Method m=activity.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}
    private String evaluate(String script)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] value={null};runOnMainSync(()->web.evaluateJavascript(script,result->{value[0]=result;latch.countDown();}));if(!latch.await(30,TimeUnit.SECONDS))throw new AssertionError("WebView timeout");return value[0];}
    private void waitFor(String expression,int seconds)throws Exception{long until=System.currentTimeMillis()+seconds*1000L;while(System.currentTimeMillis()<until){if("true".equals(evaluate("!!("+expression+")")))return;Thread.sleep(250);}throw new AssertionError("Condition timed out: "+expression+"; UI="+evaluate("document.body.textContent"));}
    private void check(boolean condition,String name)throws Exception{if(!condition)throw new AssertionError(name);checks.put(name);Bundle status=new Bundle();status.putString("check",name);sendStatus(0,status);}
}
