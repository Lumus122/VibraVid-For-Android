package it.vibravid.studio;

import android.content.Context;
import android.view.View;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class LanguageTest {
    Context context;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();AppLanguage.set(context,"it");}
    @Test public void defaultsToItalianAndRejectsUnknownCodes(){
        assertEquals("it",AppLanguage.selected(context));AppLanguage.set(context,"../../bad");assertEquals("it",AppLanguage.selected(context));
        assertEquals("Salva video",AppLanguage.text(context,"Salva video"));assertEquals("",AppLanguage.text(context,null));
    }
    @Test public void englishUiAndPersistentPreference(){
        AppLanguage.set(context,"en");assertEquals("en",AppLanguage.selected(context));
        assertEquals("Save video",AppLanguage.text(context,"Salva video"));assertEquals("Gallery",AppLanguage.text(context,"Galleria"));
        assertEquals("en",AppLanguage.context(context).getResources().getConfiguration().getLocales().get(0).getLanguage());
    }
    @Test public void arabicContextHasRightToLeftDirection(){
        AppLanguage.set(context,"ar");assertEquals(View.LAYOUT_DIRECTION_RTL,AppLanguage.context(context).getResources().getConfiguration().getLayoutDirection());
        assertNotEquals("Salva video",AppLanguage.text(context,"Salva video"));
    }
    @Test public void everyCatalogHasAllMessages()throws Exception{
        JSONArray languages=new JSONArray(read("locales/catalog.json"));assertEquals(21,languages.length());
        JSONObject source=new JSONObject(read("locales/it.json"));
        for(int i=0;i<languages.length();i++){
            String code=languages.getJSONObject(i).getString("code");assertEquals(code,AppLanguage.normalize(code));
            JSONObject catalog=new JSONObject(read("locales/"+code+".json"));assertEquals(source.length(),catalog.length());
            Iterator<String> keys=source.keys();while(keys.hasNext()){String key=keys.next();assertFalse(code+":"+key,catalog.getString(key).trim().isEmpty());}
        }
    }
    @Test public void nativePlayerTranslatesCommandsAndPreservesMovieTitle(){
        AppLanguage.set(context,"en");Context localized=AppLanguage.context(context);
        CinemaControls controls=new CinemaControls(localized,"Salva video","VIDEO OFFLINE",true,new CinemaControls.Actions(){
            public void back(){}public void play(){}public void seek(long value){}public void jump(long value){}public void tracks(){}public void speed(){}public void fit(){}public void gallery(){}public void pip(){}
        });
        android.view.View gallery=controls.findViewWithTag("player-gallery");assertEquals("Save",gallery.getContentDescription().toString());
        android.view.View back=controls.findViewWithTag("player-back");assertEquals("Back to library",back.getContentDescription().toString());
        assertTrue(containsText(controls,"Salva video"));assertTrue(containsText(controls,"OFFLINE VIDEO"));
        controls.suspend();
    }
    private boolean containsText(android.view.View view,String text){
        if(view instanceof android.widget.TextView&&text.equals(((android.widget.TextView)view).getText().toString()))return true;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(containsText(group.getChildAt(i),text))return true;}return false;
    }
    private String read(String name)throws Exception{
        try(java.io.InputStream stream=context.getAssets().open(name);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] bytes=new byte[8192];int count;while((count=stream.read(bytes))!=-1)out.write(bytes,0,count);return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
}
