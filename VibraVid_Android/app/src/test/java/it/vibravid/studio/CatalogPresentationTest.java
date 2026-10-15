package it.vibravid.studio;

import android.content.Context;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class CatalogPresentationTest {
    private JSONObject search(JSONObject title) throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        ProviderHttp.Transport fixture=(method,url,headers,body,call)->new ProviderHttp.Response(url,
            new JSONObject().put("props",new JSONObject().put("cdn_url","https://cdn.example.com")
                .put("titles",new JSONArray().put(title))).toString());
        try(ProviderEngine engine=new ProviderEngine(context,fixture)) {
            return engine.search("Fixture","streamingcommunity","all",new ProviderHttp.Call()).getJSONArray("items").getJSONObject(0);
        }
    }
    private JSONObject title() throws Exception {
        return new JSONObject().put("id",123).put("slug","fixture").put("name","Fixture")
            .put("type","movie").put("release_date","2024-01-01");
    }
    @Test public void landscapeArtworkAndSynopsisTravelWithSearchResult() throws Exception {
        JSONObject result=search(title().put("plot","<p>Una trama &amp; un titolo.</p><script>bad()</script>")
            .put("images",new JSONArray()
                .put(new JSONObject().put("type","poster").put("filename","poster.jpg"))
                .put(new JSONObject().put("type","background").put("filename","wide.jpg"))));
        assertEquals("https://cdn.example.com/images/poster.jpg",result.getString("poster"));
        assertEquals("https://cdn.example.com/images/wide.jpg",result.getString("backdrop"));
        assertEquals("Una trama & un titolo.",result.getString("description"));
        assertFalse(result.getString("key").isEmpty());
    }
    @Test public void missingOrStructuredMetadataCannotBreakSearch() throws Exception {
        JSONObject result=search(title().put("description",new JSONObject().put("unexpected",true)));
        assertEquals("",result.getString("backdrop"));assertEquals("",result.getString("description"));
        assertEquals("Fixture",result.getString("name"));
    }
}
