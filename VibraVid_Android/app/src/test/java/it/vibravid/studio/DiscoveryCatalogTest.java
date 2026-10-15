package it.vibravid.studio;
import android.content.Context;
import java.io.File;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class DiscoveryCatalogTest {
    Context context;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();new File(context.getCacheDir(),"browse-catalog.json").delete();new File(context.getCacheDir(),"browse-catalog.json.bak").delete();}
    private String movieFeed() throws Exception {
        return new JSONObject().put("feed",new JSONObject().put("entry",new JSONArray().put(new JSONObject()
            .put("im:name",new JSONObject().put("label","Film &amp; prova"))
            .put("im:contentType",new JSONObject().put("attributes",new JSONObject().put("term","Movie")))
            .put("im:releaseDate",new JSONObject().put("label","2024-01-01"))
            .put("summary",new JSONObject().put("label","<p>Una trama</p><script>bad()</script>"))
            .put("id",new JSONObject().put("label","https://itunes.apple.com/it/movie/test/id123"))
            .put("im:image",new JSONArray().put(new JSONObject().put("label","https://is1-ssl.mzstatic.com/art/113x170bb.png")))))).toString();
    }
    private String seriesFeed() throws Exception {
        return new JSONArray().put(new JSONObject().put("name","Serie").put("url","https://www.tvmaze.com/shows/1/test")
            .put("premiered","2020-01-01").put("runtime",45).put("rating",new JSONObject().put("average",8))
            .put("image",new JSONObject().put("medium","https://static.tvmaze.com/test.jpg").put("original","https://static.tvmaze.com/test.jpg"))
            .put("summary","<p>Trama serie</p>")).toString();
    }
    @Test public void movieMetadataRetainsAttributionAndDoesNotBecomePlayable() throws Exception {
        JSONObject row=DiscoveryCatalog.movies(movieFeed()).getJSONObject(0);
        assertEquals("Film & prova",row.getString("name"));assertEquals("movie",row.getString("kind"));
        assertEquals("Una trama",row.getString("description"));assertEquals("",row.getString("preview"));
        assertTrue(row.getString("poster").endsWith("/400x600bb.jpg"));assertEquals("Apple Movies",row.getJSONArray("sources").getString(0));
    }
    @Test public void seriesWithUnsafeArtworkCannotEnterTheShelf() throws Exception {
        JSONArray entries=new JSONArray(seriesFeed());entries.put(new JSONObject(entries.getJSONObject(0).toString())
            .put("name","Unsafe").put("image",new JSONObject().put("medium","javascript:bad()")));
        JSONArray result=DiscoveryCatalog.series(entries.toString());assertEquals(1,result.length());assertEquals("series",result.getJSONObject(0).getString("kind"));assertEquals(45,result.getJSONObject(0).getInt("duration"));
    }
    @Test public void successfulCatalogIsCachedWithoutRepeatedRequests() throws Exception {
        AtomicInteger count=new AtomicInteger();DiscoveryCatalog.Fetcher fetch=url->{count.incrementAndGet();return url.equals(DiscoveryCatalog.MOVIES)?movieFeed():seriesFeed();};
        JSONObject first=DiscoveryCatalog.load(context,fetch),second=DiscoveryCatalog.load(context,fetch);
        assertEquals(2,count.get());assertFalse(first.getBoolean("cached"));assertEquals(first.toString(),second.toString());
    }
    @Test public void timeoutRetainsBundledCatalogAndSpacesRetries() throws Exception {
        AtomicInteger count=new AtomicInteger();DiscoveryCatalog.Fetcher offline=url->{count.incrementAndGet();throw new SocketTimeoutException();};
        JSONObject first=DiscoveryCatalog.load(context,offline);assertTrue(first.getBoolean("cached"));assertTrue(first.getJSONArray("movies").length()>0);assertTrue(first.getJSONArray("series").length()>0);
        DiscoveryCatalog.load(context,offline);assertEquals(2,count.get());
    }
}
