package it.vibravid.studio;

import android.Manifest;
import android.app.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.Rational;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import java.util.*;

/** Completed downloads use a cache-only source: playback cannot silently stream online. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class VideoActivity extends Activity implements CinemaControls.Actions {
    private ExoPlayer player;
    private PlayerView view;
    private CinemaControls controls;
    private String key,offlineId,title="Video",titleIdentity="";
    private AlertDialog errorDialog,optionsDialog;
    private long position,lastExportNotice;
    private boolean playing=true,offline,local,errorShown,lifecyclePause,fill,resumed,exportPermissionPending;
    private Uri sourceUri;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final GalleryExportService.Listener galleryListener=this::galleryChanged;
    private final Runnable tick=new Runnable(){@Override public void run(){if(controls!=null&&controls.shown())refresh();if(resumed&&player!=null)handler.postDelayed(this,500);}};

    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(AppLanguage.context(base));}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try{
            String id=getIntent().getStringExtra("download_id"),name=getIntent().getStringExtra("name");
            MediaItem item;DefaultMediaSourceFactory sources;
            if(id!=null){
                MediaEngine engine=MediaEngine.get(this);DownloadRequest request=engine.completed(id);
                item=request.toMediaItem();sources=new DefaultMediaSourceFactory(engine.offlineFactory());
                name=MediaEngine.metadata(request).optString("name","Video offline");titleIdentity=MediaEngine.metadata(request).optString("titleIdentity");key="offline-"+id;offline=true;offlineId=id;
            }else{
                sourceUri=getIntent().getData();
                if(sourceUri==null||(!"https".equals(sourceUri.getScheme())&&!"content".equals(sourceUri.getScheme())))throw new IllegalArgumentException("Seleziona un video o un collegamento HTTPS valido.");
                local="content".equals(sourceUri.getScheme());item=MediaItem.fromUri(sourceUri);sources=new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this));key="position-"+sourceUri.toString().hashCode();
            }
            title=name==null||name.trim().isEmpty()?"Video":name;
            Object savedPosition=getSharedPreferences("playback",0).getAll().get(key);
            position=state==null?(savedPosition instanceof Number?((Number)savedPosition).longValue():0):state.getLong("position");
            position=Math.max(0,position);playing=state==null||state.getBoolean("playing",true);fill=state!=null&&state.getBoolean("fill");
            lastExportNotice=state==null?0:state.getLong("exportNotice");exportPermissionPending=state!=null&&state.getBoolean("exportPermissionPending");
            FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
            view=new PlayerView(this);view.setUseController(false);view.setKeepContentOnPlayerReset(true);view.setResizeMode(fill?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:AspectRatioFrameLayout.RESIZE_MODE_FIT);
            root.addView(view,new FrameLayout.LayoutParams(-1,-1));
            View gestures=new View(this);gestures.setContentDescription(AppLanguage.text(VideoActivity.this,"Mostra o nascondi i controlli del video"));gestures.setOnClickListener(v->{if(controls!=null)controls.toggle();});root.addView(gestures,new FrameLayout.LayoutParams(-1,-1));
            controls=new CinemaControls(this,title,sourceLabel(),getSharedPreferences("appearance",0).getBoolean("reducedMotion",false),this);
            controls.pipAvailable(getPackageManager().hasSystemFeature("android.software.picture_in_picture"));root.addView(controls,new FrameLayout.LayoutParams(-1,-1));
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
                androidx.core.graphics.Insets safe=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout()|androidx.core.view.WindowInsetsCompat.Type.systemBars());
                controls.safeInsets(safe.left,safe.top,safe.right,safe.bottom);return insets;
            });
            installGestures(gestures);setContentView(root);immersive();root.requestApplyInsets();
            if(playing)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            player=new ExoPlayer.Builder(this).setMediaSourceFactory(sources).build();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
            player.setHandleAudioBecomingNoisy(true);view.setPlayer(player);
            player.addListener(new Player.Listener(){
                @Override public void onPlayWhenReadyChanged(boolean ready,int reason){
                    if(!lifecyclePause)playing=ready;
                    if(ready&&player.getPlaybackState()!=Player.STATE_ENDED)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    refresh();controls.interacted();
                }
                @Override public void onPlaybackStateChanged(int playbackState){
                    if(playbackState==Player.STATE_ENDED){position=0;getSharedPreferences("playback",0).edit().remove(key).apply();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
                    refresh();
                }
                @Override public void onPlayerError(PlaybackException error){showPlaybackError(error);}
            });
            float speed=state==null?1f:state.getFloat("speed",1f);if(!Float.isFinite(speed)||speed<.5f||speed>2f)speed=1f;
            player.setPlaybackSpeed(speed);controls.speedLabel(speed);controls.fitLabel(fill);
            player.setMediaItem(item);player.seekTo(position);player.prepare();player.setPlayWhenReady(playing);
        }catch(Exception error){toast(MediaEngine.message(error));finish();}
    }
    private String sourceLabel(){return offline?"VIDEO OFFLINE":local?"VIDEO DEL DISPOSITIVO":"VIDEO ONLINE";}
    private void immersive(){PlayerWindow.immersive(getWindow());}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);if(!isInPictureInPictureMode())immersive();if(controls!=null){controls.requestApplyInsets();controls.hideControls();}}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&!isInPictureInPictureMode())immersive();}
    private void refresh(){if(player!=null&&controls!=null){boolean ready=player.getPlayWhenReady()&&player.getPlaybackState()!=Player.STATE_ENDED;controls.update(player.getCurrentPosition(),player.getDuration(),player.getBufferedPosition(),ready,player.getPlaybackState()==Player.STATE_BUFFERING);}}
    private void toast(String message){Toast.makeText(this,AppLanguage.text(this,message),Toast.LENGTH_LONG).show();}
    private void showPlaybackError(PlaybackException error){
        if(errorShown||isFinishing())return;errorShown=true;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(offline&&error.errorCode>=2000&&error.errorCode<3000){
            MediaEngine.get(this).missing(offlineId);
            errorDialog=new AlertDialog.Builder(this).setTitle(AppLanguage.text(VideoActivity.this,"Completiamo il video offline"))
                .setMessage(AppLanguage.text(VideoActivity.this,"Alcune parti salvate non sono disponibili. VibraVid può recuperare quelle mancanti dalla fonte, conservando le parti già presenti."))
                .setPositiveButton(AppLanguage.text(VideoActivity.this,"Ripara download"),(d,w)->{try{MediaEngine.get(this).repair(offlineId);}catch(Exception e){toast(MediaEngine.message(e));}finish();}).setNegativeButton(AppLanguage.text(VideoActivity.this,"Torna ai download"),(d,w)->finish()).show();return;
        }
        errorDialog=new AlertDialog.Builder(this).setTitle(AppLanguage.text(VideoActivity.this,"Il video non si apre"))
            .setMessage(AppLanguage.text(VideoActivity.this,local&&error.errorCode>=2000&&error.errorCode<3000?"Il file del dispositivo non è più accessibile oppure il permesso è scaduto. Selezionalo nuovamente in Download con Scegli video.":offline?"Il telefono potrebbe non supportare il formato, oppure mancano parti del download. Rileva nuovamente il video dalla fonte e scaricalo di nuovo.":"Il formato potrebbe non essere supportato o il collegamento è scaduto."))
            .setPositiveButton(AppLanguage.text(VideoActivity.this,"Chiudi"),(d,w)->finish()).show();
    }
    @Override public void back(){finish();}
    @Override public void play(){if(player==null||errorShown)return;if(player.getPlaybackState()==Player.STATE_ENDED){player.seekTo(0);player.play();}else if(player.getPlayWhenReady())player.pause();else player.play();refresh();}
    @Override public void seek(long target){if(player==null||!player.isCurrentMediaItemSeekable())return;long length=player.getDuration();player.seekTo(Math.max(0,length>0?Math.min(target,Math.max(0,length-1)):target));refresh();}
    @Override public void jump(long amount){if(player!=null)seek(player.getCurrentPosition()+amount);}
    @Override public void fit(){fill=!fill;view.setResizeMode(fill?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:AspectRatioFrameLayout.RESIZE_MODE_FIT);controls.fitLabel(fill);controls.notice(fill?"Riempi schermo":"Proporzioni originali");}
    private AlertDialog.Builder dialog(String title){if(optionsDialog!=null)optionsDialog.dismiss();return new AlertDialog.Builder(this).setTitle(AppLanguage.text(this,title));}
    @Override public void speed(){
        if(player==null)return;float[] speeds={.5f,.75f,1f,1.25f,1.5f,2f};String[] labels={"0.5×","0.75×",AppLanguage.text(this,"Normale · 1×"),"1.25×","1.5×","2×"};int selected=2;
        for(int i=0;i<speeds.length;i++)if(Math.abs(player.getPlaybackParameters().speed-speeds[i])<.01f)selected=i;
        optionsDialog=dialog("Velocità di riproduzione").setSingleChoiceItems(labels,selected,(d,index)->{player.setPlaybackSpeed(speeds[index]);controls.speedLabel(speeds[index]);d.dismiss();controls.showControls();}).setNegativeButton(AppLanguage.text(VideoActivity.this,"Chiudi"),null).show();
    }
    @Override public void tracks(){if(player==null)return;optionsDialog=dialog("Audio e sottotitoli").setItems(new String[]{AppLanguage.text(this,"Traccia audio"),AppLanguage.text(this,"Sottotitoli")},(d,index)->trackChoices(index==0?C.TRACK_TYPE_AUDIO:C.TRACK_TYPE_TEXT)).setNegativeButton(AppLanguage.text(VideoActivity.this,"Chiudi"),null).show();}
    private void trackChoices(int type){
        if(player==null)return;List<String> labels=new ArrayList<>();List<Tracks.Group> groups=new ArrayList<>();List<Integer> indices=new ArrayList<>();
        labels.add(AppLanguage.text(this,type==C.TRACK_TYPE_TEXT?"Disattivati":"Automatico"));int selected=0;
        for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==type)for(int i=0;i<group.length;i++)if(group.isTrackSupported(i)){
            Format format=group.getTrackFormat(i);String label=format.label;
            if(label==null||label.trim().isEmpty())label=format.language==null||"und".equals(format.language)?"Traccia "+(groups.size()+1):Locale.forLanguageTag(format.language).getDisplayLanguage(Locale.forLanguageTag(AppLanguage.selected(this)));
            if(label.isEmpty())label="Traccia "+(groups.size()+1);labels.add(label);groups.add(group);indices.add(i);if(group.isTrackSelected(i))selected=labels.size()-1;
        }
        if(groups.isEmpty()){toast(player.getPlaybackState()==Player.STATE_BUFFERING?"Attendi il caricamento del video.":type==C.TRACK_TYPE_TEXT?"Questo video non contiene sottotitoli disponibili.":"Questo video non contiene altre tracce audio.");return;}
        optionsDialog=dialog(type==C.TRACK_TYPE_TEXT?"Sottotitoli":"Traccia audio").setSingleChoiceItems(labels.toArray(new String[0]),selected,(d,index)->{
            TrackSelectionParameters.Builder parameters=player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type);
            if(index==0)parameters.setTrackTypeDisabled(type,type==C.TRACK_TYPE_TEXT);else parameters.setTrackTypeDisabled(type,false).setOverrideForType(new TrackSelectionOverride(groups.get(index-1).getMediaTrackGroup(),indices.get(index-1)));
            player.setTrackSelectionParameters(parameters.build());d.dismiss();controls.showControls();
        }).setNegativeButton(AppLanguage.text(VideoActivity.this,"Chiudi"),null).show();
    }
    @Override public void pip(){
        if(player==null||!getPackageManager().hasSystemFeature("android.software.picture_in_picture")){toast("Il mini player non è disponibile su questo dispositivo.");return;}
        try{VideoSize size=player.getVideoSize();float aspect=size.height>0?(float)size.width*size.pixelWidthHeightRatio/size.height:16f/9f;aspect=Math.max(.42f,Math.min(2.38f,aspect));
            if(!enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(Math.round(aspect*1000),1000)).build()))toast("Il mini player non è disponibile ora.");
        }catch(IllegalStateException|IllegalArgumentException|SecurityException error){toast("Il mini player non è disponibile ora.");}
    }
    @Override public void gallery(){
        if(!offline&&!local){toast("Scarica prima il video nell’app per salvarlo in galleria.");return;}
        GalleryExportService.State state=GalleryExportService.state();
        if(state.active){if(!key.equals(state.key)){toast("È già in corso il salvataggio di un altro video.");return;}
            optionsDialog=dialog("Salvataggio in corso").setMessage(AppLanguage.text(VideoActivity.this,"Vuoi annullare il salvataggio in galleria? Il download nell’app verrà conservato.")).setPositiveButton(AppLanguage.text(VideoActivity.this,"Annulla salvataggio"),(d,w)->GalleryExportService.cancel(this)).setNegativeButton(AppLanguage.text(VideoActivity.this,"Continua"),null).show();return;}
        optionsDialog=dialog("Salva in galleria").setMessage("Crea una copia del video in Filmati/VibraVid. Il download nell’app resta disponibile. Per i video HLS e DASH verrà creato un file MP4; l’operazione può richiedere alcuni minuti e spazio libero.")
            .setPositiveButton(AppLanguage.text(VideoActivity.this,"Salva video"),(d,w)->{
                if(Build.VERSION.SDK_INT<29&&checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){exportPermissionPending=true;requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},702);}else startExport();
            }).setNegativeButton(AppLanguage.text(VideoActivity.this,"Annulla"),null).show();
    }
    private void startExport(){try{GalleryExportService.start(this,offlineId,sourceUri,title);}catch(RuntimeException error){toast("Il salvataggio non è partito. Apri il lettore e riprova.");}}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);
        if(code==702&&exportPermissionPending){exportPermissionPending=false;if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startExport();else toast("Per salvare in galleria, consenti l’accesso ai file nelle impostazioni di Android.");}}
    private void galleryChanged(GalleryExportService.State state){
        if(controls==null||!key.equals(state.key))return;
        if(state.active)controls.exportStatus(state.message,state.progress);else{controls.exportStatus(sourceLabel(),-1);if(state.token!=lastExportNotice){lastExportNotice=state.token;controls.notice(state.message);if(state.uri==null)toast(state.message);}}
    }
    private void installGestures(View surface){
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        GestureDetector detector=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            float initialBrightness;int initialVolume;boolean vertical;
            @Override public boolean onDown(android.view.MotionEvent e){vertical=false;initialBrightness=getWindow().getAttributes().screenBrightness;
                if(initialBrightness<0)initialBrightness=Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,128)/255f;
                initialVolume=audio==null?0:audio.getStreamVolume(AudioManager.STREAM_MUSIC);return true;}
            @Override public boolean onSingleTapConfirmed(android.view.MotionEvent e){surface.performClick();return true;}
            @Override public boolean onDoubleTap(android.view.MotionEvent e){if(controls.locked()||isInPictureInPictureMode())return true;
                if(e.getX()<surface.getWidth()*.35f){jump(-10000);controls.notice("−10 secondi");}else if(e.getX()>surface.getWidth()*.65f){jump(10000);controls.notice("+10 secondi");}else play();return true;}
            @Override public boolean onScroll(android.view.MotionEvent start,android.view.MotionEvent end,float distanceX,float distanceY){
                if(start==null||controls.locked()||isInPictureInPictureMode())return false;float dy=start.getY()-end.getY(),dx=end.getX()-start.getX();
                if(!vertical){float threshold=18*getResources().getDisplayMetrics().density;if(Math.abs(dy)<threshold||Math.abs(dy)<Math.abs(dx))return false;vertical=true;}
                float delta=dy/Math.max(1,surface.getHeight())*1.5f;
                if(start.getX()<surface.getWidth()/2f){float brightness=Math.max(.02f,Math.min(1f,initialBrightness+delta));WindowManager.LayoutParams attributes=getWindow().getAttributes();attributes.screenBrightness=brightness;getWindow().setAttributes(attributes);controls.notice("Luminosità · "+Math.round(brightness*100)+"%");}
                else if(audio!=null)try{int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);int level=Math.max(0,Math.min(max,initialVolume+Math.round(delta*max)));audio.setStreamVolume(AudioManager.STREAM_MUSIC,level,0);controls.notice("Volume · "+(max>0?Math.round(level*100f/max):0)+"%");}catch(SecurityException error){controls.notice("Volume gestito dal dispositivo");}return true;
            }
        });surface.setOnTouchListener((v,event)->{detector.onTouchEvent(event);return true;});
    }
    private void remember(){if(player==null||key==null||player.getPlaybackState()==Player.STATE_IDLE)return;position=player.getPlaybackState()==Player.STATE_ENDED?0:player.getCurrentPosition();getSharedPreferences("playback",0).edit().putLong(key,position).apply();PlaybackStore.remember(this,key,titleIdentity,player.getCurrentPosition(),player.getDuration(),player.getPlaybackState()==Player.STATE_ENDED);}
    @Override protected void onSaveInstanceState(Bundle state){remember();state.putLong("position",position);state.putBoolean("playing",playing);state.putBoolean("fill",fill);state.putLong("exportNotice",lastExportNotice);state.putBoolean("exportPermissionPending",exportPermissionPending);if(player!=null)state.putFloat("speed",player.getPlaybackParameters().speed);super.onSaveInstanceState(state);}
    @Override public void onUserLeaveHint(){if(player!=null&&player.isPlaying()&&getPackageManager().hasSystemFeature("android.software.picture_in_picture"))pip();}
    @Override public void onPictureInPictureModeChanged(boolean inPip,android.content.res.Configuration config){super.onPictureInPictureModeChanged(inPip,config);if(controls!=null)controls.pip(inPip);if(!inPip)immersive();}
    private void pauseForLifecycle(){if(player==null)return;lifecyclePause=true;try{player.pause();}finally{lifecyclePause=false;}}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(tick);GalleryExportService.removeListener(galleryListener);if(controls!=null)controls.suspend();remember();if(!isInPictureInPictureMode())pauseForLifecycle();super.onPause();}
    @Override protected void onStop(){remember();pauseForLifecycle();super.onStop();}
    @Override protected void onResume(){super.onResume();resumed=true;if(player!=null&&playing&&!errorShown&&!isFinishing())player.play();if(controls!=null){controls.interacted();GalleryExportService.addListener(galleryListener);}handler.removeCallbacks(tick);handler.post(tick);}
    @Override protected void onDestroy(){resumed=false;handler.removeCallbacksAndMessages(null);GalleryExportService.removeListener(galleryListener);if(controls!=null)controls.suspend();if(optionsDialog!=null)optionsDialog.dismiss();if(errorDialog!=null){errorDialog.dismiss();errorDialog=null;}if(view!=null)view.setPlayer(null);if(player!=null){player.release();player=null;}super.onDestroy();}
}
