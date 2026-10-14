package it.vibravid.studio;
import android.content.*;
import org.json.*;
import java.util.*;
/** Same central feed as the original desktop application. No guessed domain suffixes. */
final class DomainRefresh {
    static final String FEED="https://domains-tracker.server66.workers.dev/get";
    private static final Object LOCK=new Object();
    static int refresh(Context context,boolean force){
        synchronized(LOCK){
            SharedPreferences prefs=context.getSharedPreferences("domain_refresh",0);long now=System.currentTimeMillis();
            long attempt=prefs.getLong("attempt",0),success=prefs.getLong("success",0);
            if(now>=attempt&&now-attempt<30000||!force&&now>=success&&success>0&&now-success<3600000)return 0;
            prefs.edit().putLong("attempt",now).apply();
            try{int changes=apply(context,new JSONObject(UpdateNetwork.read(FEED)));prefs.edit().putLong("success",now).apply();return changes;}
            catch(Exception error){android.util.Log.w("VibraVid","Elenco domini non disponibile; uso gli indirizzi salvati",error);return 0;}
        }
    }
    static int apply(Context context,JSONObject feed)throws Exception{
        synchronized(SourceCatalog.class){
            JSONArray rows=SourceCatalog.list(context);SharedPreferences saved=context.getSharedPreferences("sources",0),manual=context.getSharedPreferences("sources_manual",0);
            SharedPreferences.Editor edit=saved.edit();int changed=0;
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);String id=row.getString("id");JSONObject value=feed.optJSONObject(id);
                if(value==null||manual.getBoolean(id,false))continue;
                try{String url=SourceCatalog.https(value.getString("full_url")).toString();if(!url.equals(row.optString("url"))){edit.putString(id,url);changed++;}}
                catch(IllegalArgumentException|JSONException invalid){android.util.Log.w("VibraVid","Indirizzo non valido nell’elenco: "+id);}
            }
            if(changed>0&&!edit.commit())throw new java.io.IOException("Salvataggio domini non riuscito");return changed;
        }
    }
}
