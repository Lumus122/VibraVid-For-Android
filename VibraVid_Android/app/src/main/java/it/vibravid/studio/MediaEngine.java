package it.vibravid.studio;

import android.content.Context;
import android.net.Uri;
import android.os.*;
import android.webkit.CookieManager;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.datasource.cache.*;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.offline.*;
import androidx.media3.exoplayer.scheduler.Requirements;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Persistent offline media, including manifest, selected audio/video and HLS keys. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class MediaEngine {
    private static MediaEngine instance;
    public final DownloadManager manager;
    public final SimpleCache cache;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService segments=Executors.newFixedThreadPool(3);
    private final Map<DownloadHelper,Runnable> preparations=new HashMap<>();

    public interface Callback { void success(String id); void failure(String message); }
    public static synchronized MediaEngine get(Context context){
        if(instance==null)instance=new MediaEngine(context.getApplicationContext());return instance;
    }
    private MediaEngine(Context context){
        this.context=context;
        File parent=context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES);
        if(parent==null)parent=context.getFilesDir();
        StandaloneDatabaseProvider database=new StandaloneDatabaseProvider(context);
        cache=new SimpleCache(new File(parent,"offline-cache"),new NoOpCacheEvictor(),database);
        DownloaderFactory factory=request->{
            JSONObject meta=metadata(request);
            CacheDataSource.Factory data=new CacheDataSource.Factory().setCache(cache)
                .setUpstreamDataSourceFactory(network(meta))
                .setCacheWriteDataSinkFactory(new CacheDataSink.Factory().setCache(cache));
            if(!"application/x-mpegURL".equals(request.mimeType)&&!"application/dash+xml".equals(request.mimeType))return new DefaultDownloaderFactory(data,segments).createDownloader(request);
            AtomicBoolean reported=new AtomicBoolean();
            Map<String,TreeMap<Long,Long>> requestedRanges=new HashMap<>();
            data.setCacheKeyFactory(spec->{
                String key=CacheKeyFactory.DEFAULT.buildCacheKey(spec);
                synchronized(requestedRanges){if(!reported.get()){
                    long end=spec.length==C.LENGTH_UNSET||spec.length>Long.MAX_VALUE-spec.position?Long.MAX_VALUE:spec.position+spec.length;
                    TreeMap<Long,Long> ranges=requestedRanges.get(key);if(ranges==null){ranges=new TreeMap<>();requestedRanges.put(key,ranges);}
                    Long previous=ranges.get(spec.position);ranges.put(spec.position,previous==null?end:Math.max(previous,end));
                }}
                return key;
            });
            Downloader delegate=new DefaultDownloaderFactory(data,segments).createDownloader(request);
            return new Downloader(){
                @Override public void download(ProgressListener listener)throws IOException,InterruptedException{
                    reported.set(false);synchronized(requestedRanges){requestedRanges.clear();}
                    delegate.download((length,bytes,percent)->{
                        reported.set(true);synchronized(requestedRanges){requestedRanges.clear();}
                        if(listener!=null)listener.onProgress(length,bytes,percent);
                    });
                    // Media3 1.5.1 emits no progress when every selected segment is already cached.
                    if(listener!=null&&!reported.get()){
                        long bytes=0;
                        synchronized(requestedRanges){for(Map.Entry<String,TreeMap<Long,Long>> entry:requestedRanges.entrySet()){
                            long start=-1,end=-1;
                            for(Map.Entry<Long,Long> range:entry.getValue().entrySet()){
                                if(start<0){start=range.getKey();end=range.getValue();}
                                else if(range.getKey()<=end)end=Math.max(end,range.getValue());
                                else{bytes+=cache.getCachedBytes(entry.getKey(),start,end==Long.MAX_VALUE?C.LENGTH_UNSET:end-start);start=range.getKey();end=range.getValue();}
                            }
                            if(start>=0)bytes+=cache.getCachedBytes(entry.getKey(),start,end==Long.MAX_VALUE?C.LENGTH_UNSET:end-start);
                        }requestedRanges.clear();}
                        listener.onProgress(bytes,bytes,100);
                    }
                }
                @Override public void cancel(){delegate.cancel();}
                @Override public void remove(){delegate.remove();}
            };
        };
        DownloadManager createdManager=null;
        try{
        manager=createdManager=new DownloadManager(context,new DefaultDownloadIndex(database),factory);
        manager.setMaxParallelDownloads(2);
        manager.setMinRetryCount(3);
        manager.setRequirements(new Requirements(context.getSharedPreferences("offline",0).getBoolean("wifiOnly",false)?Requirements.NETWORK_UNMETERED:Requirements.NETWORK));
        manager.addListener(new DownloadManager.Listener(){
            @Override public void onDownloadRemoved(DownloadManager manager,Download download){
                context.getSharedPreferences("offline_errors",0).edit().remove(download.request.id).apply();
                context.getSharedPreferences("playback",0).edit().remove("offline-"+download.request.id).apply();
            }
            @Override public void onDownloadChanged(DownloadManager manager,Download download,Exception error){
                if(error!=null){String message=message(error);context.getSharedPreferences("offline_errors",0).edit().putString(download.request.id,message).apply();}
                else if(download.state==Download.STATE_COMPLETED)context.getSharedPreferences("offline_errors",0).edit().remove(download.request.id).apply();
            }
        });
        }catch(RuntimeException error){
            // A failed database/manager startup must release the cache directory lock before retry.
            if(createdManager!=null)try{createdManager.release();}catch(RuntimeException cleanup){error.addSuppressed(cleanup);}
            segments.shutdownNow();
            try{cache.release();}catch(RuntimeException cleanup){error.addSuppressed(cleanup);}
            try{database.close();}catch(RuntimeException cleanup){error.addSuppressed(cleanup);}
            throw error;
        }
    }
    public void requirements(boolean wifiOnly){
        main.post(()->{context.getSharedPreferences("offline",0).edit().putBoolean("wifiOnly",wifiOnly).apply();manager.setRequirements(new Requirements(wifiOnly?Requirements.NETWORK_UNMETERED:Requirements.NETWORK));});
    }
    public DownloadHelper enqueue(JSONObject payload,Callback callback)throws Exception{
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Prepare downloads on the application thread");
        String url=SourceCatalog.https(payload.getString("url")).toString();
        String mime=MediaDetector.type(url,payload.optString("mime"));
        if(mime==null)throw new IllegalArgumentException("Serve un video MP4/WebM oppure un flusso HLS/DASH, non la pagina del sito.");
        String name=payload.optString("name").trim();if(name.isEmpty()||name.length()>180)throw new IllegalArgumentException("Inserisci un nome per il video, fino a 180 caratteri.");
        JSONObject meta=new JSONObject().put("name",name).put("source",payload.optString("source", "Collegamento"))
            .put("sourcePage",payload.optString("sourcePage", ""))
            .put("headerOrigin",Uri.parse(url).getScheme()+"://"+Uri.parse(url).getEncodedAuthority())
            .put("headers",safeHeaders(payload.optJSONObject("headers")));
        String providerKey=payload.optString("providerKey"),downloadIdentity=url;
        if(!providerKey.isEmpty()&&providerKey.length()<=300){downloadIdentity="provider:"+providerKey+":"+Math.max(360,Math.min(2160,payload.optInt("quality",720)));meta.put("providerKey",providerKey);}
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(downloadIdentity.getBytes(StandardCharsets.UTF_8));
        StringBuilder identity=new StringBuilder();for(byte value:digest)identity.append(String.format(Locale.ROOT,"%02x",value&255));
        String id=identity.toString();byte[] bytes=meta.toString().getBytes(StandardCharsets.UTF_8);
        if(!providerKey.isEmpty()){
            Download existing=manager.getDownloadIndex().getDownload(id);
            if(existing!=null&&existing.state!=Download.STATE_FAILED&&existing.state!=Download.STATE_REMOVING&&!context.getSharedPreferences("offline_errors",0).getString(id,"").startsWith("incomplete:")){callback.success(id);return null;}
        }
        MediaItem item=new MediaItem.Builder().setUri(url).setMimeType(mime).build();
        if(!mime.equals("application/x-mpegURL")&&!mime.equals("application/dash+xml")){
            DownloadRequest request=new DownloadRequest.Builder(id,Uri.parse(url)).setMimeType(mime).setData(bytes).build();
            DownloadService.sendAddDownload(context,MediaDownloadService.class,request,false);callback.success(id);return null;
        }
        int maximum=Math.max(360,Math.min(2160,payload.optInt("quality",720)));
        TrackSelectionParameters tracks=new TrackSelectionParameters.Builder(context).setMaxVideoSize(maximum*16/9,maximum)
            .setPreferredVideoMimeTypes(MimeTypes.VIDEO_H264)
            .setPreferredAudioLanguages("it","ita","en","eng").setPreferredTextLanguages("it","ita")
            .setForceHighestSupportedBitrate(true).build();
        DownloadHelper helper=DownloadHelper.forMediaItem(item,tracks,new DefaultRenderersFactory(context),network(meta));
        AtomicBoolean finished=new AtomicBoolean();
        Runnable timeout=()->{if(finished.compareAndSet(false,true)){preparations.remove(helper);helper.release();callback.failure("La preparazione è scaduta. Riprova il download.");}};
        preparations.put(helper,()->{if(finished.compareAndSet(false,true)){main.removeCallbacks(timeout);helper.release();}});
        main.postDelayed(timeout,30000);
        try{helper.prepare(new DownloadHelper.Callback(){
            @Override public void onPrepared(DownloadHelper prepared){
                if(!finished.compareAndSet(false,true))return;main.removeCallbacks(timeout);preparations.remove(prepared);
                try{
                    boolean video=false,protectedVideo=false;
                    for(int p=0;p<prepared.getPeriodCount();p++)for(Tracks.Group group:prepared.getTracks(p).getGroups())for(int t=0;t<group.length;t++){
                        Format format=group.getTrackFormat(t);
                        if(group.getType()==C.TRACK_TYPE_VIDEO&&group.isSelected())video=true;
                        if(format.drmInitData!=null)protectedVideo=true;
                    }
                    if(protectedVideo)throw new IllegalArgumentException("Questo video richiede una licenza offline del servizio. Il download dal player della fonte non è disponibile per contenuti DRM.");
                    if(!video)throw new IllegalArgumentException("La fonte non ha fornito una traccia video compatibile da scaricare. Riprova o scegli un’altra fonte.");
                    DownloadRequest request=prepared.getDownloadRequest(id,bytes);
                    if(request.streamKeys.isEmpty())throw new IllegalArgumentException("La fonte non ha fornito tracce scaricabili compatibili con il telefono.");
                    DownloadService.sendAddDownload(context,MediaDownloadService.class,request,false);callback.success(id);
                }catch(Exception e){callback.failure(message(e));}finally{prepared.release();}
            }
            @Override public void onPrepareError(DownloadHelper prepared,IOException error){
                if(!finished.compareAndSet(false,true))return;main.removeCallbacks(timeout);preparations.remove(prepared);prepared.release();callback.failure(message(error));
            }
        });}catch(RuntimeException error){cancelPreparation(helper);throw error;}
        return helper;
    }
    public void cancelPreparation(DownloadHelper helper){
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->cancelPreparation(helper));return;}
        Runnable cancel=preparations.remove(helper);if(cancel!=null)cancel.run();
    }
    public JSONArray rows()throws Exception{
        JSONArray rows=new JSONArray();
        try(DownloadCursor cursor=manager.getDownloadIndex().getDownloads()){
            while(cursor.moveToNext()){
                Download d=cursor.getDownload();JSONObject meta=metadata(d.request);
                int status=d.state==Download.STATE_COMPLETED?8:d.state==Download.STATE_FAILED?16:d.state==Download.STATE_STOPPED?4:d.state==Download.STATE_DOWNLOADING?2:1;
                String error=context.getSharedPreferences("offline_errors",0).getString(d.request.id,"");
                if(error.startsWith("incomplete:")&&status==8)status=16;
                rows.put(new JSONObject().put("id",d.request.id).put("name",meta.optString("name","Video"))
                    .put("engine","offline").put("source",meta.optString("source")).put("status",status)
                    .put("bytes",d.getBytesDownloaded()).put("total",d.contentLength).put("percent",d.getPercentDownloaded())
                    .put("paused",d.state==Download.STATE_STOPPED).put("time",d.startTimeMs)
                    .put("error",error.replaceFirst("^incomplete:","")));
            }
        }
        return rows;
    }
    public DownloadRequest completed(String id)throws Exception{
        Download d=manager.getDownloadIndex().getDownload(id);
        if(d==null||d.state!=Download.STATE_COMPLETED||context.getSharedPreferences("offline_errors",0).getString(id,"").startsWith("incomplete:"))throw new IllegalArgumentException("Il video non è completo. Riprendi o ripara il download.");
        return d.request;
    }
    public void pause(String id,boolean pause)throws Exception{
        Download existing=manager.getDownloadIndex().getDownload(id);
        if(existing==null)throw new IllegalArgumentException("Questo download non esiste più.");
        if(!pause&&(existing.state==Download.STATE_FAILED||existing.state==Download.STATE_COMPLETED))DownloadService.sendAddDownload(context,MediaDownloadService.class,existing.request,false);
        DownloadService.sendSetStopReason(context,MediaDownloadService.class,id,pause?1:Download.STOP_REASON_NONE,false);
        if(!pause)DownloadService.sendResumeDownloads(context,MediaDownloadService.class,false);
    }
    public void remove(String id)throws Exception{
        if(manager.getDownloadIndex().getDownload(id)==null)throw new IllegalArgumentException("Questo download non esiste più.");
        DownloadService.sendRemoveDownload(context,MediaDownloadService.class,id,false);
    }
    public void repair(String id)throws Exception{
        Download existing=manager.getDownloadIndex().getDownload(id);
        if(existing==null)throw new IllegalArgumentException("Questo download non esiste più.");
        DownloadService.sendAddDownload(context,MediaDownloadService.class,existing.request,false);
        DownloadService.sendResumeDownloads(context,MediaDownloadService.class,false);
    }
    public void missing(String id){
        context.getSharedPreferences("offline_errors",0).edit().putString(id,"incomplete:Alcune parti del video non sono disponibili. Riprendi il download per recuperarle.").apply();
    }
    public CacheDataSource.Factory offlineFactory(){
        return new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(null).setCacheWriteDataSinkFactory(null);
    }
    public static JSONObject metadata(DownloadRequest request){
        try{return new JSONObject(new String(request.data,StandardCharsets.UTF_8));}catch(Exception e){return new JSONObject();}
    }
    private static JSONObject safeHeaders(JSONObject input)throws JSONException{
        JSONObject out=new JSONObject();if(input==null)return out;
        Iterator<String> keys=input.keys();while(keys.hasNext()){
            String key=keys.next(),lower=key.toLowerCase(Locale.ROOT),value=input.optString(key);
            if(Arrays.asList("user-agent","referer","origin","authorization").contains(lower)&&value.length()<8000&&value.indexOf('\r')<0&&value.indexOf('\n')<0)out.put(key,value);
        }
        return out;
    }
    private DataSource.Factory network(JSONObject meta){return ()->new SourceHttpDataSource(meta);}
    public static String message(Exception error){
        if(isStorageFull(error))return "Spazio sul telefono esaurito. Libera spazio e riprendi il download.";
        if(error instanceof IllegalArgumentException&&error.getMessage()!=null)return error.getMessage();
        if(error instanceof DownloadHelper.LiveContentUnsupportedException)return "Questo è un flusso in diretta. Il download offline richiede un video completo a richiesta.";
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        Throwable cause=error;while(cause!=null&&seen.add(cause)){
            if(cause instanceof java.net.UnknownHostException)return "Connessione assente. I video già scaricati restano disponibili offline.";
            if(cause instanceof java.net.SocketTimeoutException)return "La fonte impiega troppo tempo. Riprova il download tra poco.";
            if(cause instanceof HttpDataSource.InvalidResponseCodeException){int code=((HttpDataSource.InvalidResponseCodeException)cause).responseCode;
                return code==401||code==403?"La fonte ha rifiutato il download. Riprova la ricerca o scegli un’altra fonte.":"La fonte ha risposto con errore "+code+". Puoi riprovare o scegliere un altro flusso.";}
            if(cause.getCause()==cause)break;cause=cause.getCause();
        }
        return "Il video non è stato preparato o scaricato. Verifica la connessione e avvia nuovamente il player della fonte.";
    }
    public static boolean isStorageFull(Throwable error){
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        while(error!=null&&seen.add(error)){if(error instanceof android.database.sqlite.SQLiteFullException||error instanceof android.system.ErrnoException&&((android.system.ErrnoException)error).errno==android.system.OsConstants.ENOSPC)return true;error=error.getCause();}return false;
    }
}
