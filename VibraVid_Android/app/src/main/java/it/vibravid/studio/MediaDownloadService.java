package it.vibravid.studio;

import android.app.Notification;
import androidx.media3.exoplayer.offline.*;
import androidx.media3.exoplayer.scheduler.*;
import java.util.List;

/** Foreground service keeps the real download queue alive when the browser closes. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class MediaDownloadService extends DownloadService {
    private DownloadNotificationHelper notifications;
    private android.os.PowerManager.WakeLock wake;
    private DownloadManager.Listener listener;
    private boolean engineReady;
    public MediaDownloadService(){super(4101,1000,"vibravid_downloads",R.string.download_channel,0);}
    @Override public void onCreate(){
        try{MediaEngine.get(this);super.onCreate();engineReady=true;}
        catch(RuntimeException error){android.util.Log.e("VibraVid","Coda download non disponibile",error);android.widget.Toast.makeText(this,"La coda dei download non è disponibile. Controlla lo spazio libero e riapri VibraVid.",android.widget.Toast.LENGTH_LONG).show();stopSelf();return;}
        wake=((android.os.PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"VibraVid:download");
        wake.setReferenceCounted(false);
        listener=new DownloadManager.Listener(){
            @Override public void onDownloadChanged(DownloadManager manager,Download download,Exception error){updateWake();}
            @Override public void onIdle(DownloadManager manager){updateWake();}
        };
        getDownloadManager().addListener(listener);updateWake();
    }
    @Override public int onStartCommand(android.content.Intent intent,int flags,int startId){if(!engineReady){stopSelf(startId);return START_NOT_STICKY;}return super.onStartCommand(intent,flags,startId);}
    private void updateWake(){
        boolean active=false;for(Download download:getDownloadManager().getCurrentDownloads())if(download.state==Download.STATE_DOWNLOADING)active=true;
        if(active)wake.acquire(10*60*1000L);else if(wake.isHeld())wake.release();
    }
    @Override public void onDestroy(){
        if(!engineReady)return;
        if(listener!=null)getDownloadManager().removeListener(listener);if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();
    }
    @Override protected DownloadManager getDownloadManager(){return MediaEngine.get(this).manager;}
    @Override protected Scheduler getScheduler(){return new PlatformScheduler(this,4102);}
    @Override protected Notification getForegroundNotification(List<Download> downloads,int unmetRequirements){
        if(wake!=null)updateWake();
        if(notifications==null)notifications=new DownloadNotificationHelper(this,"vibravid_downloads");
        return notifications.buildProgressNotification(this,R.drawable.ic_download,null,"VibraVid · Download sul telefono",downloads,unmetRequirements);
    }
    @Override public void onTimeout(int startId,int type){if(engineReady)MediaEngine.get(this).manager.pauseDownloads();stopSelf();}
}
