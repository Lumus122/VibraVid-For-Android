package it.vibravid.studio;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.Map;

final class PlaybackStore {
    static void remember(Context context,String key,String identity,long position,long duration,boolean ended){
        if(key==null||duration<=0)return;
        try{JSONObject value=new JSONObject().put("position",ended?duration:Math.max(0,Math.min(position,duration))).put("duration",duration).put("updated_at",System.currentTimeMillis());
            SharedPreferences.Editor edit=context.getSharedPreferences("playback",0).edit().putString("progress:"+key,value.toString());
            if(identity!=null&&!identity.isEmpty()&&identity.length()<=1000)edit.putString("title:"+identity,value.toString());edit.apply();
        }catch(JSONException ignored){}
    }
    static JSONObject titles(Context context)throws JSONException{
        JSONObject rows=new JSONObject();int count=0;
        for(Map.Entry<String,?> entry:context.getSharedPreferences("playback",0).getAll().entrySet())if(entry.getKey().startsWith("title:")&&entry.getValue() instanceof String&&count<2000)try{rows.put(entry.getKey().substring(6),new JSONObject((String)entry.getValue()));count++;}catch(JSONException ignored){}
        return rows;
    }
    static JSONObject video(Context context,String id){try{return new JSONObject(context.getSharedPreferences("playback",0).getString("progress:offline-"+id,"{}"));}catch(JSONException ignored){return new JSONObject();}}
}
