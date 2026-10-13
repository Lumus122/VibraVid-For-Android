package it.vibravid.studio.tests;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import org.json.*;
import java.lang.reflect.*;
import java.util.concurrent.*;

/** New reliability regressions against the installed release, with an isolated catalog transport. */
public final class ReliabilitySmoke extends Instrumentation {
    private Activity activity;private WebView web;private JSONObject original;private Object engine;
    private final JSONArray checks=new JSONArray();
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){new Thread(()->{Bundle result=new Bundle();try{
        activity=startActivitySync(new Intent().setClassName("it.vibravid.studio","it.vibravid.studio.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));web=(WebView)field(activity,"web");waitFor("window.VibraApp&&VibraApp.diagnostics().ready",120);original=(JSONObject)invoke("load",new Class<?>[0]);
        ClassLoader loader=activity.getClassLoader();Class<?> catalog=loader.loadClass("it.vibravid.studio.SourceCatalog");
        check("https".equals(((android.net.Uri)catalog.getMethod("https",String.class).invoke(null,"HTTPS://example.com/video.mp4")).getScheme()),"Uppercase HTTPS scheme normalized before opening video or source");
        java.io.IOException first=new java.io.IOException(),second=new java.io.IOException();first.initCause(second);second.initCause(first);
        Class<?> media=loader.loadClass("it.vibravid.studio.MediaEngine");
        check(((String)media.getMethod("message",Exception.class).invoke(null,first)).length()>0,"A cyclic exception chain returns guidance without an infinite loop");
        evaluate("location.hash='settings'");waitFor("!!document.querySelector('[data-action=appearance]')",30);
        check("true".equals(evaluate("getComputedStyle(document.querySelector('.palette-swatch i')).backgroundColor==='rgb(17, 18, 21)'")),"Palette preview colors render under the actual APK Content Security Policy");
        String[] layouts={"classic","compact","posters","list","panels","focus","editorial","studio","soft","minimal"};
        for(String layout:layouts){evaluate("document.querySelector('[data-key=layout][data-value="+layout+"]').click()");waitFor("document.documentElement.dataset.layout==='"+layout+"'",30);check("true".equals(evaluate("document.documentElement.scrollWidth<=innerWidth")),"Native layout saved and fits viewport: "+layout);}
        String[] palettes={"graphite","ocean","sage","amber","mauve","paper","cream","lagoon","rose","lavender"};
        for(String palette:palettes){
            evaluate("document.querySelector('[data-key=palette][data-value="+palette+"]').click()");waitFor("document.documentElement.dataset.palette==='"+palette+"'",30);
            JSONObject prefs=((JSONObject)invoke("load",new Class<?>[0])).getJSONObject("preferences");
            String background=new JSONArray("["+evaluate("VibraModel.paletteColors({palette:'"+palette+"',theme:document.documentElement.dataset.theme}).paper")+"]").getString(0);
            int expected=Color.parseColor(background);long until=System.currentTimeMillis()+10000;while(activity.getSharedPreferences("appearance",0).getInt("background",0)!=expected&&System.currentTimeMillis()<until)Thread.sleep(100);
            boolean light=palette.equals("paper")||palette.equals("cream")||palette.equals("lagoon")||palette.equals("rose")||palette.equals("lavender");boolean[] nativeLight={false};runOnMainSync(()->nativeLight[0]=(activity.getWindow().getDecorView().getSystemUiVisibility()&View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)!=0);
            check(prefs.getString("palette").equals(palette)&&activity.getSharedPreferences("appearance",0).getInt("background",0)==expected&&nativeLight[0]==light,"Native palette persistence, background and bar icons: "+palette);
        }
        long newest=System.currentTimeMillis()*1000+100000;
        invoke("setRequirements",new Class<?>[]{JSONObject.class},new JSONObject().put("wifiOnly",true).put("requirementsRevision",newest));
        invoke("setRequirements",new Class<?>[]{JSONObject.class},new JSONObject().put("wifiOnly",false).put("requirementsRevision",newest-1));Thread.sleep(700);
        check(activity.getSharedPreferences("offline",0).getBoolean("wifiOnly",false),"Older Wi-Fi callback cannot overwrite the most recent setting");
        set(activity,"requirementsRevision",0L);invoke("setRequirements",new Class<?>[]{JSONObject.class},new JSONObject().put("wifiOnly",false));
        Object live=field(activity,"providerEngine");live.getClass().getMethod("close").invoke(live);
        Class<?> transport=loader.loadClass("it.vibravid.studio.ProviderHttp$Transport"),response=loader.loadClass("it.vibravid.studio.ProviderHttp$Response"),provider=loader.loadClass("it.vibravid.studio.ProviderEngine"),call=loader.loadClass("it.vibravid.studio.ProviderHttp$Call");
        Object fixture=Proxy.newProxyInstance(loader,new Class<?>[]{transport},(proxy,method,args)->response.getConstructor(String.class,String.class).newInstance((String)args[1],catalogFixture((String)args[1])));
        engine=provider.getConstructor(Context.class,transport).newInstance(activity,fixture);set(activity,"providerEngine",engine);
        JSONObject found=(JSONObject)provider.getMethod("search",String.class,String.class,String.class,call).invoke(engine,"QA lungo","streamingcommunity","series",call.getConstructor().newInstance());String key=found.getJSONArray("items").getJSONObject(0).getString("key");
        JSONObject episodes=(JSONObject)provider.getMethod("episodes",String.class,int.class,call).invoke(engine,key,1,call.getConstructor().newInstance());JSONArray rows=episodes.getJSONArray("episodes");check(rows.length()==1000,"A full 1,000-episode season remains selectable");
        for(int index:new int[]{0,999}){JSONObject resolved=(JSONObject)provider.getMethod("resolve",String.class,String.class,call).invoke(engine,key,rows.getJSONObject(index).getString("key"),call.getConstructor().newInstance());check(resolved.getString("providerKey").endsWith(":"+(index+1)),"Episode and title remain resolvable after a large batch: "+(index+1));}
        runOnMainSync(()->web.getWebViewClient().onRenderProcessGone(web,new RenderProcessGoneDetail(){public boolean didCrash(){return true;}public int rendererPriorityAtExit(){return 2;}}));
        runOnMainSync(()->{try{invoke("setTheme",new Class<?>[]{JSONObject.class},new JSONObject().put("background","#ffffff").put("light",true).put("revision",Long.MAX_VALUE));}catch(Exception e){throw new RuntimeException(e);}});
        check(field(activity,"web")==null&&activity.getSharedPreferences("appearance",0).getInt("background",0)==Color.WHITE,"Theme arriving after renderer loss is saved without dereferencing a destroyed WebView");
        result.putString("status","passed");
    }catch(Throwable error){result.putString("status","failed");result.putString("error",android.util.Log.getStackTraceString(error));}
    finally{try{if(original!=null){invoke("save",new Class<?>[]{JSONObject.class},original);JSONObject prefs=original.optJSONObject("preferences");activity.getSharedPreferences("offline",0).edit().putBoolean("wifiOnly",prefs!=null&&prefs.optBoolean("wifiOnly")).commit();}if(activity!=null){runOnMainSync(()->activity.finish());waitForIdleSync();}}catch(Exception error){result.putString("cleanup_error",error.toString());result.putString("status","failed");}}
    result.putString("checks",checks.toString());finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);},"VibraVidReliabilityQA").start();}
    private String catalogFixture(String url)throws Exception{
        String path=android.net.Uri.parse(url).getPath();JSONObject props=new JSONObject();
        if(path.endsWith("/search"))props.put("titles",new JSONArray().put(new JSONObject().put("id",123).put("name","QA lungo").put("type","tv").put("slug","qa").put("images",new JSONArray())));
        else if(path.contains("/season-")){JSONArray episodes=new JSONArray();for(int n=1;n<=1000;n++)episodes.put(new JSONObject().put("id",n).put("number",n).put("name","Episodio "+n));props.put("loadedSeason",new JSONObject().put("episodes",episodes));}
        else if(path.contains("/titles/"))props.put("title",new JSONObject().put("seasons",new JSONArray().put(new JSONObject().put("number",1))));
        else if(path.contains("/iframe/"))return "<iframe src='https://qa-episode.example/player'></iframe>";
        else if(path.equals("/player"))return "https://example.com/qa.m3u8";
        return new JSONObject().put("props",props).toString();
    }
    private Object field(Object owner,String name)throws Exception{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private void set(Object owner,String name,Object value)throws Exception{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);}
    private Object invoke(String name,Class<?>[] types,Object... args)throws Exception{Method method=activity.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,args);}
    private String evaluate(String script)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] result={null};runOnMainSync(()->web.evaluateJavascript(script,value->{result[0]=value;latch.countDown();}));if(!latch.await(30,TimeUnit.SECONDS))throw new AssertionError("WebView timeout");return result[0];}
    private void waitFor(String expression,int seconds)throws Exception{long until=System.currentTimeMillis()+seconds*1000L;while(System.currentTimeMillis()<until){if("true".equals(evaluate("!!("+expression+")")))return;Thread.sleep(200);}throw new AssertionError("Condition timed out: "+expression);}
    private void check(boolean condition,String name)throws Exception{if(!condition)throw new AssertionError(name);checks.put(name);Bundle status=new Bundle();status.putString("check",name);sendStatus(0,status);}
}
