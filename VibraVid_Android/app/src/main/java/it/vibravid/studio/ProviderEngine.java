package it.vibravid.studio;

import android.content.Context;
import android.net.Uri;
import android.webkit.CookieManager;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Android catalog/stream adapters ported from the corresponding desktop sources. */
public final class ProviderEngine implements AutoCloseable {
    private final Context context;
    private final ProviderHttp.Transport transport;
    private final ExecutorService catalogWorkers=Executors.newFixedThreadPool(4);
    private final Map<String,JSONObject> entries=Collections.synchronizedMap(new LinkedHashMap<String,JSONObject>(128,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,JSONObject> item){return size()>4096;}
    });
    private static final Set<String> SUPPORTED=new HashSet<>(Arrays.asList("streamingcommunity","streamingcommunity_2","animeunity","animeworld","eurostreaming","raiplay"));
    public ProviderEngine(Context context){this(context,new ProviderHttp.Network());}
    public ProviderEngine(Context context,ProviderHttp.Transport transport){this.context=context.getApplicationContext();this.transport=transport;}
    public static boolean supports(String id){return SUPPORTED.contains(id);}
    @Override public void close(){catalogWorkers.shutdownNow();}
    private ProviderHttp.Response fetch(String url,ProviderHttp.Call call)throws Exception{return transport.fetch("GET",SourceCatalog.https(url).toString(),Collections.emptyMap(),null,call);}
    private ProviderHttp.Response fetch(String url,Map<String,String> headers,ProviderHttp.Call call)throws Exception{return transport.fetch("GET",SourceCatalog.https(url).toString(),headers,null,call);}
    private JSONObject page(ProviderHttp.Response response)throws Exception{
        String text=response.body.trim();if(text.startsWith("{"))return new JSONObject(text);
        Element app=Jsoup.parse(text,response.url).selectFirst("#app[data-page]");
        if(app==null)throw new IllegalArgumentException("La fonte ha cambiato la risposta o richiede una verifica. Prova un’altra fonte.");
        return new JSONObject(app.attr("data-page"));
    }
    private static String encode(String value)throws Exception{return URLEncoder.encode(value,"UTF-8");}
    private static String absolute(String base,String value)throws Exception{return SourceCatalog.https(new URL(new URL(base),value).toString()).toString();}
    private static String root(JSONObject source){return source.optString("url").replaceAll("/+$","");}
    private static JSONObject object(JSONObject parent,String key){JSONObject value=parent.optJSONObject(key);return value==null?new JSONObject():value;}
    private static JSONArray array(JSONObject parent,String key){JSONArray value=parent.optJSONArray(key);return value==null?new JSONArray():value;}
    private static String normalize(String value){return Normalizer.normalize(value.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[^\\p{L}\\p{N}]","");}
    static String problem(Exception error){
        if(error instanceof UnknownHostException)return "Connessione assente. Riprova quando la rete è disponibile.";
        if(error instanceof SocketTimeoutException)return "La fonte impiega troppo tempo. Riprova tra poco.";
        if(error instanceof IllegalArgumentException&&error.getMessage()!=null)return error.getMessage();
        return "La fonte non ha restituito dati leggibili. Riprova o scegli un’altra fonte.";
    }
    private static int score(JSONObject item,String query){String title=normalize(item.optString("name")),q=normalize(query);return title.equals(q)?100:title.startsWith(q)?80:title.contains(q)?60:20;}
    private String remember(JSONObject entry)throws Exception{String key=UUID.randomUUID().toString();entries.put(key,entry);return key;}
    private JSONObject lookup(String key)throws Exception{JSONObject entry=entries.get(key);if(entry==null)throw new IllegalArgumentException("Questa ricerca è scaduta. Cerca nuovamente il titolo.");return new JSONObject(entry.toString());}
    private JSONObject row(JSONObject source,String id,String name,String kind,String url,String poster,Object year,JSONObject raw)throws Exception{
        JSONObject entry=new JSONObject().put("provider",source.getString("id")).put("source",source.getString("name")).put("root",root(source)).put("id",id)
            .put("name",name.contains("<")||name.contains("&")?Jsoup.parse(name).text():name).put("kind",kind).put("url",url).put("poster",poster).put("year",year==null?JSONObject.NULL:year).put("raw",raw);
        String key=remember(entry);return new JSONObject().put("key",key).put("name",entry.getString("name")).put("kind",kind).put("poster",poster).put("year",entry.opt("year"))
            .put("provider",source.getString("id")).put("source",source.getString("name"));
    }
    public JSONObject search(String query,String sourceId,String kind,ProviderHttp.Call call)throws Exception{
        call.check();
        if(query==null||sourceId==null)throw new IllegalArgumentException("Scrivi un titolo e scegli una fonte.");
        if(query.trim().length()<2||query.length()>200)throw new IllegalArgumentException("Scrivi un titolo da 2 a 200 caratteri.");
        if(!Arrays.asList("all","movie","series","anime").contains(kind))throw new IllegalArgumentException("Tipo di ricerca non valido");
        JSONArray catalog=SourceCatalog.list(context);List<JSONObject> chosen=new ArrayList<>();
        for(int i=0;i<catalog.length();i++){JSONObject source=catalog.getJSONObject(i);String id=source.getString("id");
            if(!supports(id)||!sourceId.equals("__all__")&&!sourceId.equals(id))continue;
            if(sourceId.equals("__all__")&&(kind.equals("movie")&&id.equals("eurostreaming")||kind.equals("anime")&&!id.startsWith("anime")))continue;
            chosen.add(source);
        }
        if(chosen.isEmpty())throw new IllegalArgumentException("Questa fonte non ha ancora un adattatore automatico Android.");
        CompletionService<JSONObject> completion=new ExecutorCompletionService<>(catalogWorkers);List<Future<JSONObject>> jobs=new ArrayList<>();
        for(JSONObject source:chosen)jobs.add(completion.submit(()->{
            try{return new JSONObject().put("items",searchSource(source,query,call)).put("source",source.getString("name"));}
            catch(Exception error){call.check();return new JSONObject().put("items",new JSONArray()).put("error",source.getString("name")+": "+problem(error));}
        }));
        List<JSONObject> results=new ArrayList<>();JSONArray warnings=new JSONArray();int successful=0;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(28);
        try{
            for(int completed=0;completed<jobs.size();completed++){
                call.check();long remaining=deadline-System.nanoTime();if(remaining<=0)break;
                Future<JSONObject> job=completion.poll(remaining,TimeUnit.NANOSECONDS);if(job==null)break;
                JSONObject answer=job.get();if(answer.has("error"))warnings.put(answer.getString("error"));else successful++;
                JSONArray items=answer.getJSONArray("items");for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(kind.equals("all")||kind.equals(item.optString("kind"))||kind.equals("anime")&&item.optString("provider").startsWith("anime"))results.add(item);}
            }
        }finally{for(Future<?> job:jobs)if(!job.isDone())job.cancel(true);call.disconnectActive();}
        call.check();if(successful==0&&results.isEmpty())throw new IllegalArgumentException(warnings.length()>0?warnings.getString(0):"Le fonti stanno impiegando troppo tempo. Riprova o seleziona una singola fonte.");
        Map<String,Integer> priority=new HashMap<>();for(int i=0;i<chosen.size();i++)priority.put(chosen.get(i).getString("id"),i);
        results.sort((a,b)->{int relevance=Integer.compare(score(b,query),score(a,query));return relevance!=0?relevance:Integer.compare(priority.get(a.optString("provider")),priority.get(b.optString("provider")));});JSONArray items=new JSONArray();for(JSONObject item:results){if(items.length()>=100)break;items.put(item);}
        return new JSONObject().put("items",items).put("warnings",warnings).put("successful",successful).put("queried",chosen.size());
    }
    private JSONArray searchSource(JSONObject source,String query,ProviderHttp.Call call)throws Exception{
        call.check();String id=source.getString("id"),base=root(source);JSONArray output=new JSONArray();
        if(id.startsWith("streamingcommunity")){
            Set<String> seen=new HashSet<>();Exception last=null;int catalogs=0;
            for(String language:new String[]{"it","en"})try{
                ProviderHttp.Response response=fetch(base+"/"+language+"/search?q="+encode(query),Collections.singletonMap("Accept","application/json"),call);JSONObject data=page(response);JSONObject props=object(data,"props");
                if(!props.has("titles")&&!data.has("data"))throw new IllegalArgumentException("Il catalogo della fonte non è leggibile. Riprova o scegli un’altra fonte.");
                JSONObject effective=new JSONObject(source.toString()).put("url",ProviderHttp.origin(response.url));JSONArray titles=props.has("titles")?array(props,"titles"):array(data,"data");String cdn=props.optString("cdn_url");if(cdn.isEmpty())cdn=root(effective).replace("streaming","cdn.streaming");
                for(int i=0;i<titles.length()&&i<60;i++){JSONObject title=titles.getJSONObject(i);String titleId=title.optString("id");if(titleId.isEmpty()||!seen.add(titleId))continue;
                    String date=title.optString("last_air_date",title.optString("release_date")),poster="";JSONArray translations=array(title,"translations");
                    for(int t=0;t<translations.length();t++){JSONObject translation=translations.getJSONObject(t);if(translation.optString("key").equals("first_air_date")||translation.optString("key").equals("release_date"))date=translation.optString("value",date);}
                    for(String wanted:new String[]{"poster","cover","cover_mobile","background"}){JSONArray images=array(title,"images");for(int j=0;j<images.length();j++){JSONObject image=images.getJSONObject(j);if(wanted.equals(image.optString("type"))&&!image.optString("filename").isEmpty()){poster=absolute(cdn+"/", "images/"+image.getString("filename"));break;}}if(!poster.isEmpty())break;}
                    title.put("language",language);String url=root(effective)+"/"+language+"/titles/"+titleId+"-"+title.optString("slug");
                    output.put(row(effective,titleId,title.optString("name"),title.optString("type").equals("movie")?"movie":"series",url,poster,year(date),title));
                }catalogs++;
            }catch(Exception error){call.check();last=error;}
            if(catalogs==0&&last!=null)throw last;return output;
        }
        if(id.equals("animeworld")){
            ProviderHttp.Response response=fetch(base+"/search?keyword="+encode(query),call);Document document=Jsoup.parse(response.body,response.url);
            for(Element link:document.select("a.poster")){Element image=link.selectFirst("img");if(image==null)continue;
                String kind=link.selectFirst(".status .movie")!=null?"movie":"anime";output.put(row(source,link.attr("href"),image.attr("alt"),kind,absolute(response.url,link.attr("href")),absolute(response.url,image.attr("src")),null,new JSONObject()));if(output.length()>=40)break;
            }return output;
        }
        if(id.equals("animeunity")){
            fetch(base,call);String cookies=CookieManager.getInstance().getCookie(base),token=cookie(cookies,"XSRF-TOKEN");Map<String,String> headers=new HashMap<>();headers.put("Origin",base);headers.put("Referer",base+"/");headers.put("X-XSRF-TOKEN",URLDecoder.decode(token,"UTF-8"));headers.put("Content-Type","application/x-www-form-urlencoded");
            JSONObject data=new JSONObject(transport.fetch("POST",base+"/livesearch",headers,"title="+encode(query),call).body);
            JSONArray records=array(data,"records");for(int i=0;i<records.length()&&i<60;i++){JSONObject record=records.getJSONObject(i);String name=record.optString("title_eng");if(name.isEmpty())name=record.optString("title",record.optString("title_it"));
                output.put(row(source,record.optString("id"),name,record.optString("type").equalsIgnoreCase("Movie")?"movie":"anime",base+"/anime/"+record.optString("id")+"-"+record.optString("slug"),record.optString("imageurl"),year(record.optString("year")),record));
            }return output;
        }
        if(id.equals("eurostreaming")){
            JSONArray hits=new JSONArray(fetch(base+"/wp-json/wp/v2/search?search="+encode(query)+"&_fields=id&per_page=15",call).body);
            for(int i=0;i<hits.length();i++){call.check();String postId=hits.getJSONObject(i).optString("id");JSONObject post=new JSONObject(fetch(base+"/wp-json/wp/v2/posts/"+postId+"?_embed=wp:featuredmedia&_fields=content,title,_embedded,link",call).body);
                JSONArray images=array(object(post,"_embedded"),"wp:featuredmedia");String poster=images.length()>0?images.getJSONObject(0).optString("source_url"):"";
                String content=object(post,"content").optString("rendered");Matcher y=Pattern.compile("\\b(?:19|20)\\d{2}\\b").matcher(Jsoup.parse(content).text());
                output.put(row(source,postId,object(post,"title").optString("rendered"),"series",post.optString("link",base+"/?p="+postId),poster,y.find()?Integer.parseInt(y.group()):null,post));
            }return output;
        }
        if(id.equals("raiplay")){
            JSONObject body=new JSONObject().put("templateIn","6470a982e4e0301afe1f81f1").put("templateOut","6516ac5d40da6c377b151642")
                .put("params",new JSONObject().put("param",query).put("from",JSONObject.NULL).put("sort","relevance").put("onlyVideoQuery",false));
            JSONObject data=new JSONObject(transport.fetch("POST","https://www.raiplay.it/atomatic/raiplay-search-service/api/v1/msearch",Collections.singletonMap("Content-Type","application/json"),body.toString(),call).body);
            JSONArray cards=array(object(object(data,"agg"),"titoli"),"cards");for(int i=0;i<cards.length()&&i<15;i++){
                JSONObject card=cards.getJSONObject(i);if(card.optString("path_id").isEmpty())continue;String detail=absolute(base+"/",card.getString("path_id"));JSONObject info=new JSONObject(fetch(detail,call).body);JSONObject program=object(info,"program_info");card.put("detail",info).put("detailUrl",detail);
                String type=program.optString("typology",object(info,"track_info").optString("typology"));output.put(row(source,card.optString("id",card.getString("path_id")),card.optString("titolo"),type.equalsIgnoreCase("film")?"movie":"series",absolute(base+"/",card.optString("url",card.getString("path_id"))),card.optString("immagine").isEmpty()?"":absolute(base+"/",card.getString("immagine")),year(program.optString("year")),card));
            }return output;
        }
        throw new IllegalArgumentException("Adattatore non disponibile");
    }
    private static Object year(String date){Matcher match=Pattern.compile("(?:18|19|20|21)\\d{2}").matcher(date);return match.find()?Integer.parseInt(match.group()):JSONObject.NULL;}
    private static String cookie(String cookies,String name){if(cookies!=null)for(String part:cookies.split(";")){String[] pair=part.trim().split("=",2);if(pair.length==2&&pair[0].equals(name))return pair[1];}return "";}
    private JSONObject episode(JSONObject parent,Object id,Object number,String name,int season,JSONObject raw)throws Exception{
        JSONObject entry=new JSONObject().put("parent",parent).put("id",id).put("number",number).put("name",name).put("season",season).put("raw",raw);
        return new JSONObject().put("key",remember(entry)).put("number",number).put("name",name).put("season",season);
    }
    public JSONObject episodes(String key,int season,ProviderHttp.Call call)throws Exception{
        call.check();if(season < -1 || season > 10000)throw new IllegalArgumentException("Scegli una stagione valida.");
        JSONObject title=lookup(key);String provider=title.getString("provider"),base=title.getString("root");JSONArray seasons=new JSONArray(),episodes=new JSONArray();
        if(provider.startsWith("streamingcommunity")){
            JSONObject detail=page(fetch(title.getString("url"),call)),info=object(object(detail,"props"),"title");JSONArray available=array(info,"seasons");
            for(int i=0;i<available.length();i++){JSONObject s=available.getJSONObject(i);seasons.put(new JSONObject().put("number",s.optInt("number")).put("name","Stagione "+s.optInt("number")));}
            if(season<0&&seasons.length()>0)season=seasons.getJSONObject(0).getInt("number");
            if(season>=0&&seasons.length()>0){boolean present=false;for(int i=0;i<seasons.length();i++)if(seasons.getJSONObject(i).getInt("number")==season)present=true;if(!present)throw new IllegalArgumentException("La stagione scelta non è disponibile.");String seasonUrl=title.getString("url")+"/season-"+season;JSONObject data=page(fetch(seasonUrl,call));JSONArray rows=array(object(object(data,"props"),"loadedSeason"),"episodes");
                for(int i=0;i<rows.length();i++){JSONObject ep=rows.getJSONObject(i);episodes.put(episode(title,ep.get("id"),ep.opt("number"),ep.optString("name","Episodio "+(i+1)),season,ep));}
            }
        }else if(provider.equals("animeunity")){
            seasons.put(new JSONObject().put("number",1).put("name","Episodi"));int count=new JSONObject(fetch(base+"/info_api/"+title.getString("id")+"/",call).body).optInt("episodes_count");
            for(int start=0;start<=count&&start<1000;start+=120){JSONArray rows=array(new JSONObject(fetch(base+"/info_api/"+title.getString("id")+"/1?start_range="+start+"&end_range="+Math.min(start+119,count),call).body),"episodes");
                for(int i=0;i<rows.length();i++){JSONObject ep=rows.getJSONObject(i);episodes.put(episode(title,ep.get("id"),ep.opt("number"),"Episodio "+ep.optString("number"),1,ep));}
            }season=1;
        }else if(provider.equals("animeworld")){
            seasons.put(new JSONObject().put("number",1).put("name","Episodi"));ProviderHttp.Response response=fetch(title.getString("url"),call);
            for(Element link:Jsoup.parse(response.body,response.url).select("li.episode > a")){JSONObject raw=new JSONObject().put("token",link.attr("data-id")).put("play_url",link.attr("href"));String number=link.attr("data-episode-num");episodes.put(episode(title,link.attr("data-episode-id"),number,"Episodio "+number,1,raw));}season=1;
        }else if(provider.equals("eurostreaming")){
            String content=object(title.getJSONObject("raw"),"content").optString("rendered");SortedMap<Integer,Map<Integer,JSONObject>> grouped=new TreeMap<>();
            for(String line:content.split("(?i)<br\\s*/?>")){String text=Jsoup.parse(line).text();Matcher ep=Pattern.compile("(\\d+)×(\\d{2})\\b(?:\\s*[-–]\\s*([^<]+))?").matcher(text);if(!ep.find())continue;
                int s=Integer.parseInt(ep.group(1)),e=Integer.parseInt(ep.group(2));JSONObject raw=new JSONObject().put("line",line);grouped.computeIfAbsent(s,n->new TreeMap<>()).put(e,episode(title,e,e,ep.group(3)==null?"Episodio "+e:ep.group(3).trim(),s,raw));
            }
            for(Integer number:grouped.keySet())seasons.put(new JSONObject().put("number",number).put("name","Stagione "+number));
            if(season<0&&!grouped.isEmpty())season=grouped.firstKey();if(grouped.containsKey(season))for(JSONObject ep:grouped.get(season).values())episodes.put(ep);
        }else if(provider.equals("raiplay")){
            JSONObject raw=title.getJSONObject("raw"),detail=raw.getJSONObject("detail");Map<Integer,String> urls=new TreeMap<>();JSONArray blocks=array(detail,"blocks");int n=0;
            for(int i=0;i<blocks.length();i++){JSONObject block=blocks.getJSONObject(i);if(Arrays.asList("clip","extra").contains(block.optString("name").toLowerCase(Locale.ROOT)))continue;JSONArray sets=array(block,"sets");
                for(int j=0;j<sets.length();j++){JSONObject set=sets.getJSONObject(j);if(object(set,"episode_size").optInt("number")<=0)continue;int number=++n;String endpoint=raw.getString("detailUrl").replaceAll("\\.json$","")+"/"+block.optString("id")+"/"+set.optString("id")+"/episodes.json";urls.put(number,endpoint);seasons.put(new JSONObject().put("number",number).put("name",set.optString("name","Stagione "+number)));}
            }
            if(season<0&&!urls.isEmpty())season=urls.keySet().iterator().next();if(urls.containsKey(season)){JSONObject info=new JSONObject(fetch(urls.get(season),call).body);JSONArray cards=array(info,"cards");if(cards.length()==0){JSONArray ss=array(info,"seasons");for(int i=0;i<ss.length();i++){JSONArray es=array(ss.getJSONObject(i),"episodes");for(int j=0;j<es.length();j++){JSONArray cc=array(es.getJSONObject(j),"cards");for(int k=0;k<cc.length();k++)cards.put(cc.get(k));}}}
                for(int i=0;i<cards.length();i++){JSONObject ep=cards.getJSONObject(i);episodes.put(episode(title,ep.optString("id"),ep.optInt("episode",i+1),ep.optString("episode_title",ep.optString("name","Episodio "+(i+1))),season,ep));}
            }
        }
        call.check();return new JSONObject().put("seasons",seasons).put("season",season).put("episodes",episodes);
    }
    public JSONObject resolve(String key,String episodeKey,ProviderHttp.Call call)throws Exception{
        JSONObject title=lookup(key),ep=null;if(!episodeKey.isEmpty()){ep=lookup(episodeKey);JSONObject parent=ep.optJSONObject("parent");if(parent==null||!parent.optString("id").equals(title.optString("id"))||!parent.optString("provider").equals(title.optString("provider")))throw new IllegalArgumentException("L’episodio non appartiene a questo titolo");}
        String provider=title.getString("provider"),base=title.getString("root"),pageUrl=title.getString("url"),name=title.getString("name"),url="";Map<String,String> headers=new HashMap<>();headers.put("User-Agent",ProviderHttp.USER_AGENT);
        if(!title.optString("kind").equals("movie")&&ep==null)throw new IllegalArgumentException("Scegli un episodio da scaricare.");
        if(ep!=null)name+=" · S"+ep.optInt("season")+"E"+ep.optString("number");
        if(provider.startsWith("streamingcommunity")){
            String language=title.getJSONObject("raw").optString("language","it"),iframe=base+"/"+language+"/iframe/"+title.getString("id");
            if(ep!=null)iframe+="?episode_id="+encode(ep.getString("id"))+"&next_episode=1";
            ProviderHttp.Response response=fetch(iframe,call);Element frame=Jsoup.parse(response.body,response.url).selectFirst("iframe[src]");if(frame==null)throw new IllegalArgumentException("Il player della fonte non ha fornito il video. Riprova o scegli un’altra fonte.");
            pageUrl=absolute(response.url,frame.attr("src"));url=player(pageUrl,call);headers.put("Referer",pageUrl);headers.put("Origin",ProviderHttp.origin(pageUrl));
        }else if(provider.equals("animeunity")){
            if(ep==null){JSONArray available=episodes(key,1,call).getJSONArray("episodes");if(available.length()==0)throw new IllegalArgumentException("Nessun episodio disponibile");ep=lookup(available.getJSONObject(0).getString("key"));}
            String embed=fetch(base+"/embed-url/"+encode(ep.getString("id")),call).body.trim();if(embed.startsWith("\""))embed=new JSONArray("["+embed+"]").getString(0);
            pageUrl=SourceCatalog.https(embed).toString();url=player(pageUrl,call);headers.put("Referer",pageUrl);
        }else if(provider.equals("animeworld")){
            fetch(base,call);ProviderHttp.Response detail=fetch(title.getString("url"),call);String csrf="";Document document=Jsoup.parse(detail.body,detail.url);Element token=document.selectFirst("meta[name=csrf-token]");if(token!=null)csrf=token.attr("content");if(csrf.isEmpty()){token=document.selectFirst("input[name=_csrf]");if(token!=null)csrf=token.attr("value");}
            if(ep==null){JSONArray available=episodes(key,1,call).getJSONArray("episodes");if(available.length()==0)throw new IllegalArgumentException("Nessun episodio disponibile");ep=lookup(available.getJSONObject(0).getString("key"));}
            Map<String,String> apiHeaders=new HashMap<>();apiHeaders.put("CSRF-Token",csrf);apiHeaders.put("X-Requested-With","XMLHttpRequest");apiHeaders.put("Origin",base);apiHeaders.put("Referer",absolute(base+"/",ep.getJSONObject("raw").optString("play_url")));
            JSONObject info=new JSONObject(fetch(base+"/api/episode/info?id="+encode(ep.getJSONObject("raw").optString("token"))+"&alt=0",apiHeaders,call).body);url=info.optString("grabber");headers.put("Referer",pageUrl);
        }else if(provider.equals("eurostreaming")){
            Document line=Jsoup.parse(ep.getJSONObject("raw").getString("line"));String loadm="",maxstream="";
            for(Element link:line.select("a[href]")){String value=link.attr("href");if(value.matches("https://loadm[^/]+/.*"))loadm=value;else if(value.startsWith("https://uprot.net/msf/"))maxstream=value;}
            if(!loadm.isEmpty()){String[] parts=loadm.split("#",2);pageUrl=parts[0].replaceAll("/+$","")+"/";String id=parts.length==2?parts[1]:"";
                String hex=fetch(pageUrl+"api/v1/video?id="+encode(id)+"&w=2560&h=1440&r="+encode(title.getString("url")),Collections.singletonMap("Referer",pageUrl),call).body.replaceAll("[^0-9a-fA-F]","");
                if(hex.length()%2!=0)hex="0"+hex;byte[] encrypted=new byte[hex.length()/2];for(int i=0;i<encrypted.length;i++)encrypted[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
                // Public player protocol: only API metadata is decoded, never protected media.
                Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec("kiemtienmua911ca".getBytes(StandardCharsets.UTF_8),"AES"),new IvParameterSpec("1234567890oiuytr".getBytes(StandardCharsets.UTF_8)));
                JSONObject data=new JSONObject(new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8));url=data.optString("cf",data.optString("source"));headers.put("Referer",pageUrl);
            }else if(!maxstream.isEmpty()){
                String mse=maxstream.replace("/msf/","/mse/");ProviderHttp.Response response=fetch(mse,call);Document doc=Jsoup.parse(response.body,response.url);String destination="";for(Element link:doc.select("a[href]"))if(link.text().toLowerCase(Locale.ROOT).contains("continue")){destination=absolute(response.url,link.attr("href"));break;}
                if(destination.isEmpty()){Matcher parts=Pattern.compile("decodedBaseUrl\\s*=\\s*atob\\(['\"]([A-Za-z0-9+/=]+)['\"]\\)\\s*;\\s*var\\s+\\w+\\s*=\\s*atob\\(['\"]([A-Za-z0-9+/=]+)['\"]\\)").matcher(response.body);if(parts.find())destination=new String(android.util.Base64.decode(parts.group(1),0),StandardCharsets.UTF_8)+new String(android.util.Base64.decode(parts.group(2),0),StandardCharsets.UTF_8);}
                if(!destination.isEmpty())url=mediaUrl(fetch(destination,Collections.singletonMap("Referer",mse),call).body);headers.put("Referer","https://maxstream.video/");headers.put("Origin","https://maxstream.video");
            }
        }else if(provider.equals("raiplay")){
            String content=ep==null?object(title.getJSONObject("raw"),"detail").optString("first_item_path"):ep.getJSONObject("raw").optString("weblink",ep.getJSONObject("raw").optString("url"));
            if(content.isEmpty()&&ep==null)content=new JSONObject(fetch(pageUrl+".json",call).body).optString("first_item_path");
            content=absolute(base+"/",content);if(!content.endsWith(".json"))content=content.replaceAll("\\.html$","")+".json";
            JSONObject video=object(new JSONObject(fetch(content,call).body),"video");String relinker=video.optString("content_url");String contentId=Uri.parse(relinker).getQueryParameter("cont");if(contentId==null)throw new IllegalArgumentException("La fonte non ha fornito il video.");
            JSONObject stream=new JSONObject(fetch("https://mediapolisvod.rai.it/relinker/relinkerServlet.htm?cont="+encode(contentId)+"&output=62",call).body);
            if(array(object(stream,"licence_server_map"),"drmLicenseUrlValues").length()>0)throw new IllegalArgumentException("Questo titolo richiede una licenza offline ufficiale del servizio.");
            JSONArray videos=array(stream,"video");if(videos.length()>0)url=videos.getString(0);headers.put("Referer","https://www.raiplay.it/");
        }
        call.check();if(url.isEmpty())throw new IllegalArgumentException("Nessun video scaricabile dalla fonte. Riprova o scegli un altro risultato.");
        SourceCatalog.https(url);String declaredMime=provider.startsWith("streamingcommunity")||provider.equals("eurostreaming")?"application/x-mpegURL":provider.equals("animeworld")?"video/mp4":url.contains("/playlist/")?"application/x-mpegURL":null;
        String mime=MediaDetector.type(url,null);if(mime==null)mime=MediaDetector.type(url,declaredMime);if(mime==null)throw new IllegalArgumentException("La fonte ha restituito un formato video non supportato.");
        JSONObject h=new JSONObject();for(Map.Entry<String,String> header:headers.entrySet())h.put(header.getKey(),header.getValue());
        return new JSONObject().put("name",name.length()>180?name.substring(0,180):name).put("url",url).put("mime",mime).put("source",title.getString("source")).put("sourcePage",pageUrl).put("headers",h)
            .put("providerKey",provider+":"+title.getString("id")+(ep==null?"":":"+ep.getString("id")));
    }
    private String player(String url,ProviderHttp.Call call)throws Exception{
        ProviderHttp.Response response=fetch(url,call);String text=response.body.replace("\\/","/");
        Matcher token=Pattern.compile("(?:['\"]token['\"]|token)\\s*:\\s*['\"]([^'\"]+)['\"]").matcher(text),expires=Pattern.compile("(?:['\"]expires['\"]|expires)\\s*:\\s*['\"]([^'\"]+)['\"]").matcher(text);
        Matcher master=Pattern.compile("(?:['\"]url['\"]|url)\\s*:\\s*['\"](https?://[^'\"]+)['\"]").matcher(text);
        if(token.find()&&expires.find()&&master.find()){
            String found=Jsoup.parse(master.group(1)).text();Uri parsed=Uri.parse(found);Uri.Builder builder=parsed.buildUpon().clearQuery();
            for(String parameter:parsed.getQueryParameterNames())if(!parameter.equals("token")&&!parameter.equals("expires")&&!parameter.equals("h"))for(String value:parsed.getQueryParameters(parameter))builder.appendQueryParameter(parameter,value);
            if(Pattern.compile("window\\.canPlayFHD\\s*=\\s*true").matcher(text).find())builder.appendQueryParameter("h","1");
            return builder.appendQueryParameter("token",token.group(1)).appendQueryParameter("expires",expires.group(1)).build().toString();
        }
        return mediaUrl(text);
    }
    private static String mediaUrl(String text){Matcher match=Pattern.compile("https://[^\\s'\"<>]+\\.(?:m3u8|mpd|mp4|webm)(?:\\?[^\\s'\"<>;]*)?",Pattern.CASE_INSENSITIVE).matcher(text.replace("\\/","/"));return match.find()?org.jsoup.parser.Parser.unescapeEntities(match.group(),false):"";}
}
