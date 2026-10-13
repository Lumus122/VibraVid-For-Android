package it.vibravid.studio.tests;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import org.json.*;
import java.lang.reflect.*;
import java.util.concurrent.*;

/** Separate test APK: exercises the actual release activity, bridge, store and catalog clients. */
public final class AndroidSmoke extends Instrumentation {
    private Bundle arguments;
    private final JSONArray checks = new JSONArray();
    private Activity activity;
    private WebView web;
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments=args; start(); }
    @Override public void onStart() {
        new Thread(() -> {
            Bundle result = new Bundle();
            try {
                launch(); waitFor("window.VibraApp&&window.VibraApp.diagnostics().ready", 120);
                JSONObject diag = new JSONObject(evaluate("JSON.stringify(window.VibraApp.diagnostics())", true));
                check(diag.getBoolean("native"), "Actual native bridge ready");
                check(Boolean.parseBoolean(evaluate("document.documentElement.scrollWidth<=window.innerWidth", false)), "No horizontal overflow");
                check(evaluate("getComputedStyle(document.body).fontFamily", true).contains("Nunito"), "Bundled body font selected");
                check(evaluate("getComputedStyle(document.querySelector('h1')).fontFamily", true).contains("Bricolage"), "Bundled heading font selected");
                if(arguments!=null&&"true".equals(arguments.getString("quick"))){
                    if(diag.optString("theme").equals("cinema")){
                        evaluate("location.hash='settings'",false);waitFor("!!document.querySelector('[data-key=theme]')",30);
                        evaluate("document.querySelector('[data-key=theme]').click()",false);waitFor("document.documentElement.dataset.theme==='light'",30);
                        evaluate("location.hash='catalog'",false);waitFor("!!document.querySelector('#search-form')",30);
                    }
                    capture(result);result.putString("checks",checks.toString());result.putString("status","passed");finish(Activity.RESULT_OK,result);return;
                }
                evaluate("location.hash='library'", false);
                waitFor("!!document.querySelector('[data-action=add-manual]')", 30);
                evaluate("document.querySelector('[data-action=add-manual]').click()", false);
                waitFor("!!document.querySelector('#title-form')", 30);
                evaluate("(function(){var f=document.querySelector('#title-form');f.elements.name.value='Verifica Android nativa';f.elements.duration.value='90';f.elements.notes.value='Nota persistente Android';f.elements.collection_name.value='QA';f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));})()", false);
                waitFor("window.VibraApp.diagnostics().library>=1&&!document.querySelector('#sheet').open", 60);
                JSONObject stored = (JSONObject) invoke("load", new Class<?>[0]);
                boolean found = false;
                for(int i=0;i<stored.getJSONArray("items").length();i++) {
                    JSONObject row=stored.getJSONArray("items").getJSONObject(i);
                    if(row.optString("name").equals("Verifica Android nativa")) found=row.optString("notes").equals("Nota persistente Android")&&row.getInt("duration")==90;
                }
                check(found, "UI saves notes and duration through native AtomicFile");
                getInstrumentationFinish();
                launch(); waitFor("window.VibraApp&&window.VibraApp.diagnostics().ready", 120);
                check(new JSONObject(evaluate("JSON.stringify(window.VibraApp.diagnostics())",true)).getInt("library")>=1, "Library persists after activity restart");
                evaluate("location.hash='tonight'", false);
                waitFor("!!document.querySelector('#tonight-form')", 30);
                evaluate("document.querySelector('#tonight-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))",false);
                waitFor("!!document.querySelector('.plan')",30);
                check(evaluate("document.querySelector('.plan').textContent",true).contains("90 minuti"), "Serata uses stored durations offline");
                evaluate("location.hash='settings'",false);
                waitFor("!!document.querySelector('[data-key=theme]')",30);
                evaluate("if(document.documentElement.dataset.theme!=='cinema')document.querySelector('[data-key=theme]').click()",false);
                waitFor("document.documentElement.dataset.theme==='cinema'",30);
                check(evaluate("getComputedStyle(document.documentElement).backgroundColor",true).contains("8, 9, 11"), "Native neutral cinema theme applied");
                check(invoke("downloads",new Class<?>[0]) instanceof JSONArray,"Persistent download index queried");
                if(arguments!=null&&"true".equals(arguments.getString("live"))){
                    JSONObject films=(JSONObject)invoke("search",new Class<?>[]{String.class,JSONObject.class},"qa-movie",new JSONObject().put("query","Matrix").put("kind","movie"));
                    check(films.getJSONArray("items").length()>0,"Live Wikidata movie metadata returned");
                    boolean matrix=false;
                    for(int i=0;i<films.getJSONArray("items").length();i++){JSONObject movie=films.getJSONArray("items").getJSONObject(i);if(movie.optString("name").equals("Matrix")&&movie.optInt("year")==1999&&movie.optInt("duration")==136)matrix=true;}
                    check(matrix,"Film classification, year and runtime parsed correctly");
                    JSONObject shows=(JSONObject)invoke("search",new Class<?>[]{String.class,JSONObject.class},"qa-series",new JSONObject().put("query","Sherlock").put("kind","series"));
                    check(shows.getJSONArray("items").length()>0,"Live TVmaze catalog metadata returned");
                    evaluate("location.hash='catalog'",false);waitFor("!!document.querySelector('#search-form')",30);
                    evaluate("document.querySelector('#search-query').value='Matrix';document.querySelector('#search-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))",false);
                    waitFor("window.VibraApp.diagnostics().results>0&&!window.VibraApp.diagnostics().searching",60);
                    check(evaluate("document.querySelector('#search-results').textContent",true).contains("Matrix"),"Real catalog results rendered through native bridge");
                }
                evaluate("location.hash='catalog'",false);waitFor("!!document.querySelector('#search-form')",30);
                capture(result);
                result.putString("checks",checks.toString()); result.putString("status","passed");finish(Activity.RESULT_OK,result);
            }catch(Throwable error){result.putString("status","failed");result.putString("error",android.util.Log.getStackTraceString(error));result.putString("checks",checks.toString());finish(Activity.RESULT_CANCELED,result);}
        },"VibraVidSmoke").start();
    }
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
    private void capture(Bundle result)throws Exception{
        if(arguments!=null&&"true".equals(arguments.getString("noCapture")))return;
        java.io.File screenshot=new java.io.File(getTargetContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),"VibraVid_Android_native.png");
        screenshot.getParentFile().mkdirs();android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
        if(bitmap!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(screenshot)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();result.putString("screenshot",screenshot.getAbsolutePath());}
    }
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
