package it.vibravid.studio;
import android.content.Context;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class UpdateTest {
 Context context;
 @Before public void setup(){context=RuntimeEnvironment.getApplication();context.getSharedPreferences("sources",0).edit().clear().commit();context.getSharedPreferences("sources_manual",0).edit().clear().commit();}
 @Test public void numericVersionsHandleTwoDigitMinor(){assertTrue(AppUpdater.compare("v2.10.0-android","2.9.0-android")>0);assertEquals(0,AppUpdater.compare("v2.8.0-android","2.8.0-android"));}
 private JSONObject release(String version,String url)throws Exception{return new JSONObject().put("tag_name",version).put("assets",new JSONArray().put(new JSONObject().put("name","VibraVid_Studio_Android.apk").put("size",7000000).put("browser_download_url",url)));}
 @Test public void selectsNewestStableOwnedApk()throws Exception{JSONObject old=release("v2.9.0-android",AppUpdater.REPO+"/releases/download/v2.9.0-android/app.apk"),newer=release("v2.10.0-android",AppUpdater.REPO+"/releases/download/v2.10.0-android/app.apk");assertEquals("v2.10.0-android",AppUpdater.select(new JSONArray().put(old).put(newer),"2.8.0-android").getString("version"));}
 @Test public void rejectsPreviewForeignAndOlderReleases()throws Exception{
  JSONObject preview=release("v9.0.0-android",AppUpdater.REPO+"/releases/download/v9/app.apk").put("prerelease",true);
  JSONObject foreign=release("v9.0.0-android","https://github.com/other/evil/releases/download/v9/app.apk");
  assertNull(AppUpdater.select(new JSONArray().put(preview).put(foreign).put(release("v2.5.1-android",AppUpdater.REPO+"/releases/download/v2.5.1/app.apk")),"2.8.0-android"));
 }
 @Test public void centralFeedUpdatesKnownDomainAndRetainsOtherSources()throws Exception{
  String other=SourceCatalog.find(context,"animeunity").getString("url");
  int updated=DomainRefresh.apply(context,new JSONObject().put("streamingcommunity",new JSONObject().put("full_url","https://streamingcommunityz.pizza/")));
  assertEquals(1,updated);assertEquals("https://streamingcommunityz.pizza/",SourceCatalog.find(context,"streamingcommunity").getString("url"));assertEquals(other,SourceCatalog.find(context,"animeunity").getString("url"));
 }
 @Test public void invalidAndUnknownFeedEntriesCannotOverwriteSources()throws Exception{
  String before=SourceCatalog.find(context,"streamingcommunity").getString("url");
  assertEquals(0,DomainRefresh.apply(context,new JSONObject().put("streamingcommunity",new JSONObject().put("full_url","http://evil.example/")).put("unknown",new JSONObject().put("full_url","https://unknown.example/"))));assertEquals(before,SourceCatalog.find(context,"streamingcommunity").getString("url"));
 }
 @Test public void explicitlyEditedAddressesArePreserved()throws Exception{
  SourceCatalog.update(context,"streamingcommunity","https://my-source.example/");DomainRefresh.apply(context,new JSONObject().put("streamingcommunity",new JSONObject().put("full_url","https://streamingcommunityz.pizza/")));assertEquals("https://my-source.example/",SourceCatalog.find(context,"streamingcommunity").getString("url"));
 }
}
