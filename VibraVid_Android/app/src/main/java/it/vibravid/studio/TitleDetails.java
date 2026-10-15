package it.vibravid.studio;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import org.jsoup.Jsoup;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;

/** Public catalogue metadata. A trailer is a provider preview, never the feature URL. */
final class TitleDetails {
    interface Fetcher {String fetch(String url)throws Exception;}
    static String normalized(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
    private static String safe(String value){try{return SourceCatalog.https(value).toString();}catch(Exception error){return "";}}
    private static String plain(String value,int max){String text=Jsoup.parse(value).text();return text.substring(0,Math.min(max,text.length()));}
    private static JSONObject obj(JSONObject value,String key){JSONObject child=value.optJSONObject(key);return child==null?new JSONObject():child;}
    private static JSONArray array(JSONObject value,String key){JSONArray child=value.optJSONArray(key);return child==null?new JSONArray():child;}
    private static int year(String value){try{return Integer.parseInt(value.substring(0,4));}catch(Exception e){return 0;}}
    static JSONObject appleMatch(JSONArray rows,String name,int releaseYear){
        JSONObject found=null;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row==null||!normalized(row.optString("trackName")).equals(normalized(name)))continue;
            if(releaseYear>0&&year(row.optString("releaseDate"))!=releaseYear)continue;
            if(!"feature-movie".equals(row.optString("kind")))continue;
            if(found!=null)return null;found=row;
        }return found;
    }
    static JSONObject apple(JSONObject row)throws Exception{
        JSONObject result=new JSONObject().put("description",plain(row.optString("longDescription",row.optString("shortDescription")),6000))
            .put("preview",safe(row.optString("previewUrl"))).put("director",plain(row.optString("artistName"),300))
            .put("catalog_id","apple:"+row.optLong("trackId")).put("metadata_source","Apple Movies");
        int duration=(int)Math.round(row.optDouble("trackTimeMillis",0)/60000);if(duration>0&&duration<=600)result.put("duration",duration);
        String genre=plain(row.optString("primaryGenreName"),32);if(!genre.isEmpty())result.put("tags",new JSONArray().put(genre));
        return result;
    }
    static JSONObject tvmaze(JSONObject row)throws Exception{
        JSONObject result=new JSONObject().put("description",plain(row.optString("summary"),6000)).put("catalog_id","tvmaze:"+row.optLong("id")).put("metadata_source","TVmaze · CC BY-SA");
        int duration=row.optInt("averageRuntime",row.optInt("runtime"));if(duration>0&&duration<=600)result.put("duration",duration);
        double score=obj(row,"rating").optDouble("average",0);if(Double.isFinite(score)&&score>0&&score<=10)result.put("catalog_rating",score);
        JSONArray tags=new JSONArray();JSONArray genres=array(row,"genres");for(int i=0;i<genres.length()&&i<8;i++)tags.put(plain(genres.optString(i),32));result.put("tags",tags);
        JSONObject embedded=obj(row,"_embedded");JSONArray cast=new JSONArray(),actors=array(embedded,"cast");
        for(int i=0;i<actors.length()&&cast.length()<24;i++){JSONObject actor=actors.optJSONObject(i);if(actor==null)continue;JSONObject person=obj(actor,"person");String name=plain(person.optString("name"),200);if(!name.isEmpty())cast.put(new JSONObject().put("name",name).put("role",plain(obj(actor,"character").optString("name"),200)).put("image",safe(obj(person,"image").optString("medium"))));}
        result.put("cast",cast);
        JSONArray episodes=array(embedded,"episodes"),seasons=new JSONArray();Map<Integer,JSONArray> groups=new TreeMap<>();
        for(int i=0;i<episodes.length()&&i<1500;i++){JSONObject ep=episodes.optJSONObject(i);if(ep==null)continue;int season=ep.optInt("season"),number=ep.optInt("number");if(season<0||season>100||number<0)continue;
            JSONArray list=groups.get(season);if(list==null){list=new JSONArray();groups.put(season,list);}
            list.put(new JSONObject().put("number",number).put("name",plain(ep.optString("name"),300)).put("description",plain(ep.optString("summary"),1500)).put("duration",Math.max(0,ep.optInt("runtime"))).put("airdate",ep.optString("airdate")));
        }
        for(Map.Entry<Integer,JSONArray> group:groups.entrySet())seasons.put(new JSONObject().put("number",group.getKey()).put("episodes",group.getValue()));
        result.put("seasons",seasons);return result;
    }
    private static JSONObject movie(JSONObject row,Fetcher fetcher)throws Exception{
        String id=row.optString("catalog_id"),url;
        if(id.matches("apple:[0-9]+"))url="https://itunes.apple.com/lookup?id="+id.substring(6)+"&country=it";
        else url="https://itunes.apple.com/search?term="+URLEncoder.encode(row.getString("name"),"UTF-8")+"&country=it&media=movie&entity=movie&limit=12";
        JSONObject candidate=appleMatch(array(new JSONObject(fetcher.fetch(url)),"results"),row.getString("name"),row.optInt("year"));
        if(candidate==null)return new JSONObject();JSONObject result=apple(candidate);
        // Cast and public score are separate from the owner's personal library rating.
        try{JSONArray cast=wikidataCast(row,fetcher);if(cast.length()>0)result.put("cast",cast).put("cast_source","Wikidata · CC0");}catch(Exception e){if(Thread.currentThread().isInterrupted())throw e;}
        return result;
    }
    private static JSONArray wikidataCast(JSONObject row,Fetcher fetcher)throws Exception{
        String name=row.getString("name"),base="https://www.wikidata.org/w/api.php?format=json&";
        JSONArray search=array(new JSONObject(fetcher.fetch(base+"action=wbsearchentities&language=it&uselang=it&limit=5&type=item&search="+URLEncoder.encode(name,"UTF-8"))),"search");
        List<String> ids=new ArrayList<>();for(int i=0;i<search.length();i++){JSONObject hit=search.optJSONObject(i);if(hit!=null&&normalized(hit.optString("label")).equals(normalized(name))&&hit.optString("id").matches("Q[0-9]+"))ids.add(hit.getString("id"));}
        if(ids.isEmpty())return new JSONArray();JSONObject entities=obj(new JSONObject(fetcher.fetch(base+"action=wbgetentities&props=claims&ids="+String.join("%7C",ids))),"entities");List<String> actors=new ArrayList<>();int matches=0;
        for(String id:ids){JSONObject claims=obj(obj(entities,id),"claims");boolean film=false;JSONArray classes=array(claims,"P31");for(int j=0;j<classes.length();j++){String type=obj(obj(obj(classes.getJSONObject(j),"mainsnak"),"datavalue"),"value").optString("id");if(Arrays.asList("Q11424","Q202866","Q24869","Q24862","Q506240","Q226730").contains(type))film=true;}if(!film)continue;
            boolean sameYear=row.optInt("year")==0;JSONArray dates=array(claims,"P577");for(int j=0;j<dates.length();j++){String date=obj(obj(obj(dates.getJSONObject(j),"mainsnak"),"datavalue"),"value").optString("time");if(date.length()>5&&year(date.substring(1))==row.optInt("year"))sameYear=true;}if(!sameYear)continue;matches++;
            JSONArray cast=array(claims,"P161");for(int j=0;j<cast.length()&&actors.size()<24;j++){String actor=obj(obj(obj(cast.getJSONObject(j),"mainsnak"),"datavalue"),"value").optString("id");if(actor.matches("Q[0-9]+")&&!actors.contains(actor))actors.add(actor);}
        }
        if(matches!=1||actors.isEmpty())return new JSONArray();JSONObject people=obj(new JSONObject(fetcher.fetch(base+"action=wbgetentities&props=labels&languages=it%7Cen&ids="+String.join("%7C",actors))),"entities");JSONArray result=new JSONArray();
        for(String id:actors){JSONObject labels=obj(obj(people,id),"labels");String label=obj(labels,"it").optString("value",obj(labels,"en").optString("value"));if(!label.isEmpty())result.put(new JSONObject().put("name",plain(label,200)).put("role","").put("image",""));}return result;
    }
    private static JSONObject series(JSONObject row,Fetcher fetcher)throws Exception{
        String id=row.optString("catalog_id"),url=row.optString("url");
        if(!id.matches("tvmaze:[0-9]+")&&url.matches("https://(www\\.)?tvmaze.com/shows/[0-9]+/.*"))id="tvmaze:"+url.replaceFirst("https://(www\\.)?tvmaze.com/shows/([0-9]+)/.*","$2");
        if(!id.matches("tvmaze:[0-9]+")){
            JSONArray hits=new JSONArray(fetcher.fetch("https://api.tvmaze.com/search/shows?q="+URLEncoder.encode(row.getString("name"),"UTF-8")));JSONObject match=null;
            for(int i=0;i<hits.length();i++){JSONObject show=obj(hits.getJSONObject(i),"show");if(!normalized(show.optString("name")).equals(normalized(row.getString("name"))))continue;if(row.optInt("year")>0&&year(show.optString("premiered"))!=row.optInt("year"))continue;if(match!=null)return new JSONObject();match=show;}
            if(match==null)return new JSONObject();id="tvmaze:"+match.getLong("id");
        }
        return tvmaze(new JSONObject(fetcher.fetch("https://api.tvmaze.com/shows/"+id.substring(7)+"?embed[]=cast&embed[]=episodes")));
    }
    static JSONObject load(Context context,JSONObject row,Fetcher fetcher)throws Exception{
        String name=row.optString("name"),kind=row.optString("kind");if(name.trim().isEmpty()||name.length()>300||!Arrays.asList("movie","series","anime").contains(kind))return new JSONObject();
        byte[] digest=MessageDigest.getInstance("SHA-256").digest((normalized(name)+"|"+kind+"|"+row.optInt("year")+"|"+row.optString("catalog_id")).getBytes(StandardCharsets.UTF_8));StringBuilder key=new StringBuilder();for(byte b:digest)key.append(String.format(Locale.ROOT,"%02x",b&255));
        AtomicFile store=new AtomicFile(new File(context.getCacheDir(),"title-"+key+".json"));JSONObject saved=null;
        try(InputStream in=store.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>3*1024*1024)throw new IOException();out.write(b,0,n);}saved=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));long age=System.currentTimeMillis()-saved.optLong("time");if(age>=0&&age<86400000)return saved.getJSONObject("details");}catch(Exception ignored){}
        try{JSONObject details=kind.equals("movie")?movie(row,fetcher):series(row,fetcher);FileOutputStream out=null;
            try{out=store.startWrite();out.write(new JSONObject().put("time",System.currentTimeMillis()).put("details",details).toString().getBytes(StandardCharsets.UTF_8));store.finishWrite(out);}catch(Exception e){if(out!=null)store.failWrite(out);}return details;
        }catch(Exception e){if(saved!=null&&!Thread.currentThread().isInterrupted())return saved.getJSONObject("details");throw e;}
    }
}
