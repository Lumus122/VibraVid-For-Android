package it.vibravid.studio;

import android.webkit.CookieManager;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

/** HTTPS requests for catalogs and player metadata; no site JavaScript is executed. */
public final class ProviderHttp {
    public static final String USER_AGENT="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";
    public interface Transport { Response fetch(String method,String url,Map<String,String> headers,String body,Call call)throws Exception; }
    public static final class Response {
        public final String url,body;
        public Response(String url,String body){this.url=url;this.body=body;}
    }
    public static final class Call {
        private final AtomicBoolean cancelled=new AtomicBoolean();
        private final Set<HttpURLConnection> connections=ConcurrentHashMap.newKeySet();
        public void cancel(){cancelled.set(true);for(HttpURLConnection connection:connections)connection.disconnect();}
        public void disconnectActive(){for(HttpURLConnection connection:connections)connection.disconnect();}
        public void check()throws InterruptedIOException{if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Operazione interrotta");}
    }
    public static final class Network implements Transport {
        @Override public Response fetch(String method,String url,Map<String,String> headers,String body,Call call)throws Exception{
            String originalOrigin=origin(url);
            for(int redirect=0;redirect<=6;redirect++){
                call.check();SourceCatalog.https(url);
                HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();call.connections.add(connection);
                try{
                    call.check();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(8000);connection.setReadTimeout(12000);connection.setRequestMethod(method);
                    connection.setRequestProperty("User-Agent",USER_AGENT);connection.setRequestProperty("Accept-Encoding","gzip");
                    connection.setRequestProperty("Accept","text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
                    for(Map.Entry<String,String> entry:headers.entrySet()){
                        String key=entry.getKey();
                        if((key.equalsIgnoreCase("Authorization")||key.equalsIgnoreCase("X-XSRF-TOKEN")||key.equalsIgnoreCase("CSRF-Token"))&&!originalOrigin.equals(origin(url)))continue;
                        String value=entry.getValue();if(value!=null&&value.indexOf('\r')<0&&value.indexOf('\n')<0)connection.setRequestProperty(key,value);
                    }
                    String cookie=CookieManager.getInstance().getCookie(url);if(cookie!=null&&!cookie.isEmpty())connection.setRequestProperty("Cookie",cookie);
                    if(body!=null&&method.equals("POST")){connection.setDoOutput(true);byte[] bytes=body.getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
                    int code=connection.getResponseCode();call.check();
                    for(Map.Entry<String,List<String>> entry:connection.getHeaderFields().entrySet())if("Set-Cookie".equalsIgnoreCase(entry.getKey()))for(String value:entry.getValue())CookieManager.getInstance().setCookie(url,value);
                    if(code==301||code==302||code==303||code==307||code==308){
                        String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Redirect incompleto della fonte");
                        url=new URL(new URL(url),location).toString();if(code==303||(code==301||code==302)&&method.equals("POST")){method="GET";body=null;}continue;
                    }
                    if(code==401||code==403)throw new IllegalArgumentException("La fonte richiede l’accesso o ha rifiutato la ricerca automatica. Prova un’altra fonte.");
                    if(code==429)throw new IllegalArgumentException("La fonte ha ricevuto troppe richieste. Attendi un minuto e riprova.");
                    if(code<200||code>=300)throw new IllegalArgumentException("La fonte non è disponibile ora ("+code+"). Riprova o scegli un’altra fonte.");
                    InputStream stream=connection.getInputStream();if("gzip".equalsIgnoreCase(connection.getContentEncoding()))stream=new GZIPInputStream(stream);
                    try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
                        byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){call.check();if(out.size()+n>4*1024*1024)throw new IOException("Risposta della fonte troppo grande");out.write(bytes,0,n);}
                        Charset charset=StandardCharsets.UTF_8;String contentType=connection.getContentType();
                        if(contentType!=null){java.util.regex.Matcher match=java.util.regex.Pattern.compile("charset=([A-Za-z0-9_-]+)").matcher(contentType);if(match.find())try{charset=Charset.forName(match.group(1));}catch(Exception ignored){}}
                        return new Response(url,new String(out.toByteArray(),charset));
                    }
                }finally{connection.disconnect();call.connections.remove(connection);}
            }
            throw new IOException("Troppi redirect della fonte");
        }
    }
    public static String origin(String url)throws MalformedURLException{URL parsed=new URL(url);return parsed.getProtocol()+"://"+parsed.getAuthority();}
}
