package it.vibravid.studio;
import android.content.Context;
import android.os.Looper;
import android.app.Activity;
import android.view.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class ExperienceTest {
    Context context=RuntimeEnvironment.getApplication();
    CinemaControls.Actions actions=new CinemaControls.Actions(){public void back(){}public void play(){}public void seek(long p){}public void jump(long p){}public void tracks(){}public void speed(){}public void fit(){}public void gallery(){}public void pip(){}};
    @Test public void pausedControlsDisappearAndPositionTicksDoNotRevealThem(){
        CinemaControls controls=new CinemaControls(context,"Film","VIDEO OFFLINE",true,actions);controls.showControls();controls.update(1000,90000,1000,false,false);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));assertFalse(controls.shown());
        controls.update(1000,90000,1000,false,false);assertFalse(controls.shown());controls.toggle();assertTrue(controls.shown());controls.suspend();
    }
    @Test public void playingControlsAutoHideAndLockAndPipStillWork(){
        CinemaControls controls=new CinemaControls(context,"Film","VIDEO OFFLINE",true,actions);controls.update(1000,90000,1000,true,false);controls.showControls();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));assertFalse(controls.shown());controls.setLocked(true);assertTrue(controls.locked());controls.toggle();assertFalse(controls.shown());controls.setLocked(false);assertTrue(controls.shown());controls.pip(true);assertFalse(controls.shown());controls.pip(false);assertTrue(controls.shown());controls.suspend();
    }
    @Test public void landscapeUsesCompactChromeWithoutResizingVideo(){
        CinemaControls controls=new CinemaControls(context,"Film","VIDEO OFFLINE",true,actions);float d=context.getResources().getDisplayMetrics().density;controls.layout(0,0,(int)(900*d),(int)(400*d));
        View play=controls.findViewWithTag("player-play");assertEquals(Math.round(64*d),play.getLayoutParams().height);
        controls.safeInsets(30,20,50,10);android.widget.FrameLayout.LayoutParams unlock=(android.widget.FrameLayout.LayoutParams)controls.findViewWithTag("player-unlock").getLayoutParams();assertEquals(Math.round(20*d)+50,unlock.rightMargin);assertEquals(Math.round(20*d)+30,unlock.leftMargin);
        controls.layout(0,0,(int)(390*d),(int)(844*d));assertEquals(Math.round(88*d),play.getLayoutParams().height);controls.suspend();
    }
    @Test public void immersiveWindowSupportsCutoutAndRestoresBars(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();PlayerWindow.immersive(activity.getWindow());
        assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES,activity.getWindow().getAttributes().layoutInDisplayCutoutMode);
        PlayerWindow.restore(activity.getWindow());assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT,activity.getWindow().getAttributes().layoutInDisplayCutoutMode);activity.finish();
    }
    @Test public void progressUsesRealDurationAndClampsAndTracksCompleted()throws Exception{
        PlaybackStore.remember(context,"offline-test","identity",5000,10000,false);assertEquals(5000,PlaybackStore.titles(context).optJSONObject("identity").optLong("position"));
        PlaybackStore.remember(context,"offline-test","identity",50000,10000,false);assertEquals(10000,PlaybackStore.video(context,"test").optLong("position"));
        PlaybackStore.remember(context,"offline-test","identity",0,10000,true);assertEquals(10000,PlaybackStore.video(context,"test").optLong("position"));
        PlaybackStore.remember(context,"offline-test","identity",0,-1,false);assertEquals(10000,PlaybackStore.video(context,"test").optLong("duration"));
    }
    @Test public void malformedProgressDoesNotHideOtherTitles()throws Exception{
        context.getSharedPreferences("playback",0).edit().putString("title:broken","not json").putLong("position-123",123).apply();PlaybackStore.remember(context,"offline-id","valid",3,100,false);assertTrue(PlaybackStore.titles(context).has("valid"));assertFalse(PlaybackStore.titles(context).has("broken"));
    }
    @Test public void moviesNeverUseAnAmbiguousOrWrongYearTrailer()throws Exception{
        JSONObject one=new JSONObject().put("trackName","Film").put("kind","feature-movie").put("releaseDate","2024-01-01");
        assertNull(TitleDetails.appleMatch(new JSONArray().put(one),"Film",2023));assertNull(TitleDetails.appleMatch(new JSONArray().put(one).put(one),"Film",2024));assertNotNull(TitleDetails.appleMatch(new JSONArray().put(one),"Film",2024));
    }
    @Test public void appleTrailerIsValidatedAndRuntimeAndDirectorRemainSeparateFromCast()throws Exception{
        JSONObject result=TitleDetails.apple(new JSONObject().put("previewUrl","javascript:alert(1)").put("artistName","Regista").put("trackTimeMillis",7200000).put("longDescription","<p>Trama</p><script>bad()</script>"));
        assertEquals("",result.getString("preview"));assertEquals(120,result.getInt("duration"));assertEquals("Regista",result.getString("director"));assertFalse(result.has("cast"));assertEquals("Trama",result.getString("description"));
    }
    @Test public void seriesRetainScoreCastAndGroupedEpisodes()throws Exception{
        JSONObject show=new JSONObject().put("id",12).put("rating",new JSONObject().put("average",8.5)).put("genres",new JSONArray().put("Drama"));
        JSONObject cast=new JSONObject().put("person",new JSONObject().put("name","Attrice").put("image",new JSONObject().put("medium","https://example.com/a.jpg"))).put("character",new JSONObject().put("name","Lucy"));
        show.put("_embedded",new JSONObject().put("cast",new JSONArray().put(cast)).put("episodes",new JSONArray().put(new JSONObject().put("season",2).put("number",1).put("name","Episodio").put("summary","<p>Trama</p>"))));
        JSONObject result=TitleDetails.tvmaze(show);assertEquals(8.5,result.getDouble("catalog_rating"),0);assertEquals("Lucy",result.getJSONArray("cast").getJSONObject(0).getString("role"));assertEquals(2,result.getJSONArray("seasons").getJSONObject(0).getInt("number"));assertFalse(result.has("preview"));
    }
    @Test public void detailCacheAvoidsRepeatedRequestsAndSurvivesNetworkFailure()throws Exception{
        JSONObject row=new JSONObject().put("name","Unique QA series cache").put("kind","series").put("catalog_id","tvmaze:987654");AtomicInteger count=new AtomicInteger();
        JSONObject first=TitleDetails.load(context,row,url->{count.incrementAndGet();return "{\"id\":987654,\"summary\":\"<p>Trama</p>\"}";});
        JSONObject second=TitleDetails.load(context,row,url->{throw new java.io.IOException("offline");});assertEquals(1,count.get());assertEquals(first.toString(),second.toString());
    }
    @Test public void rssTrailerIsRetainedAndUnsafeTrailerIsRejected()throws Exception{
        JSONObject entry=new JSONObject().put("im:name",new JSONObject().put("label","Film")).put("id",new JSONObject().put("label","https://itunes.apple.com/it/movie/id123").put("attributes",new JSONObject().put("im:id","123"))).put("im:contentType",new JSONObject().put("attributes",new JSONObject().put("term","Movie")));
        entry.put("link",new JSONArray().put(new JSONObject().put("attributes",new JSONObject().put("type","video/x-m4v").put("href","https://example.com/trailer.m4v"))));
        String body=new JSONObject().put("feed",new JSONObject().put("entry",new JSONArray().put(entry))).toString();assertEquals("https://example.com/trailer.m4v",DiscoveryCatalog.movies(body).getJSONObject(0).getString("preview"));
        assertEquals("apple:123",DiscoveryCatalog.movies(body).getJSONObject(0).getString("catalog_id"));
    }
    private JSONObject claim(String id)throws Exception{return new JSONObject().put("mainsnak",new JSONObject().put("datavalue",new JSONObject().put("value",new JSONObject().put("id",id))));}
    @Test public void featureFilmCastIsRecognizedAndRemainsSeparateFromDirector()throws Exception{
        String name="Unique feature cast QA";
        JSONObject claims=new JSONObject().put("P31",new JSONArray().put(claim("Q24869"))).put("P161",new JSONArray().put(claim("Q34")));
        claims.put("P577",new JSONArray().put(new JSONObject().put("mainsnak",new JSONObject().put("datavalue",new JSONObject().put("value",new JSONObject().put("time","+2024-01-01T00:00:00Z"))))));
        JSONObject result=TitleDetails.load(context,new JSONObject().put("name",name).put("kind","movie").put("year",2024),url->{
            if(url.contains("itunes.apple.com"))return new JSONObject().put("results",new JSONArray().put(new JSONObject().put("trackName",name).put("kind","feature-movie").put("releaseDate","2024-01-01").put("artistName","Regista"))).toString();
            if(url.contains("wbsearchentities"))return new JSONObject().put("search",new JSONArray().put(new JSONObject().put("id","Q12").put("label",name))).toString();
            if(url.contains("props=claims"))return new JSONObject().put("entities",new JSONObject().put("Q12",new JSONObject().put("claims",claims))).toString();
            return new JSONObject().put("entities",new JSONObject().put("Q34",new JSONObject().put("labels",new JSONObject().put("it",new JSONObject().put("value","Attrice"))))).toString();
        });
        assertEquals("Regista",result.getString("director"));assertEquals("Attrice",result.getJSONArray("cast").getJSONObject(0).getString("name"));
    }

}
