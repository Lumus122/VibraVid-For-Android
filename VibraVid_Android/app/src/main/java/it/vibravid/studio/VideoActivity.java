package it.vibravid.studio;

import android.app.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Rational;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

/** Completed downloads use a cache-only source: playback cannot silently stream online. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class VideoActivity extends Activity {
    private ExoPlayer player;
    private PlayerView view;
    private String key;
    private String offlineId;
    private AlertDialog errorDialog;
    private long position;
    private boolean playing = true, offline, local, errorShown, lifecyclePause;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            String id = getIntent().getStringExtra("download_id"), name = getIntent().getStringExtra("name");
            MediaItem item;
            DefaultMediaSourceFactory sources;
            if (id != null) {
                MediaEngine engine = MediaEngine.get(this);
                DownloadRequest request = engine.completed(id);
                item = request.toMediaItem(); sources = new DefaultMediaSourceFactory(engine.offlineFactory());
                name = MediaEngine.metadata(request).optString("name", "Video offline"); key = "offline-" + id; offline = true; offlineId=id;
            } else {
                Uri uri = getIntent().getData();
                if (uri == null || (!"https".equals(uri.getScheme()) && !"content".equals(uri.getScheme())))throw new IllegalArgumentException("Seleziona un video o un collegamento HTTPS valido.");
                local="content".equals(uri.getScheme());
                item = MediaItem.fromUri(uri); sources = new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this));
                key = "position-" + uri.toString().hashCode();
            }
            Object savedPosition = getSharedPreferences("playback", 0).getAll().get(key);
            position = state == null ? (savedPosition instanceof Number ? ((Number)savedPosition).longValue() : 0) : state.getLong("position");
            position=Math.max(0,position);playing=state==null||state.getBoolean("playing",true);
            FrameLayout root = new FrameLayout(this); root.setBackgroundColor(getColor(R.color.cinema_background));
            root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
            view = new PlayerView(this); view.setShowSubtitleButton(true); view.setControllerShowTimeoutMs(3500);
            root.addView(view, new FrameLayout.LayoutParams(-1, -1));
            TextView title = new TextView(this); title.setText("‹  " + (name == null ? "Video" : name) + (offline ? " · Offline" : ""));
            title.setTextColor(Color.WHITE); title.setTextSize(16); title.setPadding(24, 22, 24, 22); title.setBackgroundColor(0xb3101010);
            root.addView(title, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP)); title.setOnClickListener(v -> finish());
            setContentView(root);root.requestApplyInsets();if(playing)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            player = new ExoPlayer.Builder(this).setMediaSourceFactory(sources).build();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
            player.setHandleAudioBecomingNoisy(true); view.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override public void onPlayWhenReadyChanged(boolean ready,int reason){
                    if(!lifecyclePause)playing=ready;
                    if(ready&&player.getPlaybackState()!=Player.STATE_ENDED)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
                @Override public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_ENDED) {
                        position = 0; getSharedPreferences("playback", 0).edit().remove(key).apply();
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                }
                @Override public void onPlayerError(PlaybackException error) {
                    if (errorShown) return; errorShown = true;
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    if(offline&&error.errorCode>=2000&&error.errorCode<3000){
                        MediaEngine.get(VideoActivity.this).missing(offlineId);
                        errorDialog=new AlertDialog.Builder(VideoActivity.this).setTitle("Completiamo il video offline")
                            .setMessage("Alcune parti salvate non sono disponibili. VibraVid può recuperare quelle mancanti dalla fonte, conservando le parti già presenti.")
                            .setPositiveButton("Ripara download",(d,w)->{
                                try{MediaEngine.get(VideoActivity.this).repair(offlineId);}catch(Exception e){Toast.makeText(VideoActivity.this,MediaEngine.message(e),Toast.LENGTH_LONG).show();}finish();
                            }).setNegativeButton("Torna ai download",(d,w)->finish()).show();return;
                    }
                    errorDialog=new AlertDialog.Builder(VideoActivity.this).setTitle("Il video non si apre")
                        .setMessage(local&&error.errorCode>=2000&&error.errorCode<3000?"Il file del dispositivo non è più accessibile oppure il permesso è scaduto. Selezionalo nuovamente in Download con Scegli video.":offline ? "Il telefono potrebbe non supportare il formato, oppure mancano parti del download. Rileva nuovamente il video dalla fonte e scaricalo di nuovo." : "Il formato potrebbe non essere supportato o il collegamento è scaduto.")
                        .setPositiveButton("Chiudi", (d,w) -> finish()).show();
                }
            });
            player.setMediaItem(item); player.seekTo(position); player.prepare(); player.setPlayWhenReady(playing);
        } catch (Exception error) { Toast.makeText(this, MediaEngine.message(error), Toast.LENGTH_LONG).show(); finish(); }
    }
    private void remember() {
        if (player == null || key == null || player.getPlaybackState() == Player.STATE_IDLE) return;
        position = player.getPlaybackState() == Player.STATE_ENDED ? 0 : player.getCurrentPosition();
        getSharedPreferences("playback", 0).edit().putLong(key, position).apply();
    }
    @Override protected void onSaveInstanceState(Bundle state) { remember(); state.putLong("position", position);state.putBoolean("playing",playing); super.onSaveInstanceState(state); }
    @Override public void onUserLeaveHint() {
        if (player != null && player.isPlaying() && getPackageManager().hasSystemFeature("android.software.picture_in_picture"))try{
            enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build());
        }catch(IllegalStateException|IllegalArgumentException|SecurityException error){android.util.Log.w("VibraVid","PiP non disponibile sul dispositivo");}
    }
    @Override public void onPictureInPictureModeChanged(boolean inPip, android.content.res.Configuration config) {
        super.onPictureInPictureModeChanged(inPip, config); if (view != null) view.setUseController(!inPip);
    }
    private void pauseForLifecycle(){if(player==null)return;lifecyclePause=true;try{player.pause();}finally{lifecyclePause=false;}}
    @Override protected void onPause() { remember(); if (!isInPictureInPictureMode())pauseForLifecycle(); super.onPause(); }
    @Override protected void onStop() { remember();pauseForLifecycle();super.onStop(); }
    @Override protected void onResume() { super.onResume(); if (player != null && playing&&!errorShown&&!isFinishing()) player.play(); }
    @Override protected void onDestroy() {if(errorDialog!=null){errorDialog.dismiss();errorDialog=null;} if (view != null) view.setPlayer(null); if (player != null) player.release(); super.onDestroy(); }
}
