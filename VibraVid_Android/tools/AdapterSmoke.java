package it.vibravid.studio.tests;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import org.json.*;
import java.lang.reflect.*;
import java.util.*;

/** All six native adapter families exercised with deterministic public-player protocol fixtures. */
public final class AdapterSmoke extends Instrumentation {
    private final JSONArray checks=new JSONArray();private Activity activity;private Object engine;
    private Class<?> provider,call;private SharedPreferences prefs;private Map<String,?> saved;
    private final String[] ids={"streamingcommunity","streamingcommunity_2","animeunity","animeworld","eurostreaming","raiplay"};
    private boolean drm;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){new Thread(()->{Bundle result=new Bundle();try{
        activity=startActivitySync(new Intent().setClassName("it.vibravid.studio","it.vibravid.studio.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ClassLoader loader=activity.getClassLoader();provider=loader.loadClass("it.vibravid.studio.ProviderEngine");call=loader.loadClass("it.vibravid.studio.ProviderHttp$Call");Class<?> transport=loader.loadClass("it.vibravid.studio.ProviderHttp$Transport"),response=loader.loadClass("it.vibravid.studio.ProviderHttp$Response");
        prefs=activity.getSharedPreferences("sources",0);saved=new HashMap<>(prefs.getAll());SharedPreferences.Editor edit=prefs.edit();for(String id:ids)edit.putString(id,"https://qa-"+id.replace('_','-')+".example");edit.commit();
        Object fake=Proxy.newProxyInstance(loader,new Class<?>[]{transport},(proxy,method,args)->{if(!method.getName().equals("fetch"))return "Adapter fixture";call.getMethod("check").invoke(args[4]);return response.getConstructor(String.class,String.class).newInstance((String)args[1],fixture((String)args[0],(String)args[1],(String)args[3]));});
        engine=provider.getConstructor(Context.class,transport).newInstance(activity,fake);
        for(String id:ids){
            JSONObject found=(JSONObject)provider.getMethod("search",String.class,String.class,String.class,call).invoke(engine,"QA adapter",id,"all",fresh());JSONArray titles=found.getJSONArray("items");check(titles.length()>0,"Catalog parsed: "+id);
            for(int index=0;index<titles.length();index++){
                JSONObject row=titles.getJSONObject(index);String key=row.getString("key"),episode="";
                if(!row.getString("kind").equals("movie")){
                    JSONObject list=(JSONObject)provider.getMethod("episodes",String.class,int.class,call).invoke(engine,key,-1,fresh());JSONArray episodes=list.getJSONArray("episodes");check(episodes.length()>0&&list.getJSONArray("seasons").length()>0,"Season and episode parsed: "+id);episode=episodes.getJSONObject(0).getString("key");
                    if(id.equals("eurostreaming")){JSONObject alternate=resolve(key,episodes.getJSONObject(1).getString("key"));check(alternate.getString("url").endsWith(".m3u8"),"Alternative MaxStream player resolves without executing page JavaScript");}
                }
                JSONObject video=resolve(key,episode);check(video.getString("url").startsWith("https://qa-media.example/")&&video.getString("providerKey").startsWith(id+":"),"Video resolver preserves provider/episode identity: "+id+" / "+row.getString("kind"));
                if(id.equals("raiplay")&&row.getString("kind").equals("movie")){drm=true;try{resolve(key,"");throw new AssertionError("DRM accepted");}catch(InvocationTargetException expected){check(expected.getCause() instanceof IllegalArgumentException&&expected.getCause().getMessage().contains("licenza"),"RaiPlay DRM produces clear offline-license guidance");}finally{drm=false;}}
            }
        }
        result.putString("status","passed");
    }catch(Throwable error){result.putString("status","failed");result.putString("error",android.util.Log.getStackTraceString(error));}
    finally{try{if(engine!=null)provider.getMethod("close").invoke(engine);if(prefs!=null&&saved!=null){SharedPreferences.Editor edit=prefs.edit();for(String id:ids){Object before=saved.get(id);if(before instanceof String)edit.putString(id,(String)before);else edit.remove(id);}edit.commit();}if(activity!=null){runOnMainSync(()->activity.finish());waitForIdleSync();}}catch(Exception e){result.putString("cleanup_error",e.toString());result.putString("status","failed");}}
    result.putString("checks",checks.toString());finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);},"VibraVidAdapterQA").start();}
    private Object fresh()throws Exception{return call.getConstructor().newInstance();}
    private JSONObject resolve(String key,String episode)throws Exception{return (JSONObject)provider.getMethod("resolve",String.class,String.class,call).invoke(engine,key,episode,fresh());}
    private String fixture(String method,String url,String body)throws Exception{
        Uri uri=Uri.parse(url);String path=uri.getPath(),host=uri.getHost();
        if(host.startsWith("qa-streamingcommunity")){
            if(path.endsWith("/search"))return "{\"props\":{\"titles\":[{\"id\":1,\"name\":\"QA film\",\"type\":\"movie\",\"slug\":\"qa\"},{\"id\":2,\"name\":\"QA serie\",\"type\":\"tv\",\"slug\":\"qa\"}]}}";
            if(path.contains("/season-"))return "{\"props\":{\"loadedSeason\":{\"episodes\":[{\"id\":9,\"number\":1,\"name\":\"QA episodio\"}]}}}";
            if(path.contains("/titles/"))return "{\"props\":{\"title\":{\"seasons\":[{\"number\":0}]}}}";
            if(path.contains("/iframe/"))return "<iframe src='https://qa-player.example/sc'></iframe>";
        }
        if(host.equals("qa-player.example"))return path.equals("/sc")?"<script>var masterPlaylist={url:'https://qa-media.example/movie.m3u8?original=1',token:'QA-public',expires:'2200000000'};</script>":"https://qa-media.example/anime.mp4";
        if(host.equals("qa-animeunity.example")){
            if(path.equals("/livesearch")){if(!method.equals("POST")||body==null||!body.startsWith("title="))throw new AssertionError("Invalid AnimeUnity search request");return "{\"records\":[{\"id\":10,\"title\":\"QA anime film\",\"type\":\"Movie\",\"slug\":\"qa\"},{\"id\":11,\"title\":\"QA anime serie\",\"type\":\"TV\",\"slug\":\"qa\"}]}";}
            if(path.startsWith("/info_api/"))return uri.getQueryParameter("start_range")==null?"{\"episodes_count\":1}":"{\"episodes\":[{\"id\":51,\"number\":1}]}";
            if(path.startsWith("/embed-url/"))return JSONObject.quote("https://qa-player.example/unity");
            return "<html>QA</html>";
        }
        if(host.equals("qa-animeworld.example")){
            if(path.equals("/search"))return "<a class='poster' href='/movie'><img alt='QA film' src='https://qa-media.example/poster.jpg'><span class='status'><span class='movie'></span></span></a><a class='poster' href='/series'><img alt='QA serie' src='https://qa-media.example/poster.jpg'></a>";
            if(path.startsWith("/api/episode/"))return "{\"grabber\":\"https://qa-media.example/world.mp4\"}";
            return "<meta name='csrf-token' content='QA'><ul><li class='episode'><a data-id='52' data-episode-id='53' data-episode-num='1' href='/play'>QA</a></li></ul>";
        }
        if(host.equals("qa-eurostreaming.example")){
            if(path.endsWith("/search"))return "[{\"id\":20}]";
            return new JSONObject().put("title",new JSONObject().put("rendered","QA serie")).put("content",new JSONObject().put("rendered","1×01 - Primo <a href='https://loadm-qa.example/#id'>Play</a><br>1×02 - Secondo <a href='https://uprot.net/msf/qa'>Play</a>" )).put("link","https://qa-eurostreaming.example/qa").toString();
        }
        if(host.equals("loadm-qa.example")){
            javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,new javax.crypto.spec.SecretKeySpec("kiemtienmua911ca".getBytes("UTF-8"),"AES"),new javax.crypto.spec.IvParameterSpec("1234567890oiuytr".getBytes("UTF-8")));
            byte[] bytes=cipher.doFinal("{\"cf\":\"https://qa-media.example/loadm.m3u8\"}".getBytes("UTF-8"));StringBuilder hex=new StringBuilder();for(byte value:bytes)hex.append(String.format(Locale.ROOT,"%02x",value&255));return hex.toString();
        }
        if(host.equals("uprot.net"))return "<a href='https://maxstream.video/qa'>Continue</a>";
        if(host.equals("maxstream.video"))return "https://qa-media.example/maxstream.m3u8";
        if(host.equals("www.raiplay.it"))return "{\"agg\":{\"titoli\":{\"cards\":[{\"id\":30,\"titolo\":\"QA film Rai\",\"path_id\":\"/movie.json\"},{\"id\":31,\"titolo\":\"QA serie Rai\",\"path_id\":\"/series.json\"}]}}}";
        if(host.equals("qa-raiplay.example")){
            if(path.equals("/movie.json"))return "{\"program_info\":{\"typology\":\"film\",\"year\":\"2025\"},\"first_item_path\":\"/video/film.html\"}";
            if(path.equals("/series.json"))return "{\"program_info\":{\"typology\":\"serie\"},\"blocks\":[{\"id\":\"b\",\"sets\":[{\"id\":\"s\",\"episode_size\":{\"number\":1}}]}]}";
            if(path.endsWith("/episodes.json"))return "{\"cards\":[{\"id\":61,\"episode\":1,\"name\":\"QA\",\"weblink\":\"/video/ep.html\"}]}";
            if(path.startsWith("/video/"))return "{\"video\":{\"content_url\":\"https://mediapolisvod.rai.it/relinker?cont=QA\"}}";
        }
        if(host.equals("mediapolisvod.rai.it"))return new JSONObject().put("video",new JSONArray().put("https://qa-media.example/rai.m3u8")).put("licence_server_map",new JSONObject().put("drmLicenseUrlValues",drm?new JSONArray().put("https://qa-license.example/"):new JSONArray())).toString();
        throw new AssertionError("Unexpected adapter fixture: "+url);
    }
    private void check(boolean condition,String name)throws Exception{if(!condition)throw new AssertionError(name);checks.put(name);Bundle status=new Bundle();status.putString("check",name);sendStatus(0,status);}
}
