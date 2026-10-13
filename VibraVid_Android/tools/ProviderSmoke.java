package it.vibravid.studio.tests;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.webkit.WebView;
import org.json.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Release-APK UI -> native catalog -> resolver -> actual public offline video. */
public final class ProviderSmoke extends Instrumentation {
    private Activity activity;
    private Bundle arguments;
    private WebView web;
    private Object engine,mediaEngine;
    private ClassLoader loader;
    private final JSONArray checks=new JSONArray();
    private final List<String> requested=Collections.synchronizedList(new ArrayList<>());
    private String hlsId,dashId;
    private final java.util.concurrent.atomic.AtomicInteger fixtureExpiry=new java.util.concurrent.atomic.AtomicInteger(0);
    private final String fixtureHls="https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8";
    private final String fixtureDash="https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd";
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
    @Override public void onStart(){new Thread(()->{
        Bundle result=new Bundle();
        try{
            activity=startActivitySync(new Intent().setClassName("it.vibravid.studio","it.vibravid.studio.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            loader=activity.getClassLoader();web=(WebView)field(activity,"web");
            long until=System.currentTimeMillis()+120000;while(System.currentTimeMillis()<until){boolean[] ready={false};runOnMainSync(()->{try{ready[0]=(Boolean)field(activity,"ready");}catch(Exception e){throw new RuntimeException(e);}});if(ready[0])break;Thread.sleep(250);}
            waitFor("window.VibraApp&&window.VibraApp.diagnostics().ready",90);
            check("true".equals(evaluate("!!document.querySelector('#source-form')&&document.querySelector('#source-form').textContent.includes('Cerca e scarica')")),"Release APK opens internal search and direct-download form");
            check("true".equals(evaluate("document.documentElement.scrollWidth<=window.innerWidth")),"Search layout fits native phone viewport");
            check("true".equals(evaluate("!document.querySelector('#source-list')&&!document.querySelector('.source-steps')")),"Website navigation removed from main download flow");
            ActivityMonitor website=addMonitor("it.vibravid.studio.SourceActivity",null,false);
            Object realEngine=field(activity,"providerEngine");Class<?> engineClass=realEngine.getClass(),callClass=loader.loadClass("it.vibravid.studio.ProviderHttp$Call");
            Object call=callClass.getConstructor().newInstance();
            JSONObject live=(JSONObject)engineClass.getMethod("search",String.class,String.class,String.class,callClass).invoke(realEngine,"Angel","streamingcommunity","all",call);
            result.putString("live_search_summary",new JSONObject().put("successful",live.optInt("successful")).put("warnings",live.optJSONArray("warnings")).toString());
            JSONArray liveRows=live.getJSONArray("items");check(liveRows.length()>0,"Real StreamingCommunity catalog responds to native HTTPS search");
            result.putInt("live_catalog_titles",liveRows.length());
            JSONObject movie=null,series=null;for(int i=0;i<liveRows.length();i++){JSONObject row=liveRows.getJSONObject(i);if(movie==null&&row.optString("kind").equals("movie"))movie=row;if(series==null&&row.optString("kind").equals("series"))series=row;}
            if(movie!=null){JSONObject resolved=(JSONObject)engineClass.getMethod("resolve",String.class,String.class,callClass).invoke(realEngine,movie.getString("key"),"",callClass.getConstructor().newInstance());check(resolved.getString("mime").equals("application/x-mpegURL"),"Real provider movie resolves directly to HLS without opening player");result.putString("live_stream_host",android.net.Uri.parse(resolved.getString("url")).getHost());
                if(arguments!=null&&"true".equals(arguments.getString("manifestOnly"))){
                    Object media=loader.loadClass("it.vibravid.studio.MediaEngine").getMethod("get",Context.class).invoke(null,activity);
                    android.net.Uri streamUri=android.net.Uri.parse(resolved.getString("url"));resolved.put("headerOrigin",streamUri.getScheme()+"://"+streamUri.getEncodedAuthority());
                    Method network=media.getClass().getDeclaredMethod("network",JSONObject.class);network.setAccessible(true);Object factory=network.invoke(media,resolved);
                    Class<?> factoryType=loader.loadClass("androidx.media3.datasource.DataSource$Factory"),sourceType=loader.loadClass("androidx.media3.datasource.DataSource"),specType=loader.loadClass("androidx.media3.datasource.DataSpec"),builderType=loader.loadClass("androidx.media3.datasource.DataSpec$Builder");
                    Object source=factoryType.getMethod("createDataSource").invoke(factory),builder=builderType.getConstructor().newInstance();builderType.getMethod("setUri",android.net.Uri.class).invoke(builder,streamUri);builderType.getMethod("setLength",long.class).invoke(builder,2048L);Object spec=builderType.getMethod("build").invoke(builder);
                    byte[] bytes=new byte[2048];int read;try{sourceType.getMethod("open",specType).invoke(source,spec);read=(Integer)sourceType.getMethod("read",byte[].class,int.class,int.class).invoke(source,bytes,0,bytes.length);}finally{sourceType.getMethod("close").invoke(source);}
                    String manifest=read>0?new String(bytes,0,read,java.nio.charset.StandardCharsets.UTF_8):"";
                    check(manifest.startsWith("#EXTM3U"),"Real provider HLS manifest fetched through actual download transport and headers");
                    result.putString("status","passed");result.putString("checks",checks.toString());runOnMainSync(()->activity.finish());waitForIdleSync();activity=null;finish(Activity.RESULT_OK,result);return;
                }
            }
            if(series!=null){JSONObject episodes=(JSONObject)engineClass.getMethod("episodes",String.class,int.class,callClass).invoke(realEngine,series.getString("key"),-1,callClass.getConstructor().newInstance());check(episodes.getJSONArray("episodes").length()>0,"Real provider series returns episodes inside app");}
            engineClass.getMethod("close").invoke(realEngine);
            Class<?> transport=loader.loadClass("it.vibravid.studio.ProviderHttp$Transport"),responseClass=loader.loadClass("it.vibravid.studio.ProviderHttp$Response");
            Object fake=Proxy.newProxyInstance(loader,new Class<?>[]{transport},(proxy,method,args)->{
                if(!method.getName().equals("fetch"))return "Provider QA transport";
                String url=(String)args[1];requested.add(url);String body=fixture(url);
                return responseClass.getConstructor(String.class,String.class).newInstance(url,body);
            });
            engine=engineClass.getConstructor(Context.class,transport).newInstance(activity,fake);setField(activity,"providerEngine",engine);
            mediaEngine=loader.loadClass("it.vibravid.studio.MediaEngine").getMethod("get",Context.class).invoke(null,activity);
            submit("QA ambiguo","__all__","movie");waitFor("!window.VibraApp.diagnostics().providerSearching&&window.VibraApp.diagnostics().providerResults===2",45);
            check("true".equals(evaluate("!window.VibraApp.diagnostics().providerBusy&&location.hash!=='#files'&&document.querySelectorAll('[data-action=provider-download]').length===2")),"Ambiguous movie years ask for choice and never start wrong download");
            submit("QA automatico","__all__","movie");waitFor("location.hash==='#files'",90);hlsId=waitDownload("QA automatico",180);
            check(hlsId!=null,"Exact unique movie starts actual native download directly from search submit");
            JSONObject hls=row(hlsId);check(hls.getLong("bytes")>100000,"Automatic flow saves real HLS video bytes: "+hls.getLong("bytes"));result.putLong("hls_bytes",hls.getLong("bytes"));
            waitFor("document.querySelector('#downloads').textContent.includes('Disponibile offline')",30);check(true,"Actual completed automatic download appears in Download screen");
            check(website.getHits()==0,"No provider browser activity opened during search or download");
            Object request=mediaEngine.getClass().getMethod("completed",String.class).invoke(mediaEngine,hlsId);String uri=request.getClass().getField("uri").get(request).toString();
            check(uri.contains("existing=kept")&&uri.contains("token=QA-public")&&uri.contains("expires="),"Resolver preserves signed URL and existing query parameters");
            playback(hlsId);
            int countBefore=((JSONArray)mediaEngine.getClass().getMethod("rows").invoke(mediaEngine)).length();
            evaluate("location.hash='search'");waitFor("!!document.querySelector('#source-form')",30);submit("QA automatico","streamingcommunity","movie");waitFor("location.hash==='#files'",90);
            check(((JSONArray)mediaEngine.getClass().getMethod("rows").invoke(mediaEngine)).length()==countBefore,"Repeating a movie with a renewed URL reuses the existing download");
            evaluate("location.hash='search'");waitFor("!!document.querySelector('#source-form')",30);
            submit("QA serie","streamingcommunity","series");waitFor("!window.VibraApp.diagnostics().providerSearching&&document.querySelectorAll('[data-action=provider-episodes]').length===1",45);
            evaluate("document.querySelector('[data-action=provider-episodes]').click()");waitFor("document.querySelectorAll('[data-action=provider-episode-download]').length===2",45);
            check("true".equals(evaluate("!!document.querySelector('#provider-season')&&document.querySelector('.episode-list').textContent.includes('Secondo episodio')")),"Seasons and episodes selectable inside application");
            evaluate("document.querySelectorAll('[data-action=provider-episode-download]')[1].click()");waitFor("location.hash==='#files'",90);dashId=waitDownload("QA serie",180);
            check(dashId!=null,"Episode selection starts a real DASH download without website interaction");
            check(requested.toString().contains("episode_id=302"),"Selected episode ID forwarded to original provider iframe endpoint");
            JSONObject dash=row(dashId);check(dash.getLong("bytes")>100000,"Automatic episode flow saves real DASH video bytes: "+dash.getLong("bytes"));result.putLong("dash_bytes",dash.getLong("bytes"));
            playback(dashId);check(website.getHits()==0,"Provider browser never opened through both automatic download flows");removeMonitor(website);
            reusedSegments(fixtureHls,"application/x-mpegURL","QA HLS cached segments");
            reusedSegments(fixtureDash,"application/dash+xml","QA DASH cached segments");
            result.putString("hls_id",hlsId);result.putString("dash_id",dashId);result.putString("status","passed");
        }catch(Throwable error){result.putString("status","failed");result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{if(activity!=null){runOnMainSync(()->activity.finish());waitForIdleSync();}}
        result.putString("checks",checks.toString());finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    },"VibraVidProviderQA").start();}
    private String fixture(String url)throws Exception{
        android.net.Uri uri=android.net.Uri.parse(url);String path=uri.getPath(),query=uri.getQueryParameter("q");
        if(path.equals("/livesearch"))return "{\"records\":[]}";
        if(uri.getHost().contains("animeworld"))return "<html></html>";
        if(uri.getHost().contains("animeunity")&&!path.equals("/livesearch"))return "<html></html>";
        if(url.contains("raiplay-search-service"))return "{\"agg\":{\"titoli\":{\"cards\":[]}}}";
        if(path.endsWith("/search")){
            JSONArray titles=new JSONArray();
            if("QA ambiguo".equals(query)){titles.put(title(101,"QA ambiguo","movie","1980-01-01"));titles.put(title(102,"QA ambiguo","movie","2020-01-01"));}
            else if("QA serie".equals(query))titles.put(title(202,"QA serie","tv","2025-01-01"));
            else titles.put(title(101,"QA automatico","movie","2026-01-01"));
            return html(new JSONObject().put("props",new JSONObject().put("titles",titles).put("cdn_url","https://storage.googleapis.com")));
        }
        if(path.contains("/season-1"))return html(new JSONObject().put("props",new JSONObject().put("loadedSeason",new JSONObject().put("episodes",new JSONArray().put(new JSONObject().put("id",301).put("number",1).put("name","Primo episodio")).put(new JSONObject().put("id",302).put("number",2).put("name","Secondo episodio"))))));
        if(path.contains("/titles/202"))return html(new JSONObject().put("props",new JSONObject().put("title",new JSONObject().put("seasons",new JSONArray().put(new JSONObject().put("id",1).put("number",1))))));
        if(path.contains("/iframe/"))return "<html><iframe src='https://provider-fixture.example/embed/"+(path.endsWith("202")?"dash":"hls")+"'></iframe></html>";
        if(uri.getHost().equals("provider-fixture.example"))return "<html><script>window.masterPlaylist = { url: '"+(path.endsWith("dash")?fixtureDash:fixtureHls)+"?existing=kept',token:'QA-public',expires:'"+(2200000000L+fixtureExpiry.incrementAndGet())+"'};window.canPlayFHD=true;</script></html>";
        throw new AssertionError("Unexpected catalog fixture URL: "+url);
    }
    private JSONObject title(int id,String name,String type,String date)throws Exception{return new JSONObject().put("id",id).put("name",name).put("slug","qa-title").put("type",type).put("last_air_date",date).put("images",new JSONArray()).put("translations",new JSONArray());}
    private String html(JSONObject page){return "<html><div id='app' data-page='"+page.toString().replace("&","&amp;").replace("'","&#39;").replace("\"","&quot;")+"'></div></html>";}
    private void submit(String query,String source,String kind)throws Exception{
        evaluate("(function(){document.querySelector('#source-query').value="+JSONObject.quote(query)+";document.querySelector('#source-choice').value="+JSONObject.quote(source)+";document.querySelector('#provider-kind').value="+JSONObject.quote(kind)+";document.querySelector('#provider-quality').value='480';document.querySelector('#source-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));})()");
    }
    private void reusedSegments(String url,String mime,String name)throws Exception{
        Class<?> callback=loader.loadClass("it.vibravid.studio.MediaEngine$Callback");String[] error={null};CountDownLatch prepared=new CountDownLatch(1);
        Object receiver=Proxy.newProxyInstance(loader,new Class<?>[]{callback},(proxy,method,args)->{if(method.getName().equals("failure")){error[0]=(String)args[0];prepared.countDown();}if(method.getName().equals("success"))prepared.countDown();return null;});
        JSONObject payload=new JSONObject().put("url",url+"?qa-cache-reuse="+System.currentTimeMillis()).put("mime",mime).put("name",name).put("quality",480);
        runOnMainSync(()->{try{mediaEngine.getClass().getMethod("enqueue",JSONObject.class,callback).invoke(mediaEngine,payload,receiver);}catch(Exception e){throw new RuntimeException(e);}});
        if(!prepared.await(45,TimeUnit.SECONDS)||error[0]!=null)throw new AssertionError("Cached segment preparation: "+error[0]);
        String id=waitDownload(name,90);JSONObject cached=row(id);
        check(cached.getLong("bytes")>100000,"A distinct "+mime+" download reusing already-cached segments reports real bytes: "+cached.getLong("bytes"));
    }
    private JSONObject row(String id)throws Exception{JSONArray rows=(JSONArray)mediaEngine.getClass().getMethod("rows").invoke(mediaEngine);for(int i=0;i<rows.length();i++)if(id.equals(rows.getJSONObject(i).optString("id")))return rows.getJSONObject(i);return null;}
    private String waitDownload(String name,int seconds)throws Exception{
        long until=System.currentTimeMillis()+seconds*1000L;while(System.currentTimeMillis()<until){JSONArray rows=(JSONArray)mediaEngine.getClass().getMethod("rows").invoke(mediaEngine);for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.optString("name").startsWith(name)){if(row.optInt("status")==16)throw new AssertionError(row.optString("error"));if(row.optInt("status")==8)return row.getString("id");}}Thread.sleep(700);}throw new AssertionError("Automatic download did not complete: "+name);
    }
    private void playback(String id)throws Exception{
        Activity playerActivity=startActivitySync(new Intent().setClassName("it.vibravid.studio","it.vibravid.studio.VideoActivity").putExtra("download_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Class<?> playerClass=loader.loadClass("androidx.media3.common.Player");boolean[] ready={false},tracks={false,false};String[] error={null};long until=System.currentTimeMillis()+90000;
        while(System.currentTimeMillis()<until){runOnMainSync(()->{try{Object player=field(playerActivity,"player");if(player==null)return;Object failure=playerClass.getMethod("getPlayerError").invoke(player);error[0]=failure==null?null:failure.toString();ready[0]=(Integer)playerClass.getMethod("getPlaybackState").invoke(player)==3&&(Long)playerClass.getMethod("getCurrentPosition").invoke(player)>1000;if(ready[0]){Object current=playerClass.getMethod("getCurrentTracks").invoke(player);for(Object group:(List<?>)current.getClass().getMethod("getGroups").invoke(current)){int type=(Integer)group.getClass().getMethod("getType").invoke(group);boolean selected=(Boolean)group.getClass().getMethod("isSelected").invoke(group);if(selected&&type==2)tracks[0]=true;if(selected&&type==1)tracks[1]=true;}}}catch(Exception e){throw new RuntimeException(e);}});if(error[0]!=null||ready[0])break;Thread.sleep(500);}
        check(ready[0]&&error[0]==null&&tracks[0]&&tracks[1],"Automatic download plays from cache with advancing video and audio: "+id.substring(0,8));runOnMainSync(()->playerActivity.finish());waitForIdleSync();
    }
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void setField(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private String evaluate(String script)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] value={null};runOnMainSync(()->web.evaluateJavascript(script,result->{value[0]=result;latch.countDown();}));if(!latch.await(30,TimeUnit.SECONDS))throw new AssertionError("WebView callback timed out");return value[0];}
    private void waitFor(String expression,int seconds)throws Exception{long until=System.currentTimeMillis()+seconds*1000L;while(System.currentTimeMillis()<until){if("true".equals(evaluate("!!("+expression+")")))return;Thread.sleep(300);}throw new AssertionError("Condition timed out: "+expression+"; UI="+evaluate("document.body.textContent"));}
    private void check(boolean condition,String name)throws Exception{if(!condition)throw new AssertionError(name);checks.put(name);Bundle status=new Bundle();status.putString("check",name);sendStatus(0,status);}
}
