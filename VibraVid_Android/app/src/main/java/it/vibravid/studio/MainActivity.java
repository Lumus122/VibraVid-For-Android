package it.vibravid.studio;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Offline application shell. Only packaged documents receive the native bridge. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class MainActivity extends Activity {
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final int IMPORT = 101, EXPORT = 102, VIDEO = 103, SOURCE = 104;
    private BroadcastReceiver updateCompleted;
    private volatile boolean updateVisible;
    boolean updateVisible(){return updateVisible;}
    private void resumeUpdate(){if(!alive||!updateVisible||isFinishing())return;try{tasks.execute(()->{try{AppUpdater.complete(this);}catch(Exception error){runOnUiThread(()->{if(alive)Toast.makeText(this,AppLanguage.text(this,MediaEngine.message(error)),Toast.LENGTH_LONG).show();});}});}catch(RejectedExecutionException ignored){}}
    private static final int MAX_DATA = 8 * 1024 * 1024;
    private WebView web;
    private FrameLayout root;
    private View fullscreenVideo;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private final ExecutorService tasks = new ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(32), new ThreadPoolExecutor.AbortPolicy());
    private final Map<String, Future<?>> pending = new ConcurrentHashMap<>();
    private final Map<String, HttpURLConnection> connections = new ConcurrentHashMap<>();
    private ProviderEngine providerEngine;
    private final Map<String,ProviderHttp.Call> providerCalls=new ConcurrentHashMap<>();
    private final Map<String,androidx.media3.exoplayer.offline.DownloadHelper> preparations=new HashMap<>();
    private final LinkedHashMap<String, CacheEntry> searchCache = new LinkedHashMap<String, CacheEntry>(16, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> e) { return size() > 24; }
    };
    private static final Object dataLock = new Object();
    private AtomicFile store;
    private String pickerId, exportBody;
    private int pickerType;
    private volatile boolean ready, alive = true;
    private long themeRevision, requirementsRevision;
    private String pickerPurpose;
    private boolean pickerRestored;
    private JSONObject recoveredPicker;
    private BroadcastReceiver completed;
    private static final class CacheEntry {
        final long time = SystemClock.elapsedRealtime(); final JSONObject value;
        CacheEntry(JSONObject value) { this.value = value; }
    }

    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(AppLanguage.context(base));}

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        providerEngine=new ProviderEngine(this);
        store = new AtomicFile(new File(getFilesDir(), "library.json"));
        root = new FrameLayout(this);
        int savedBackground=getSharedPreferences("appearance",0).getInt("background",getColor(R.color.app_background));
        root.setBackgroundColor(savedBackground);
        getWindow().setStatusBarColor(savedBackground);getWindow().setNavigationBarColor(savedBackground);
        getWindow().getDecorView().setSystemUiVisibility(getSharedPreferences("appearance",0).getBoolean("light",false) ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if(fullscreenVideo!=null){v.setPadding(0,0,0,0);return insets;}
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        try { web = new WebView(this); }
        catch(RuntimeException error){
            setContentView(root);
            showWebFailure("Il componente Android System WebView non è disponibile. Attivalo o aggiornalo sul telefono, poi riapri VibraVid. I dati salvati restano disponibili.");
            return;
        }
        web.setBackgroundColor(savedBackground);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        try{MediaEngine.get(this);androidx.media3.exoplayer.offline.DownloadService.sendResumeDownloads(this, MediaDownloadService.class, false);}
        catch(RuntimeException error){Toast.makeText(this,AppLanguage.text(MainActivity.this,"Lo spazio dei download non è disponibile. Controlla lo spazio libero sul telefono."),Toast.LENGTH_LONG).show();}
        root.requestApplyInsets();
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
        settings.setSafeBrowsingEnabled(true);
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.addJavascriptInterface(new Bridge(), "VibraAndroid");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(fullscreenVideo!=null){callback.onCustomViewHidden();return;}
                fullscreenVideo=view;fullscreenCallback=callback;root.setPadding(0,0,0,0);root.addView(view,new FrameLayout.LayoutParams(-1,-1));web.setVisibility(View.GONE);PlayerWindow.immersive(getWindow());
            }
            @Override public void onHideCustomView(){hideVideoFullscreen();}
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
                    android.util.Log.e("VibraVid", m.message() + " @" + m.lineNumber());
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (ORIGIN.equals(uri.getScheme() + "://" + uri.getAuthority())) {
                    String path = uri.getPath();
                    if (path == null || !path.startsWith("/assets/") || path.contains("..")) return emptyResponse();
                    try {
                        String name = path.substring(8);
                        String mime = name.endsWith(".html") ? "text/html" : name.endsWith(".css") ? "text/css" :
                            name.endsWith(".js") ? "text/javascript" : name.endsWith(".ttf") ? "font/ttf" :
                            name.endsWith(".svg") ? "image/svg+xml" : "text/plain";
                        Map<String, String> headers = new HashMap<>();
                        headers.put("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self'; " +
                            "font-src 'self'; img-src 'self' https: data:; media-src 'self' https: blob:; connect-src 'self'; base-uri 'none'; " +
                            "form-action 'none'; frame-src 'none'; object-src 'none'");
                        headers.put("X-Content-Type-Options", "nosniff");
                        return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, getAssets().open(name));
                    } catch (IOException e) { return emptyResponse(); }
                }
                if (!"https".equals(uri.getScheme())) return emptyResponse();
                return null; // Only poster images can use the network in the document's CSP.
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request.isForMainFrame()) return true;
                return !ORIGIN.equals(request.getUrl().getScheme() + "://" + request.getUrl().getAuthority());
            }
            @Override public void onPageFinished(WebView view, String url) { ready = url.startsWith(ORIGIN + "/assets/");if(ready&&recoveredPicker!=null){emit("",true,recoveredPicker,"pickerRecovered");recoveredPicker=null;} }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) Toast.makeText(MainActivity.this,AppLanguage.text(MainActivity.this,"Avvio non riuscito. Riapri VibraVid."), Toast.LENGTH_LONG).show();
            }
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
                if(view!=web)return true;
                ready=false;web=null;root.removeView(view);view.removeJavascriptInterface("VibraAndroid");view.destroy();
                showWebFailure("Android ha chiuso l’interfaccia web. Riaprila per continuare: la libreria e i download salvati restano disponibili.");return true;
            }
        });
        if (state != null) {
            pickerId = state.getString("pickerId"); pickerType = state.getInt("pickerType");
            pickerPurpose=state.getString("pickerPurpose");pickerRestored=pickerId!=null;
            if (pickerType == EXPORT) try { exportBody = readBounded(new FileInputStream(new File(getCacheDir(), "export.json")), MAX_DATA); } catch (Exception ignored) {}
        }
        completed = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { emit("", true, new JSONObject(), "downloadsChanged"); }
        };
        if(Build.VERSION.SDK_INT>=33)registerReceiver(completed,new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),Context.RECEIVER_EXPORTED);
        else registerLegacyDownloadReceiver();
        web.loadUrl(ORIGIN + "/assets/index.html");
    }

    // Only API 26–32 reach this branch; explicit exported flags apply from API 33.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerLegacyDownloadReceiver(){registerReceiver(completed,new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));}

    private WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    private void showWebFailure(String explanation){
        LinearLayout message=new LinearLayout(this);message.setOrientation(LinearLayout.VERTICAL);message.setGravity(Gravity.CENTER);message.setPadding(32,24,32,24);message.setBackgroundColor(getColor(R.color.app_background));
        TextView text=new TextView(this);text.setText(explanation);text.setTextColor(getColor(R.color.app_text));text.setTextSize(17);message.addView(text);
        Button retry=new Button(this);retry.setText(AppLanguage.text(MainActivity.this,"Riapri interfaccia"));retry.setAllCaps(false);retry.setTextColor(getColor(R.color.app_background));retry.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.app_text)));retry.setOnClickListener(v->recreate());message.addView(retry);
        root.addView(message,new FrameLayout.LayoutParams(-1,-1));
    }

    private void dispatchUi(Runnable action){
        runOnUiThread(()->{if(!alive||isFinishing())return;try{action.run();}catch(RuntimeException error){Toast.makeText(this,AppLanguage.text(MainActivity.this,friendlyError(error)),Toast.LENGTH_LONG).show();}});
    }

    public final class Bridge {
        @JavascriptInterface public void request(String id, String action, String json) {
            if (!alive || id == null || id.isEmpty() || id.length() > 80) return;
            if(action==null||json==null||json.length()>MAX_DATA){emit(id,false,"Richiesta non valida o troppo grande.",null);return;}
            if ("cancel".equals(action)) {
                try {
                    String target = parseObject(json).optString("id");
                    Future<?> previous = pending.remove(target); if (previous != null) previous.cancel(true);
                    HttpURLConnection connection = connections.remove(target); if (connection != null) connection.disconnect();
                    ProviderHttp.Call providerCall=providerCalls.remove(target);if(providerCall!=null)providerCall.cancel();
                    emit(id, true, new JSONObject(), null);
                } catch (Exception e) { emit(id, false, "Ricerca già conclusa.", null); }
                return;
            }
            try {
                FutureTask<Void> task = new FutureTask<Void>(() -> {
                    try {
                        JSONObject payload = parseObject(json);
                        if (action.equals("import") || action.equals("export") || action.equals("pickVideo")) {
                            if(action.equals("export")){
                                String body=payload.getJSONObject("data").toString(2);
                                if(body.getBytes(StandardCharsets.UTF_8).length>MAX_DATA)throw new IllegalArgumentException("Esportazione troppo grande.");
                                payload.put("serializedExport",body);
                            }
                            runOnUiThread(() -> {if(alive)beginPicker(id, action, payload);}); return;
                        }
                        if (action.equals("download")) {
                            runOnUiThread(() -> {
                                if(!alive)return;
                                try {
                                    if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&!getSharedPreferences("offline",0).getBoolean("notificationAsked",false)){
                                        getSharedPreferences("offline",0).edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},301);
                                    }
                                    setRequirements(payload);
                                    androidx.media3.exoplayer.offline.DownloadHelper helper=MediaEngine.get(MainActivity.this).enqueue(payload, new MediaEngine.Callback() {
                                        public void success(String downloadId) {
                                            preparations.remove(id);
                                            try { emit(id, true, new JSONObject().put("id", downloadId), null); } catch (JSONException ignored) {}
                                        }
                                        public void failure(String error) {preparations.remove(id); emit(id, false, error, null); }
                                    });
                                    if(helper!=null)preparations.put(id,helper);
                                } catch (Exception e) { emit(id, false, MediaEngine.message(e), null); }
                            }); return;
                        }
                        Object result;
                        switch (action) {
                            case "load": result = load(); break;
                            case "save": save(payload); result = new JSONObject().put("saved", true); break;
                            case "search": result = search(id, payload); break;
                            case "titleDetails": result=TitleDetails.load(MainActivity.this,payload,url->fetch(id,url,4500)); break;
                            case "playbackProgress": result=PlaybackStore.titles(MainActivity.this); break;
                            case "discover": result=DiscoveryCatalog.load(MainActivity.this,url->fetch(id,url,8000)); break;
                            case "providerSearch":
                                ProviderHttp.Call searchCall=new ProviderHttp.Call();providerCalls.put(id,searchCall);
                                result=providerEngine.search(payload.optString("query").trim(),payload.optString("source","__all__"),payload.optString("kind","all"),searchCall);break;
                            case "providerEpisodes":
                                ProviderHttp.Call episodeCall=new ProviderHttp.Call();providerCalls.put(id,episodeCall);
                                result=providerEngine.episodes(payload.getString("key"),payload.optInt("season",-1),episodeCall);break;
                            case "providerResolve":
                                ProviderHttp.Call resolveCall=new ProviderHttp.Call();providerCalls.put(id,resolveCall);
                                result=providerEngine.resolve(payload.getString("key"),payload.optString("episode"),resolveCall);break;
                            case "downloads": result = downloads(); break;
                            case "sources": result = SourceCatalog.list(MainActivity.this); break;
                            case "appUpdate": result=AppUpdater.check(MainActivity.this);resumeUpdate();break;
                            case "sourceUpdate": SourceCatalog.update(MainActivity.this, payload.getString("id"), payload.getString("url")); result = new JSONObject(); break;
                            case "sourceImport": result = new JSONObject().put("updated", SourceCatalog.importDomains(MainActivity.this, payload.getJSONObject("data"))); break;
                            case "openSource": openSource(payload); result = new JSONObject(); break;
                            case "mediaRequirements": setRequirements(payload); result = new JSONObject(); break;
                            case "mediaPause": MediaEngine.get(MainActivity.this).pause(payload.getString("id"), payload.optBoolean("paused")); result = new JSONObject(); break;
                            case "mediaRemove": MediaEngine.get(MainActivity.this).remove(payload.getString("id")); result = new JSONObject(); break;
                            case "mediaRepair": MediaEngine.get(MainActivity.this).repair(payload.getString("id")); result = new JSONObject(); break;
                            case "mediaPlay":
                                String mediaId = payload.getString("id");
                                MediaEngine.get(MainActivity.this).completed(mediaId);
                                dispatchUi(() -> startActivity(new Intent(MainActivity.this, VideoActivity.class).putExtra("download_id", mediaId)));
                                result = new JSONObject(); break;
                            case "cancelDownload": result = cancelDownload(payload); break;
                            case "openDownload": openDownload(payload); result = new JSONObject(); break;
                            case "openLink": openLink(payload.optString("url")); result = new JSONObject(); break;
                            case "play": play(payload); result = new JSONObject(); break;
                            case "share": share(payload); result = new JSONObject(); break;
                            case "theme": setTheme(payload); result = new JSONObject(); break;
                            default: throw new IllegalArgumentException("Comando non disponibile.");
                        }
                        if (!Thread.currentThread().isInterrupted()) emit(id, true, result, null);
                    } catch (Exception e) {
                        if (!Thread.currentThread().isInterrupted()) emit(id, false, action.startsWith("provider")?ProviderEngine.problem(e):friendlyError(e), null);
                    } finally { pending.remove(id); connections.remove(id);providerCalls.remove(id); }
                }, null);
                pending.put(id, task);
                tasks.execute(task);
            } catch (RejectedExecutionException e) { pending.remove(id); emit(id, false, "Attendi che l'operazione in corso finisca.", null); }
        }
    }

    private String friendlyError(Exception e) {
        if(MediaEngine.isStorageFull(e))return "Spazio sul telefono esaurito. Libera spazio e riprova: i dati già salvati restano disponibili.";
        if (e instanceof UnknownHostException) return "Connessione assente. La tua libreria resta disponibile offline.";
        if (e instanceof SocketTimeoutException) return "La fonte sta impiegando troppo tempo. Riprova tra poco.";
        if ((e instanceof IllegalArgumentException || e instanceof SecurityException)&&e.getMessage()!=null) return e.getMessage();
        if (e instanceof FileNotFoundException) return "Il file non è più disponibile. Selezionalo di nuovo.";
        return "Operazione non riuscita. Riprova; i titoli salvati restano nella libreria.";
    }

    private void emit(String id, boolean ok, Object value, String event) {
        if (!alive || web == null) return;
        if(!ok&&value instanceof String)value=AppLanguage.text(this,(String)value);
        try {
            JSONObject response = new JSONObject().put("id", id).put("ok", ok).put("value", value).put("event", event == null ? JSONObject.NULL : event);
            // Older supported WebViews cannot parse literal Unicode line separators in JS strings.
            String encoded=response.toString().replace("\u2028","\\u2028").replace("\u2029","\\u2029");
            String js = "window.VibraNativeReceive&&window.VibraNativeReceive(" + encoded + ")";
            runOnUiThread(() -> { if (alive && web != null) web.evaluateJavascript(js, null); });
        } catch (JSONException ignored) {}
    }

    private JSONObject load() throws Exception {
        synchronized (dataLock) {
            if (!store.getBaseFile().exists()&&!new File(store.getBaseFile()+".bak").exists()) return new JSONObject().put("version", 1).put("items", new JSONArray()).put("collections", new JSONArray());
            return parseObject(readBounded(store.openRead(), MAX_DATA));
        }
    }

    private void save(JSONObject value) throws Exception {
        if (value.optInt("version") != 1 || value.optJSONArray("items") == null || value.getJSONArray("items").length() > 2000)
            throw new IllegalArgumentException("Libreria non valida o troppo grande.");
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_DATA) throw new IllegalArgumentException("La libreria supera lo spazio previsto.");
        synchronized (dataLock) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Salvataggio interrotto");
            JSONArray previousFiles=load().optJSONArray("files");
            FileOutputStream out = null;
            try { out = store.startWrite(); out.write(bytes); store.finishWrite(out); }
            catch (Exception e) { if (out != null) store.failWrite(out); throw e; }
            pruneVideoPermissions(previousFiles,value.optJSONArray("files"));
        }
    }

    private void pruneVideoPermissions(JSONArray previous,JSONArray files){
        Set<String> tokens=new HashSet<>(),uris=new HashSet<>();android.content.SharedPreferences prefs=getSharedPreferences("media",MODE_PRIVATE);
        if(files!=null)for(int i=0;i<files.length();i++){JSONObject file=files.optJSONObject(i);if(file!=null){String token=file.optString("token");tokens.add(token);String uri=prefs.getString(token,null);if(uri!=null)uris.add(uri);}}
        Set<String> removed=new HashSet<>();if(previous!=null)for(int i=0;i<previous.length();i++){JSONObject file=previous.optJSONObject(i);if(file!=null&&!tokens.contains(file.optString("token")))removed.add(file.optString("token"));}
        for(Map.Entry<String,?> entry:prefs.getAll().entrySet())if(!removed.contains(entry.getKey())&&entry.getValue() instanceof String)uris.add((String)entry.getValue());
        android.content.SharedPreferences.Editor edit=prefs.edit();
        for(Map.Entry<String,?> entry:prefs.getAll().entrySet())if(removed.contains(entry.getKey())&&entry.getValue() instanceof String){
            String uri=(String)entry.getValue();if(!uris.contains(uri)){
                try{getContentResolver().releasePersistableUriPermission(Uri.parse(uri),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
                getSharedPreferences("playback",0).edit().remove("position-"+uri.hashCode()).apply();
            }
            edit.remove(entry.getKey());
        }
        edit.apply();
    }

    private JSONObject search(String requestId, JSONObject data) throws Exception {
        String query = data.optString("query").trim(), kind = data.optString("kind", "movie");
        if (query.length() < 2 || query.length() > 200) throw new IllegalArgumentException("Scrivi almeno due caratteri.");
        if (!kind.equals("movie") && !kind.equals("series")) throw new IllegalArgumentException("Scegli Film oppure Serie.");
        String key = kind + "|" + query.toLowerCase(Locale.ROOT);
        synchronized (searchCache) {
            CacheEntry cached = searchCache.get(key);
            if (cached != null && SystemClock.elapsedRealtime() - cached.time < 90000) return new JSONObject(cached.value.toString()).put("cached", true);
        }
        JSONArray items = new JSONArray();
        if (kind.equals("movie")) {
            items = movieSearch(requestId, query);
        } else {
            String body = fetch(requestId, "https://api.tvmaze.com/search/shows?q=" + URLEncoder.encode(query, "UTF-8"));
            JSONArray rows = new JSONArray(body);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i).getJSONObject("show"), image = row.optJSONObject("image");
                int duration = row.optInt("averageRuntime", row.optInt("runtime"));
                items.put(new JSONObject().put("name", row.getString("name")).put("kind", "series")
                    .put("year", year(row.optString("premiered"))).put("poster", image == null ? "" : secureUrl(image.optString("medium")))
                    .put("duration", duration > 0 && duration <= 600 ? duration : JSONObject.NULL)
                    .put("sources", new JSONArray().put("TVmaze")).put("url", secureUrl(row.optString("url")))
                    .put("preview", "").put("description", plainText(row.optString("summary")))
                    .put("tags", row.optJSONArray("genres") == null ? new JSONArray() : row.getJSONArray("genres")));
            }
        }
        JSONObject response = new JSONObject().put("items", items).put("source", kind.equals("movie") ? "Wikidata" : "TVmaze").put("cached", false);
        synchronized (searchCache) { searchCache.put(key, new CacheEntry(response)); }
        return response;
    }

    private JSONArray movieSearch(String id, String query) throws Exception {
        String base = "https://www.wikidata.org/w/api.php?format=json&";
        JSONObject found = new JSONObject(fetch(id, base + "action=wbsearchentities&type=item&language=it&uselang=it&limit=30&search=" + URLEncoder.encode(query, "UTF-8")));
        if (found.has("error")) throw new IllegalArgumentException("Wikidata non è disponibile ora. Riprova tra poco.");
        JSONArray matches = found.optJSONArray("search"), result = new JSONArray();
        if (matches == null || matches.length() == 0) return result;
        List<String> ids = new ArrayList<>();
        for (int i=0;i<matches.length();i++) { String value=matches.getJSONObject(i).optString("id"); if(value.matches("Q[0-9]+")) ids.add(value); }
        if(ids.isEmpty()) return result;
        JSONObject fetched = new JSONObject(fetch(id,base+"action=wbgetentities&props=labels%7Cdescriptions%7Cclaims&languages=it%7Cen&ids="+URLEncoder.encode(String.join("|",ids),"UTF-8")));
        JSONObject entities = fetched.optJSONObject("entities");
        if(entities==null) throw new IllegalArgumentException("Wikidata non ha restituito dati leggibili. Riprova.");
        Set<String> filmTypes = new HashSet<>(Arrays.asList("Q11424","Q202866","Q24869","Q24862","Q506240","Q226730"));
        Map<String, List<JSONObject>> images = new LinkedHashMap<>();
        for(String entityId:ids){
            JSONObject entity=entities.optJSONObject(entityId);if(entity==null)continue;
            JSONObject claims=entity.optJSONObject("claims"); if(claims==null)continue;
            boolean film=false;
            for(Object value:claimValues(claims,"P31")) if(value instanceof JSONObject&&filmTypes.contains(((JSONObject)value).optString("id")))film=true;
            if(!film)continue; // Never turn matching companies, games or franchises into films.
            String name=languageValue(entity.optJSONObject("labels"));if(name.isEmpty()||name.length()>300)continue;
            Object released=JSONObject.NULL, duration=JSONObject.NULL;
            for(Object value:claimValues(claims,"P577"))if(value instanceof JSONObject){
                String date=((JSONObject)value).optString("time").replaceFirst("^\\+","");Object candidate=year(date);
                if(candidate instanceof Integer&&(released==JSONObject.NULL||(Integer)candidate<(Integer)released))released=candidate;
            }
            for(Object value:claimValues(claims,"P2047"))if(value instanceof JSONObject){
                JSONObject amount=(JSONObject)value;
                try { double minutes=Double.parseDouble(amount.optString("amount"));String unit=amount.optString("unit");
                    if(unit.endsWith("/Q11574"))minutes/=60;
                    else if(unit.endsWith("/Q25235"))minutes*=60;
                    else if(!unit.endsWith("/Q7727"))continue;
                    int rounded=(int)Math.round(minutes);if(rounded>=1&&rounded<=600){duration=rounded;break;}
                }catch(NumberFormatException ignored){}
            }
            JSONObject item=new JSONObject().put("name",name).put("kind","movie").put("year",released).put("duration",duration)
                .put("sources",new JSONArray().put("Wikidata")).put("url","https://www.wikidata.org/wiki/"+entityId)
                .put("poster","").put("preview","").put("tags",new JSONArray())
                .put("description",languageValue(entity.optJSONObject("descriptions")));
            result.put(item);
            List<Object> artwork=claimValues(claims,"P18");
            if(!artwork.isEmpty()&&artwork.get(0) instanceof String){String title="File:"+artwork.get(0);images.computeIfAbsent(title,k->new ArrayList<>()).add(item);}
        }
        if(!images.isEmpty()){
            // Artwork is optional: a slow or unavailable image service never loses title metadata.
            try{
                String uri="https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=360&titles="+URLEncoder.encode(String.join("|",images.keySet()),"UTF-8");
                JSONObject pages=new JSONObject(fetch(id,uri,4500)).getJSONObject("query").getJSONObject("pages");
                Iterator<String> keys=pages.keys();
                while(keys.hasNext()){
                    JSONObject row=pages.getJSONObject(keys.next());JSONArray info=row.optJSONArray("imageinfo");if(info==null||info.length()==0)continue;
                    JSONObject file=info.getJSONObject(0), metadata=file.optJSONObject("extmetadata");if(metadata==null)continue;
                    String license=metadata.optJSONObject("LicenseShortName")==null?"":metadata.getJSONObject("LicenseShortName").optString("value");
                    if(!license.startsWith("CC ")&&!license.equals("CC0")&&!license.toLowerCase(Locale.ROOT).contains("public domain"))continue;
                    String artist=metadata.optJSONObject("Artist")==null?"":plainText(metadata.getJSONObject("Artist").optString("value"));
                    if(artist.length()>300)artist=artist.substring(0,300);
                    List<JSONObject> titles=images.get(row.optString("title"));if(titles==null)continue;
                    for(JSONObject item:titles)item.put("poster",secureUrl(file.optString("thumburl",file.optString("url"))))
                        .put("image_source",secureUrl(file.optString("descriptionurl"))).put("image_credit",artist+" · "+license);
                }
            }catch(Exception ignored){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
        }
        return result;
    }
    private static List<Object> claimValues(JSONObject claims,String property){
        List<Object> values=new ArrayList<>();JSONArray rows=claims.optJSONArray(property);if(rows==null)return values;
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null||row.optString("rank").equals("deprecated"))continue;
            JSONObject snak=row.optJSONObject("mainsnak"),data=snak==null?null:snak.optJSONObject("datavalue");if(data!=null){Object value=data.opt("value");if(value!=null&&value!=JSONObject.NULL)values.add(value);}}
        return values;
    }
    private static String languageValue(JSONObject values){
        if(values==null)return "";JSONObject text=values.optJSONObject("it");if(text==null)text=values.optJSONObject("en");return text==null?"":text.optString("value");
    }

    private String fetch(String id, String url) throws Exception {
        return fetch(id,url,12000);
    }
    private String fetch(String id, String url,int timeout) throws Exception {
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
        connections.put(id, connection);
        connection.setConnectTimeout(timeout); connection.setReadTimeout(timeout);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "VibraVidStudio/3.1 (Android catalog app; https://www.wikidata.org/)");
        try {
            int code = connection.getResponseCode();
            if (code == 429) throw new IllegalArgumentException("La fonte ha ricevuto troppe richieste. Attendi un minuto e riprova.");
            if (code != 200) throw new IllegalArgumentException("La fonte non è disponibile ora (" + code + "). Riprova più tardi.");
            return readBounded(connection.getInputStream(), 3 * 1024 * 1024);
        } finally { connection.disconnect(); connections.remove(id); }
    }

    private static Object year(String date) {
        try { int value = Integer.parseInt(date.substring(0, 4)); return value >= 1800 && value <= 2200 ? value : JSONObject.NULL; }
        catch (Exception e) { return JSONObject.NULL; }
    }
    private static String plainText(String value) {
        if (value.equals("null")) return "";
        String result = android.text.Html.fromHtml(value, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
        return result.length() > 6000 ? result.substring(0, 6000) : result;
    }
    private static String secureUrl(String value) {
        if (value.startsWith("http://")) value = "https://" + value.substring(7);
        try { return validHttps(value).toString(); } catch (Exception e) { return ""; }
    }
    private static Uri validHttps(String raw) {
        if (raw==null||raw.length() > 2000)
            throw new IllegalArgumentException("Usa un collegamento HTTPS completo.");
        return SourceCatalog.https(raw);
    }

    private void beginPicker(String id, String action, JSONObject data) {
        if (pickerId != null) { emit(id, false, "Completa prima il selettore già aperto.", null); return; }
        pickerId = id;
        pickerPurpose=data.optString("purpose", "library");pickerRestored=false;
        try {
            Intent intent;
            if (action.equals("export")) {
                exportBody = data.getString("serializedExport");
                if (exportBody.getBytes(StandardCharsets.UTF_8).length > MAX_DATA) throw new IllegalArgumentException("Esportazione troppo grande.");
                try (FileOutputStream out = new FileOutputStream(new File(getCacheDir(), "export.json"))) { out.write(exportBody.getBytes(StandardCharsets.UTF_8)); }
                pickerType = EXPORT;
                intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE, "VibraVid-libreria.json");
            } else if (action.equals("import")) {
                pickerType = IMPORT; intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
            } else {
                pickerType = VIDEO; intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            }
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, pickerType);
        } catch (Exception e) { emit(id, false, "Il selettore dei file non è disponibile.", null); pickerId = null; exportBody = null; }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("pickerId", pickerId); state.putInt("pickerType", pickerType);
        state.putString("pickerPurpose",pickerPurpose);
        super.onSaveInstanceState(state);
    }

    @Override protected void onActivityResult(int code, int result, Intent intent) {
        super.onActivityResult(code, result, intent);
        if (code == SOURCE) {
            if (result == RESULT_OK && web != null) web.evaluateJavascript("location.hash='files'", null);
            emit("", true, new JSONObject(), "downloadsChanged"); return;
        }
        if(code!=IMPORT&&code!=EXPORT&&code!=VIDEO)return;
        final String id = pickerId; final String body = exportBody;final boolean recovered=pickerRestored;final String purpose=pickerPurpose;
        pickerId = null; exportBody = null; pickerType = 0;
        if (id == null) return;
        if (result != RESULT_OK || intent == null || intent.getData() == null) {
            try { emit(id, true, new JSONObject().put("cancelled", true), null); } catch (Exception ignored) {} return;
        }
        Uri uri = intent.getData();
        try{tasks.submit(() -> {
            try {
                JSONObject value;
                if (code == IMPORT) value = new JSONObject().put("data", parseObject(readBounded(getContentResolver().openInputStream(uri), MAX_DATA)));
                else if (code == EXPORT) {
                    if (body == null) throw new IOException();
                    try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) { if (out == null) throw new IOException(); out.write(body.getBytes(StandardCharsets.UTF_8)); }
                    value = new JSONObject().put("saved", true);
                } else {
                    try{getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);}
                    catch(SecurityException error){throw new IllegalArgumentException("Il provider non consente un collegamento persistente. Scegli un video dalla memoria del telefono.");}
                    String name = "Video del dispositivo";
                    try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()&&cursor.getString(0)!=null&&!cursor.getString(0).trim().isEmpty()) name = cursor.getString(0);
                    }catch(RuntimeException ignored){}
                    String token = UUID.randomUUID().toString();
                    getSharedPreferences("media", MODE_PRIVATE).edit().putString(token, uri.toString()).apply();
                    value = new JSONObject().put("token", token).put("name", name);
                }
                if(recovered){JSONObject event=new JSONObject().put("type",code).put("purpose",purpose).put("result",value);runOnUiThread(()->{if(!alive)return;if(ready)emit("",true,event,"pickerRecovered");else recoveredPicker=event;});}
                else emit(id, true, value, null);
            } catch (Exception e) {String error=code == IMPORT ? "File non valido: scegli un'esportazione JSON di VibraVid." : friendlyError(e);if(recovered)runOnUiThread(()->{if(alive)Toast.makeText(MainActivity.this,AppLanguage.text(MainActivity.this,error),Toast.LENGTH_LONG).show();});else emit(id, false,error,null); }
        });}catch(RejectedExecutionException error){emit(id,false,"Attendi la fine dell’operazione in corso e riprova il selettore.",null);}
    }

    private Set<String> downloadIds() { return new HashSet<>(getSharedPreferences("downloads", MODE_PRIVATE).getStringSet("ids", Collections.emptySet())); }
    private JSONArray downloads() throws Exception {
        Set<String> ids = downloadIds(); JSONArray rows = MediaEngine.get(this).rows();
        if (ids.isEmpty()) return rows;
        long[] values = new long[ids.size()]; int n = 0;
        for (String id : ids)try{long value=Long.parseLong(id);values[n++]=value;}catch(NumberFormatException ignored){}
        if(n==0)return rows;values=Arrays.copyOf(values,n);
        DownloadManager manager = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(values))) {
            if (cursor == null) return rows;
            while (cursor.moveToNext()) rows.put(new JSONObject()
                .put("id", cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)))
                .put("name", cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)))
                .put("status", cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)))
                .put("reason", cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)))
                .put("bytes", cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)))
                .put("total", cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))));
        }
        return rows;
    }

    private long ownDownload(JSONObject data) {
        long id = data.optLong("id", -1);
        if (!downloadIds().contains(Long.toString(id))) throw new IllegalArgumentException("Questo download non appartiene a VibraVid.");
        return id;
    }
    private JSONObject cancelDownload(JSONObject data) throws Exception {
        long id = ownDownload(data);
        ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).remove(id);
        synchronized (dataLock) {
            Set<String> ids = downloadIds(); ids.remove(Long.toString(id));
            getSharedPreferences("downloads", MODE_PRIVATE).edit().putStringSet("ids", ids).apply();
        }
        return new JSONObject().put("removed", true);
    }
    private void openDownload(JSONObject data) {
        long id = ownDownload(data); DownloadManager manager = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        Uri uri = manager.getUriForDownloadedFile(id);
        if (uri == null) throw new IllegalArgumentException("Il download non è completo o il file è stato rimosso.");
        dispatchUi(() -> startPlayer(uri, data.optString("name", "Video")));
    }
    private void play(JSONObject data) {
        Uri uri;
        if (data.has("token")) {
            String stored = getSharedPreferences("media", MODE_PRIVATE).getString(data.optString("token"), null);
            if (stored == null) throw new IllegalArgumentException("Seleziona nuovamente questo video.");
            uri = Uri.parse(stored);
        } else uri = validHttps(data.optString("url"));
        final Uri destination = uri;
        dispatchUi(() -> startPlayer(destination, data.optString("name", "Video")));
    }
    private void startPlayer(Uri uri, String name) {
        startActivity(new Intent(this, VideoActivity.class).setData(uri).putExtra("name", name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    }
    private void openSource(JSONObject payload) throws Exception {
        String id = payload.getString("id"), query = payload.optString("query").trim();
        SourceCatalog.find(this, id);
        if (query.length() > 200) throw new IllegalArgumentException("Usa un titolo più breve.");
        dispatchUi(() -> startActivityForResult(new Intent(this, SourceActivity.class)
            .putExtra("source", id).putExtra("query", query).putExtra("wifiOnly", payload.optBoolean("wifiOnly")), SOURCE));
    }
    private void openLink(String url) {
        Uri uri = validHttps(url);
        dispatchUi(() -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
            catch (ActivityNotFoundException e) { Toast.makeText(this,AppLanguage.text(MainActivity.this,"Nessuna app può aprire il collegamento."), Toast.LENGTH_LONG).show(); }
        });
    }
    private void share(JSONObject data) {
        String text = data.optString("text");
        if (text.isEmpty() || text.length() > 4000) throw new IllegalArgumentException("Testo da condividere non valido.");
        dispatchUi(() -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Condividi con…")));
    }
    private void setTheme(JSONObject data) {
        boolean dark = data.optBoolean("dark");
        String background=data.optString("background",dark ? "#08090b" : "#111215");
        if(!background.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Colore non valido.");
        final int color=Color.parseColor(background);
        final long revision=data.optLong("revision",0);
        final boolean light=data.optBoolean("light",false);
        runOnUiThread(() -> {
            if(!alive||revision<themeRevision)return;
            themeRevision=revision;
            if(data.has("language"))AppLanguage.set(this,data.optString("language","it"));
            root.setBackgroundColor(color); if(web!=null)web.setBackgroundColor(color);
            getWindow().setStatusBarColor(color); getWindow().setNavigationBarColor(color);
            getWindow().getDecorView().setSystemUiVisibility(light ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
            getSharedPreferences("appearance",0).edit().putBoolean("cinema",dark).putBoolean("reducedMotion",data.optBoolean("reducedMotion",false)).putBoolean("light",light).putInt("background",color).apply();
        });
    }

    private void setRequirements(JSONObject data){
        final long revision=data.optLong("requirementsRevision",0);
        final boolean wifiOnly=data.optBoolean("wifiOnly");
        dispatchUi(()->{if(revision<requirementsRevision)return;requirementsRevision=revision;MediaEngine.get(this).requirements(wifiOnly);});
    }

    private static String readBounded(InputStream stream, int maximum) throws IOException {
        if (stream == null) throw new FileNotFoundException();
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192]; int count;
            while ((count = in.read(chunk)) != -1) {
                if (out.size() + count > maximum) throw new IOException("File troppo grande");
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                out.write(chunk, 0, count);
            }
            return out.toString("UTF-8");
        }
    }

    private static JSONObject parseObject(String text)throws JSONException {
        int depth=0;boolean quoted=false,escaped=false;
        for(int index=0;index<text.length();index++){
            char value=text.charAt(index);
            if(quoted){if(escaped)escaped=false;else if(value=='\\')escaped=true;else if(value=='"')quoted=false;continue;}
            if(value=='"')quoted=true;
            else if(value=='{'||value=='['){if(++depth>64)throw new JSONException("Il JSON contiene troppi livelli annidati.");}
            else if(value=='}'||value==']')depth--;
        }
        return new JSONObject(text);
    }

    private void hideVideoFullscreen(){
        if(fullscreenVideo==null)return;root.removeView(fullscreenVideo);fullscreenVideo=null;
        if(web!=null)web.setVisibility(View.VISIBLE);PlayerWindow.restore(getWindow());root.requestApplyInsets();
        WebChromeClient.CustomViewCallback callback=fullscreenCallback;fullscreenCallback=null;if(callback!=null)callback.onCustomViewHidden();
    }
    @Override public void onBackPressed() {
        if(fullscreenVideo!=null){hideVideoFullscreen();return;}
        if(web==null){super.onBackPressed();return;}
        web.evaluateJavascript("window.VibraApp&&window.VibraApp.back()", result -> {
            if (!"true".equals(result)) MainActivity.super.onBackPressed();
        });
    }
    @Override protected void onResume() { super.onResume();
        updateVisible=true;
        if(updateCompleted==null){updateCompleted=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){resumeUpdate();}};IntentFilter updateFilter=new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);androidx.core.content.ContextCompat.registerReceiver(this,updateCompleted,updateFilter,androidx.core.content.ContextCompat.RECEIVER_EXPORTED);}
        resumeUpdate();
        try{tasks.execute(()->DomainRefresh.refresh(this,false));}catch(RejectedExecutionException ignored){}
        try{MediaEngine.get(this);androidx.media3.exoplayer.offline.DownloadService.sendResumeDownloads(this,MediaDownloadService.class,false);}catch(RuntimeException error){Toast.makeText(this,AppLanguage.text(MainActivity.this,"La coda dei download non è disponibile. Riprova dopo aver controllato lo spazio libero."),Toast.LENGTH_LONG).show();}
        if (web != null) { web.onResume(); emit("", true, new JSONObject(), "resume"); }
    }
    @Override protected void onPause() { hideVideoFullscreen();updateVisible=false;if (web != null) web.onPause(); super.onPause(); }
    @Override protected void onDestroy() {
        alive = false; tasks.shutdownNow();
        if(updateCompleted!=null)try{unregisterReceiver(updateCompleted);}catch(RuntimeException ignored){}updateCompleted=null;
        for(androidx.media3.exoplayer.offline.DownloadHelper helper:preparations.values())MediaEngine.get(this).cancelPreparation(helper);preparations.clear();
        for(ProviderHttp.Call call:providerCalls.values())call.cancel();providerCalls.clear();if(providerEngine!=null)providerEngine.close();
        for (HttpURLConnection connection : connections.values()) connection.disconnect();
        if (completed != null) try { unregisterReceiver(completed); } catch (Exception ignored) {}
        if (web != null) { web.removeJavascriptInterface("VibraAndroid"); web.stopLoading();root.removeView(web); web.destroy(); web = null; }
        super.onDestroy();
    }
}
