package it.vibravid.studio;

import android.content.Context;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Source addresses copied from the PC configuration; credentials are never copied. */
public final class SourceCatalog {
    public static JSONArray list(Context context) throws Exception {
        byte[] bytes;
        try(InputStream input=context.getAssets().open("providers.json");ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);bytes=output.toByteArray();
        }
        JSONArray rows=new JSONArray(new String(bytes,StandardCharsets.UTF_8));
        android.content.SharedPreferences overrides=context.getSharedPreferences("sources",Context.MODE_PRIVATE);
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String changed=overrides.getString(row.getString("id"),null);
            if(changed!=null)row.put("url",changed);
            row.put("automatic",ProviderEngine.supports(row.getString("id")));
        }
        return rows;
    }
    public static JSONObject find(Context context,String id)throws Exception{
        JSONArray rows=list(context);for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(id))return rows.getJSONObject(i);
        throw new IllegalArgumentException("Questa fonte non è nella tua configurazione.");
    }
    public static Uri https(String value){
        if(value==null||value.trim().isEmpty())throw new IllegalArgumentException("Inserisci un indirizzo HTTPS completo.");
        Uri uri=Uri.parse(value);
        try { if(new java.net.URI(value).getHost()==null||uri.getPort()>65535)throw new IllegalArgumentException(); }
        catch(Exception error){throw new IllegalArgumentException("Inserisci un indirizzo HTTPS valido.");}
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||value.length()>2500||value.matches(".*[\\r\\n\\s].*"))
            throw new IllegalArgumentException("Inserisci un indirizzo HTTPS completo, senza credenziali.");
        return uri.normalizeScheme();
    }
    public static void update(Context context,String id,String url)throws Exception{
        find(context,id);String valid=https(url==null?null:url.trim()).toString();
        if(!context.getSharedPreferences("sources",Context.MODE_PRIVATE).edit().putString(id,valid).commit())throw new IOException("Salvataggio non riuscito");
    }
    public static int importDomains(Context context,JSONObject input)throws Exception{
        Map<String,String> changes=new HashMap<>();JSONArray known=list(context);
        for(int i=0;i<known.length();i++){
            String id=known.getJSONObject(i).getString("id");JSONObject block=input.optJSONObject(id);
            if(block!=null&&block.has("full_url"))changes.put(id,https(block.getString("full_url")).toString());
        }
        if(changes.isEmpty())throw new IllegalArgumentException("Nel file non ci sono indirizzi delle fonti VibraVid.");
        android.content.SharedPreferences.Editor editor=context.getSharedPreferences("sources",Context.MODE_PRIVATE).edit();
        for(Map.Entry<String,String> entry:changes.entrySet())editor.putString(entry.getKey(),entry.getValue());
        if(!editor.commit())throw new IOException("Salvataggio non riuscito");return changes.size();
    }
}
