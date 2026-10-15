package it.vibravid.studio;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import org.jsoup.Jsoup;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Browsing metadata only. Playback/download availability is resolved by the existing source engine. */
final class DiscoveryCatalog {
    static final String MOVIES="https://itunes.apple.com/it/rss/topmovies/limit=100/json";
    static final String SERIES="https://api.tvmaze.com/shows?page=0";
    private static final long TTL=6*60*60*1000L;
    interface Fetcher { String fetch(String url) throws Exception; }
    private static JSONObject object(JSONObject row,String key){JSONObject value=row.optJSONObject(key);return value==null?new JSONObject():value;}
    private static String label(JSONObject row,String key){return object(row,key).optString("label");}
    private static String text(String value,int max){String plain=Jsoup.parse(value).text();return plain.length()>max?plain.substring(0,max):plain;}
    private static String https(String value){try{return SourceCatalog.https(value).toString();}catch(Exception e){return "";}}
    private static Object year(String value){try{int y=Integer.parseInt(value.substring(0,4));return y>=1800&&y<=2200?y:JSONObject.NULL;}catch(Exception e){return JSONObject.NULL;}}
    static JSONArray movies(String body) throws Exception {
        JSONObject feed=object(new JSONObject(body),"feed");JSONArray entries=feed.optJSONArray("entry");
        if(entries==null&&feed.optJSONObject("entry")!=null)entries=new JSONArray().put(feed.getJSONObject("entry"));
        if(entries==null)throw new IOException("Catalogo film non leggibile");
        JSONArray items=new JSONArray();Set<String> seen=new HashSet<>();
        for(int i=0;i<entries.length()&&items.length()<100;i++)try{
            JSONObject entry=entries.getJSONObject(i);String name=text(label(entry,"im:name"),300),url=https(label(entry,"id"));
            if(name.isEmpty()||url.isEmpty()||!"Movie".equals(object(object(entry,"im:contentType"),"attributes").optString("term"))||!seen.add(url))continue;
            JSONArray art=entry.optJSONArray("im:image");String poster="";
            if(art!=null&&art.length()>0)poster=https(art.getJSONObject(art.length()-1).optString("label"));
            // Keep the same artwork asset and request a larger portrait rendition.
            poster=poster.replaceFirst("/[0-9]+x[0-9]+bb\\.(png|jpg)$","/400x600bb.jpg");
            String trailer="";JSONArray links=entry.optJSONArray("link");if(links==null&&entry.optJSONObject("link")!=null)links=new JSONArray().put(entry.getJSONObject("link"));
            if(links!=null)for(int j=0;j<links.length();j++){JSONObject link=links.optJSONObject(j);if(link==null)continue;JSONObject attrs=object(link,"attributes");if(attrs.optString("type").startsWith("video/"))trailer=https(attrs.optString("href"));}
            String appleId=object(object(entry,"id"),"attributes").optString("im:id");
            JSONArray tags=new JSONArray();String genre=object(object(entry,"category"),"attributes").optString("label");if(!genre.isEmpty())tags.put(text(genre,32));
            items.put(new JSONObject().put("name",name).put("kind","movie").put("year",year(label(entry,"im:releaseDate")))
                .put("poster",poster).put("description",text(label(entry,"summary"),6000)).put("duration",JSONObject.NULL)
                .put("sources",new JSONArray().put("Apple Movies")).put("url",url).put("preview",trailer).put("catalog_id",appleId.matches("[0-9]+")?"apple:"+appleId:"").put("tags",tags)
                .put("image_source",url).put("image_credit",text(label(entry,"rights"),1000)));
        }catch(JSONException ignored){}
        if(items.length()==0)throw new IOException("Catalogo film vuoto");return items;
    }
    static JSONArray series(String body) throws Exception {
        JSONArray entries=new JSONArray(body);List<JSONObject> rows=new ArrayList<>();
        for(int i=0;i<entries.length();i++){JSONObject row=entries.optJSONObject(i);if(row!=null&&!https(object(row,"image").optString("medium")).isEmpty()&&!row.optString("name").trim().isEmpty())rows.add(row);}
        rows.sort((a,b)->Double.compare(object(b,"rating").optDouble("average",0),object(a,"rating").optDouble("average",0)));
        JSONArray items=new JSONArray();Set<String> seen=new HashSet<>();
        for(JSONObject row:rows){if(items.length()>=100)break;String url=https(row.optString("url"));if(url.isEmpty()||!seen.add(url))continue;
            int runtime=row.optInt("averageRuntime",row.optInt("runtime"));JSONArray tags=new JSONArray(),genres=row.optJSONArray("genres");
            if(genres!=null)for(int j=0;j<genres.length()&&j<8;j++)tags.put(text(genres.optString(j),32));
            items.put(new JSONObject().put("name",text(row.optString("name"),300)).put("kind","series").put("year",year(row.optString("premiered")))
                .put("poster",https(object(row,"image").optString("original",object(row,"image").optString("medium"))))
                .put("description",text(row.optString("summary"),6000)).put("duration",runtime>0&&runtime<=600?runtime:JSONObject.NULL)
                .put("sources",new JSONArray().put("TVmaze")).put("url",url).put("preview","").put("catalog_id","tvmaze:"+row.optLong("id")).put("catalog_rating",object(row,"rating").optDouble("average",0)).put("tags",tags).put("image_source",url).put("image_credit","TVmaze"));
        }
        if(items.length()==0)throw new IOException("Catalogo serie vuoto");return items;
    }
    private static String read(InputStream input) throws Exception {
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>1024*1024)throw new IOException("Catalogo troppo grande");out.write(buffer,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    static synchronized JSONObject load(Context context,Fetcher fetcher) throws Exception {
        AtomicFile store=new AtomicFile(new File(context.getCacheDir(),"browse-catalog.json"));JSONObject saved;
        try{saved=new JSONObject(read(store.openRead()));if(saved.getJSONArray("movies").length()==0||saved.getJSONArray("series").length()==0)throw new IOException();}
        catch(Exception error){saved=new JSONObject(read(context.getAssets().open("discovery.json")));}
        long now=System.currentTimeMillis(),age=now-saved.optLong("refreshed_at",0),attemptAge=now-saved.optLong("attempted_at",0);
        if((saved.optInt("catalog_version")==2&&age>=0&&age<TTL)||(attemptAge>=0&&attemptAge<60000))return saved;
        JSONObject next=new JSONObject(saved.toString());int updated=0;
        try{next.put("movies",movies(fetcher.fetch(MOVIES)));updated++;}catch(Exception error){if(Thread.currentThread().isInterrupted())throw error;}
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
        try{next.put("series",series(fetcher.fetch(SERIES)));updated++;}catch(Exception error){if(Thread.currentThread().isInterrupted())throw error;}
        next.put("catalog_version",2).put("attempted_at",now).put("cached",updated==0);if(updated==2)next.put("refreshed_at",now);
        FileOutputStream out=null;try{out=store.startWrite();out.write(next.toString().getBytes(StandardCharsets.UTF_8));store.finishWrite(out);}catch(Exception e){if(out!=null)store.failWrite(out);}
        return next;
    }
}
