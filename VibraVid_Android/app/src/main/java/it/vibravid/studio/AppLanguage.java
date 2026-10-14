package it.vibravid.studio;

import android.content.Context;
import android.content.res.Configuration;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Static local catalogs translate UI copy; user titles, notes, paths and source content are preserved. */
final class AppLanguage {
    private static final Set<String> SUPPORTED=new HashSet<>(Arrays.asList("it","en","es","pt","fr","de","nl","pl","ro","uk","ru","tr","ar","hi","bn","zh","ja","ko","id","vi","th"));
    private static final Map<String,JSONObject> catalogs=new ConcurrentHashMap<>();
    private static volatile List<String> fragments;
    static String normalize(String language){return SUPPORTED.contains(language)?language:"it";}
    static String selected(Context context){try{return normalize(context.getSharedPreferences("language",0).getString("selected","it"));}catch(RuntimeException error){return "it";}}
    static void set(Context context,String language){context.getSharedPreferences("language",0).edit().putString("selected",normalize(language)).apply();}
    static Context context(Context base){Configuration configuration=new Configuration(base.getResources().getConfiguration());Locale locale=Locale.forLanguageTag(selected(base));configuration.setLocale(locale);configuration.setLayoutDirection(locale);return base.createConfigurationContext(configuration);}
    private static JSONObject catalog(Context context,String code){
        JSONObject existing=catalogs.get(code);if(existing!=null)return existing;
        try(InputStream input=context.getAssets().open("locales/"+code+".json")){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[8192];int count;while((count=input.read(bytes))!=-1){out.write(bytes,0,count);if(out.size()>1024*1024)throw new IOException("Catalog too large");}
            JSONObject value=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));catalogs.put(code,value);return value;
        }catch(Exception error){android.util.Log.w("VibraVid","Catalogo lingua non disponibile: "+code);return new JSONObject();}
    }
    static String text(Context context,String value){
        if(value==null)return "";String language=selected(context);if(language.equals("it"))return value;
        String key=value.trim();JSONObject dictionary=catalog(context,language),english=catalog(context,"en");
        String result=dictionary.optString(key,english.optString(key,""));
        if(!result.isEmpty()){int start=value.indexOf(key);return value.substring(0,start)+result+value.substring(start+key.length());}
        List<String> parts=fragments;
        if(parts==null){List<String> loaded=new ArrayList<>();Iterator<String> names=english.keys();while(names.hasNext()){String part=names.next();if(part.length()>=4)loaded.add(part);}loaded.sort((a,b)->Integer.compare(b.length(),a.length()));fragments=parts=Collections.unmodifiableList(loaded);}
        for(String part:parts){String translated=dictionary.optString(part,english.optString(part,part));
            if(key.startsWith(part)&&key.length()>part.length()&&Character.isWhitespace(key.charAt(part.length()))){int start=value.indexOf(part);return value.substring(0,start)+translated+value.substring(start+part.length());}
            if(key.endsWith(part)&&key.length()>part.length()&&(Character.isWhitespace(key.charAt(key.length()-part.length()-1))||Character.isDigit(key.charAt(key.length()-part.length()-1)))){int start=value.lastIndexOf(part);return value.substring(0,start)+translated+value.substring(start+part.length());}
        }return value;
    }
}
