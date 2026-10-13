package it.vibravid.studio;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.media3.exoplayer.offline.DownloadHelper;
import org.json.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** The actual provider page runs in an isolated browser with no application JavaScript bridge. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class SourceActivity extends Activity {
    private WebView browser;
    private LinearLayout layout;
    private FrameLayout content;
    private TextView title,status;
    private Button download,adblock;
    private JSONObject provider;
    private final LinkedHashMap<String,JSONObject> videos=new LinkedHashMap<>();
    private volatile boolean closed,adBlockEnabled;
    private boolean preparing,protectionReady;
    private volatile AdBlocker blocker;
    private String cosmeticScript;
    private final AtomicInteger blockedRequests=new AtomicInteger();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable refreshProtection=()->{if(!closed)updateProtectionButton();};
    private DownloadHelper helper;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private Typeface body;
    private final Set<AlertDialog> dialogs=new HashSet<>();

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        try{provider=SourceCatalog.find(this,getIntent().getStringExtra("source"));SourceCatalog.https(provider.getString("url"));}
        catch(Exception e){Toast.makeText(this,"La fonte non è valida. Controlla il suo indirizzo nelle Impostazioni.",Toast.LENGTH_LONG).show();finish();return;}
        adBlockEnabled=getSharedPreferences("source_browser",MODE_PRIVATE).getBoolean("adblock:"+provider.optString("id"),true);
        body=Typeface.createFromAsset(getAssets(),"fonts/nunito-sans.ttf");
        layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setBackgroundColor(getColor(getSharedPreferences("appearance",0).getBoolean("cinema",false)?R.color.cinema_background:R.color.app_background));
        layout.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),dp(6),dp(10),dp(6));
        Button back=button("‹");back.setTextSize(25);back.setContentDescription("Torna a VibraVid");back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(45),dp(45)));
        title=new TextView(this);title.setText(provider.optString("name"));title.setTypeface(body,Typeface.BOLD);title.setTextColor(getColor(R.color.app_text));title.setTextSize(18);title.setMaxLines(2);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        adblock=button("AD");adblock.setTextSize(12);adblock.setPadding(0,0,0,0);adblock.setMinWidth(0);adblock.setMinimumWidth(0);adblock.setMaxLines(2);adblock.setEnabled(false);adblock.setContentDescription("Caricamento blocco pubblicità");adblock.setOnClickListener(v->showProtection());
        header.addView(adblock,new LinearLayout.LayoutParams(dp(54),dp(45)));
        Button refresh=button("↻");refresh.setContentDescription("Ricarica la fonte");refresh.setOnClickListener(v->{if(browser==null){recreate();return;}clearVideos();browser.reload();});header.addView(refresh,new LinearLayout.LayoutParams(dp(45),dp(45)));
        layout.addView(header);
        content=new FrameLayout(this);browser=new WebView(this);content.addView(browser,new FrameLayout.LayoutParams(-1,-1));layout.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=new LinearLayout(this);footer.setOrientation(LinearLayout.VERTICAL);footer.setPadding(dp(16),dp(10),dp(16),dp(12));
        status=new TextView(this);status.setTypeface(body);status.setTextSize(13);status.setTextColor(getColor(R.color.app_muted));status.setText("Apri il titolo e avvia il suo player. Qui comparirà il video da scaricare.");footer.addView(status);
        download=button("In attesa del video…");download.setBackgroundTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled},new int[]{}},new int[]{getColor(R.color.app_text),getColor(R.color.app_line)}));download.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled},new int[]{}},new int[]{getColor(R.color.app_background),getColor(R.color.app_text)}));download.setEnabled(false);download.setOnClickListener(v->chooseVideo());
        LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(-1,dp(50));action.topMargin=dp(8);footer.addView(download,action);layout.addView(footer);setContentView(layout);layout.requestApplyInsets();getWindow().setStatusBarColor(getColor(R.color.app_background));getWindow().setNavigationBarColor(getColor(R.color.app_background));
        WebSettings settings=browser.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(adBlockEnabled);
        settings.setMediaPlaybackRequiresUserGesture(true);settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser,true);
        browser.setWebChromeClient(new WebChromeClient(){
            @Override public void onReceivedTitle(WebView view,String text){title.setText(text==null?provider.optString("name"):text);}
            @Override public void onPermissionRequest(PermissionRequest request){request.deny();}
            @Override public boolean onCreateWindow(WebView view,boolean dialog,boolean gesture,android.os.Message message){if(adBlockEnabled)recordBlocked();return false;}
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(fullscreen!=null){callback.onCustomViewHidden();return;}fullscreen=view;fullscreenCallback=callback;browser.setVisibility(View.GONE);content.addView(view,new FrameLayout.LayoutParams(-1,-1));
            }
            @Override public void onHideCustomView(){hideFullscreen();}
        });
        browser.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                String url=request.getUrl().toString();AdBlocker rules=blocker;
                if(adBlockEnabled&&rules!=null&&rules.blocks(url)){recordBlocked();return rules.emptyResponse();}
                String type=MediaDetector.type(url,null);
                if(type!=null)capture(url,type,request.getRequestHeaders());return null;
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){blockedRequests.set(0);updateProtectionButton();clearVideos();status.setText("Apri il titolo e avvia il player per rilevare il video.");}
            @Override public void onPageCommitVisible(WebView view,String url){applyCosmeticFilter();}
            @Override public void onPageFinished(WebView view,String url){applyCosmeticFilter();CookieManager.getInstance().flush();}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();if(!"https".equalsIgnoreCase(uri.getScheme()))return true;
                if(adBlockEnabled&&blocker!=null&&blocker.blocks(uri.toString())){recordBlocked();return true;}
                if(request.isForMainFrame()&&!request.hasGesture()&&!request.isRedirect()){
                    String current=browser.getUrl();String oldHost=current==null?null:Uri.parse(current).getHost();
                    if(oldHost!=null&&!oldHost.equals(uri.getHost()))return true;
                }
                return false;
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())status.setText("La fonte non si apre. Verifica la connessione o aggiorna il suo indirizzo nelle Impostazioni.");}
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
                if(view!=browser)return true;hideFullscreen();browser=null;content.removeView(view);view.destroy();videos.clear();download.setEnabled(false);download.setText("In attesa del video…");
                status.setText("Android ha chiuso il browser della fonte. Tocca Ricarica per riaprirlo. I download salvati restano disponibili.");return true;
            }
        });
        browser.setDownloadListener((url,agent,disposition,mime,length)->{
            if(adBlockEnabled&&blocker!=null&&blocker.blocks(url)){recordBlocked();return;}
            String type=MediaDetector.type(url,mime);if(type==null){status.setText("Questo collegamento non è un video supportato.");return;}
            Map<String,String> headers=new HashMap<>();headers.put("User-Agent",agent);String page=browser.getUrl();if(page!=null)headers.put("Referer",page);capture(url,type,headers);chooseVideo();
        });
        String url=provider.optString("url"),query=getIntent().getStringExtra("query"),template=provider.optString("search");
        if(query!=null&&!query.trim().isEmpty()&&!template.isEmpty())try{url=url.replaceAll("/+$","")+template.replace("{query}",URLEncoder.encode(query,"UTF-8"));}catch(Exception ignored){}
        // Read the bundled rules off the UI thread, before any source requests.
        final String firstUrl;
        try{firstUrl=SourceCatalog.https(url).toString();}
        catch(IllegalArgumentException error){status.setText("L’indirizzo di ricerca della fonte non è valido o è troppo lungo. Torna a VibraVid e aggiorna l’indirizzo della fonte.");return;}
        status.setText("Preparo il blocco pubblicità…");
        new Thread(()->{
            AdBlocker loaded=null;String script=null;
            try{
                loaded=AdBlocker.get(this);
                try(InputStream input=getAssets().open("adblock/cosmetic.js");ByteArrayOutputStream output=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);script=new String(output.toByteArray(),StandardCharsets.UTF_8);
                }
            }catch(IOException ignored){}
            final AdBlocker readyRules=loaded;final String readyScript=script;
            runOnUiThread(()->{
                if(closed||browser==null)return;blocker=readyRules;cosmeticScript=readyScript;protectionReady=blocker!=null;
                if(!protectionReady)adBlockEnabled=false;
                browser.getSettings().setSupportMultipleWindows(adBlockEnabled);adblock.setEnabled(true);updateProtectionButton();browser.loadUrl(firstUrl);
            });
        },"VibraVidAdBlock").start();
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!getSharedPreferences("offline",0).getBoolean("notificationAsked",false)){
            getSharedPreferences("offline",0).edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},301);
        }
    }
    private void recordBlocked(){if(closed)return;blockedRequests.incrementAndGet();ui.removeCallbacks(refreshProtection);ui.postDelayed(refreshProtection,120);}
    private void updateProtectionButton(){
        int count=blockedRequests.get();adblock.setText(!protectionReady?"AD\n!":adBlockEnabled?"AD\n"+(count>999?"999+":count):"AD\nOFF");
        adblock.setContentDescription(!protectionReady?"Blocco pubblicità non disponibile":adBlockEnabled?"Blocco pubblicità attivo. "+count+" richieste bloccate. Tocca per le opzioni.":"Blocco pubblicità disattivato per questa fonte. Tocca per attivarlo.");
        adblock.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(adBlockEnabled?R.color.app_text:R.color.app_soft)));adblock.setTextColor(getColor(adBlockEnabled?R.color.app_background:R.color.app_text));
    }
    private void applyCosmeticFilter(){if(adBlockEnabled&&cosmeticScript!=null&&!closed&&browser!=null)browser.evaluateJavascript(cosmeticScript,null);}
    private void showProtection(){
        if(!protectionReady){showDialog(new AlertDialog.Builder(this).setTitle("Blocco pubblicità").setMessage("I filtri non sono disponibili. Riapri la fonte per riprovare.").setPositiveButton("Chiudi",null).create());return;}
        showDialog(new AlertDialog.Builder(this).setTitle("Blocco pubblicità")
            .setMessage((adBlockEnabled?"Attivo · "+blockedRequests.get()+" richieste o nuove finestre bloccate in questa pagina.":"Disattivato per "+provider.optString("name")+".")+"\n\nFiltra domini pubblicitari e di tracciamento, nasconde i banner più comuni e blocca le nuove finestre. Se il player non funziona, puoi disattivarlo solo su questa fonte. La pagina verrà ricaricata.")
            .setPositiveButton(adBlockEnabled?"Disattiva su questa fonte":"Attiva su questa fonte",(dialog,which)->setAdBlockEnabled(!adBlockEnabled))
            .setNegativeButton("Chiudi",null).create());
    }
    private void setAdBlockEnabled(boolean enabled){
        adBlockEnabled=enabled;getSharedPreferences("source_browser",MODE_PRIVATE).edit().putBoolean("adblock:"+provider.optString("id"),enabled).apply();
        blockedRequests.set(0);updateProtectionButton();if(browser==null){recreate();return;}browser.getSettings().setSupportMultipleWindows(enabled);clearVideos();browser.reload();
    }
    private void capture(String url,String mime,Map<String,String> headers){
        runOnUiThread(()->{
            if(closed||preparing||browser==null)return;
            try{
                JSONObject copied=new JSONObject();for(Map.Entry<String,String> entry:headers.entrySet()){
                    String lower=entry.getKey().toLowerCase(Locale.ROOT);if(Arrays.asList("user-agent","referer","origin","authorization").contains(lower))copied.put(entry.getKey(),entry.getValue());
                }
                if(!copied.has("User-Agent"))copied.put("User-Agent",browser.getSettings().getUserAgentString());
                if(!copied.has("Referer")&&browser.getUrl()!=null)copied.put("Referer",browser.getUrl());
                JSONObject candidate=new JSONObject().put("url",url).put("mime",mime).put("headers",copied).put("source",provider.getString("name"))
                    .put("sourcePage",browser.getUrl()==null?provider.getString("url"):browser.getUrl());
                videos.remove(url);videos.put(url,candidate);while(videos.size()>12)videos.remove(videos.keySet().iterator().next());
                status.setText(videos.size()==1?"Video rilevato. Puoi salvarlo sul telefono.":videos.size()+" flussi rilevati. Scegli quello del film.");download.setEnabled(true);download.setText("Scarica il video");
            }catch(Exception ignored){}
        });
    }
    private void clearVideos(){runOnUiThread(()->{if(preparing)return;videos.clear();download.setEnabled(false);download.setText("In attesa del video…");});}
    private void chooseVideo(){
        if(videos.isEmpty()||preparing)return;
        List<JSONObject> choices=new ArrayList<>(videos.values());Collections.reverse(choices);choices.sort((a,b)->Boolean.compare(a.optString("mime").startsWith("video/"),b.optString("mime").startsWith("video/")));
        if(choices.size()==1){confirm(choices.get(0));return;}
        String[] labels=new String[choices.size()];for(int i=0;i<labels.length;i++){JSONObject choice=choices.get(i);Uri uri=Uri.parse(choice.optString("url"));labels[i]=MediaDetector.label(choice.optString("mime"))+" · "+uri.getHost()+"\n"+uri.getLastPathSegment();}
        showDialog(new AlertDialog.Builder(this).setTitle("Quale video scarichiamo?").setItems(labels,(dialog,index)->confirm(choices.get(index))).setNegativeButton("Indietro",null).create());
    }
    private void confirm(JSONObject candidate){
        if(closed||browser==null||isFinishing())return;
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(22),dp(12),dp(22),dp(4));
        EditText name=new EditText(this);name.setTypeface(body);String suggested=browser.getTitle();if(suggested==null||suggested.length()>180)suggested=provider.optString("name")+" · Video";name.setText(suggested);name.setSingleLine(true);name.setTextColor(getColor(R.color.app_text));form.addView(name);
        TextView label=new TextView(this);label.setTypeface(body);label.setText("Qualità massima (se disponibile)");label.setPadding(0,dp(14),0,0);form.addView(label);
        Spinner quality=new Spinner(this);quality.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"720p","1080p","480p"}));form.addView(quality);
        TextView hint=new TextView(this);hint.setTypeface(body);hint.setTextSize(13);hint.setText("Video e audio italiano vengono scelti quando disponibili. HLS/DASH vengono salvati offline nell’app; i contenuti DRM richiedono la licenza del servizio.");hint.setPadding(0,dp(10),0,dp(10));form.addView(hint);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Scarica sul telefono").setView(form).setNegativeButton("Annulla",null).setPositiveButton("Scarica",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{
            try{
                String title=name.getText().toString().trim();if(title.isEmpty()||title.length()>180){name.setError("Inserisci un nome fino a 180 caratteri");return;}
                JSONObject payload=new JSONObject(candidate.toString()).put("name",title).put("quality",new int[]{720,1080,480}[quality.getSelectedItemPosition()]);
                dialog.dismiss();preparing=true;download.setEnabled(false);download.setText("Preparo il download…");status.setText("Leggo le tracce del film. Non è ancora un download completato.");
                MediaEngine.get(this).requirements(getSharedPreferences("offline",0).getBoolean("wifiOnly",false));
                helper=MediaEngine.get(this).enqueue(payload,new MediaEngine.Callback(){
                    @Override public void success(String id){helper=null;preparing=false;if(closed)return;setResult(RESULT_OK,new Intent().putExtra("download",id));finish();}
                    @Override public void failure(String message){helper=null;preparing=false;if(closed)return;download.setEnabled(!videos.isEmpty());download.setText("Riprova il download");status.setText(message);}
                });
            }catch(Exception e){preparing=false;download.setEnabled(true);download.setText("Riprova il download");status.setText(MediaEngine.message(e));}
        }));showDialog(dialog);
    }
    private void showDialog(AlertDialog dialog){if(closed||isFinishing())return;dialogs.add(dialog);dialog.setOnDismissListener(ignored->dialogs.remove(dialog));dialog.show();}
    private Button button(String text){Button button=new Button(this);button.setText(text);button.setTypeface(body,Typeface.BOLD);button.setAllCaps(false);button.setTextColor(getColor(R.color.app_text));button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.app_soft)));return button;}
    private int dp(int value){return Math.round(getResources().getDisplayMetrics().density*value);}
    private void hideFullscreen(){if(fullscreen!=null){content.removeView(fullscreen);fullscreen=null;if(browser!=null)browser.setVisibility(View.VISIBLE);if(fullscreenCallback!=null)fullscreenCallback.onCustomViewHidden();fullscreenCallback=null;}}
    @Override public void onBackPressed(){if(fullscreen!=null){hideFullscreen();return;}if(browser!=null&&browser.canGoBack()){browser.goBack();return;}super.onBackPressed();}
    @Override protected void onPause(){if(browser!=null)browser.onPause();CookieManager.getInstance().flush();super.onPause();}
    @Override protected void onResume(){super.onResume();if(browser!=null)browser.onResume();}
    @Override protected void onDestroy(){closed=true;ui.removeCallbacks(refreshProtection);for(AlertDialog dialog:new ArrayList<>(dialogs))dialog.dismiss();dialogs.clear();if(helper!=null)MediaEngine.get(this).cancelPreparation(helper);hideFullscreen();if(browser!=null){browser.stopLoading();content.removeView(browser);browser.destroy();browser=null;}super.onDestroy();}
}
