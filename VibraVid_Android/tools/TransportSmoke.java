package it.vibravid.studio.tests;
import android.app.*;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import org.json.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Exercises release HTTP transport deterministically, with no network or TLS exceptions. */
public final class TransportSmoke extends Instrumentation {
    private final JSONArray checks=new JSONArray();
    private final List<FakeConnection> connections=new ArrayList<>();
    private Class<?> sourceType,builderType;
    private Object source;
    private final byte[] video="0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){new Thread(()->{
        Bundle result=new Bundle();try{
            ClassLoader loader=getTargetContext().getClassLoader();
            URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL url)throws IOException{FakeConnection c=new FakeConnection(url);connections.add(c);return c;}}:null);
            runOnMainSync(()->android.webkit.CookieManager.getInstance());
            Class<?> implementation=loader.loadClass("it.vibravid.studio.SourceHttpDataSource");Constructor<?> constructor=implementation.getDeclaredConstructor(JSONObject.class);constructor.setAccessible(true);
            JSONObject metadata=new JSONObject().put("headerOrigin","https://qa-http.example").put("headers",new JSONObject().put("Authorization","Bearer QA").put("Referer","https://qa-page.example/"));
            source=constructor.newInstance(metadata);sourceType=loader.loadClass("androidx.media3.datasource.DataSource");builderType=loader.loadClass("androidx.media3.datasource.DataSpec$Builder");
            check(Arrays.equals(read("https://qa-http.example/video",0,-1),video),"Full response body read with transfer lifecycle closed");
            check(new String(read("https://qa-http.example/range",3,4),"UTF-8").equals("3456")&&last().properties.get("Range").equals("bytes=3-6"),"HTTP 206 resumes at requested offset and length");
            check(new String(read("https://qa-http.example/ignore-range",3,4),"UTF-8").equals("3456"),"HTTP 200 from a server ignoring Range skips bytes correctly");
            check(read("https://qa-http.example/end",10,-1).length==0,"HTTP 416 at exact file end returns clean EOF");
            expectError("https://qa-http.example/bad-range",3,4);check(last().disconnected,"Mismatched Content-Range rejected and connection released");
            expectError("https://qa-http.example/reversed-range",3,-1);check(last().disconnected,"Reversed range cannot produce a silently complete truncated video");
            expectError("https://qa-http.example/range-length",3,-1);check(last().disconnected,"Range and Content-Length disagreement rejected");
            expectError("https://qa-http.example/chunked-short-range",3,-1);check(last().disconnected,"Chunked partial response must contain every byte advertised in Content-Range");
            expectError("https://qa-http.example/outside-total",3,-1);check(last().disconnected,"Range beyond the declared file length rejected");
            check(new String(read("https://qa-http.example/chunked-range",3,-1),"UTF-8").equals("3456"),"Complete chunked range accepted without Content-Length");
            expectError("https://qa-http.example/short",0,10);check(last().disconnected,"Premature body EOF detected rather than marking a truncated video complete");
            expectError("https://qa-http.example/forbidden",0,-1);check(last().disconnected,"HTTP 403 fails clearly and closes the rejected connection");
            check(Arrays.equals(read("https://qa-http.example/gzip",0,-1),video),"Server gzip response decoded despite requested identity encoding");
            check(new String(read("https://qa-http.example/gzip",3,4),"UTF-8").equals("3456"),"Ignoring Range with gzip skips decompressed bytes correctly");
            int first=connections.size();read("https://qa-http.example/redirect",0,-1);
            check(connections.get(first).properties.containsKey("Authorization")&&!last().properties.containsKey("Authorization")&&last().properties.containsKey("Referer"),"Cross-origin redirect strips authorization and retains allowed source headers");
            expectError("https://qa-http.example/insecure",0,-1);check(last().disconnected,"HTTPS redirect to insecure destination rejected without opening it");
            expectError("https://qa-http.example/loop",0,-1);check(last().disconnected,"Redirect loop bounded and final connection released");
            result.putString("status","passed");
        }catch(Throwable error){result.putString("status","failed");result.putString("error",android.util.Log.getStackTraceString(error));}
        result.putString("checks",checks.toString());finish("passed".equals(result.getString("status"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    },"VibraVidTransportQA").start();}
    private FakeConnection last(){return connections.get(connections.size()-1);}
    private Object spec(String url,long position,long length)throws Exception{Object builder=builderType.getConstructor().newInstance();builderType.getMethod("setUri",Uri.class).invoke(builder,Uri.parse(url));builderType.getMethod("setPosition",long.class).invoke(builder,position);if(length!=-1)builderType.getMethod("setLength",long.class).invoke(builder,length);return builderType.getMethod("build").invoke(builder);}
    private byte[] read(String url,long position,long length)throws Exception{
        Class<?> specType=getTargetContext().getClassLoader().loadClass("androidx.media3.datasource.DataSpec");
        try{sourceType.getMethod("open",specType).invoke(source,spec(url,position,length));ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[3];int n;while((n=(Integer)sourceType.getMethod("read",byte[].class,int.class,int.class).invoke(source,b,0,b.length))!=-1)out.write(b,0,n);return out.toByteArray();}
        finally{sourceType.getMethod("close").invoke(source);}
    }
    private void expectError(String url,long position,long length)throws Exception{try{read(url,position,length);throw new AssertionError("Expected transfer rejection");}catch(InvocationTargetException expected){if(!(expected.getCause() instanceof IOException)&&!(expected.getCause() instanceof IllegalArgumentException))throw expected;}}
    private final class FakeConnection extends HttpURLConnection {
        final Map<String,String> properties=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final Map<String,String> headers=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        byte[] body=video;
        int status=200;
        boolean disconnected;
        FakeConnection(URL url)throws IOException{super(url);String path=url.getPath();
            if(path.equals("/range")){status=206;headers.put("Content-Range","bytes 3-6/10");body="3456".getBytes();}
            if(path.equals("/bad-range")){status=206;headers.put("Content-Range","bytes 0-3/10");body="0123".getBytes();}
            if(path.equals("/reversed-range")){status=206;headers.put("Content-Range","bytes 3-2/10");body=new byte[0];}
            if(path.equals("/range-length")){status=206;headers.put("Content-Range","bytes 3-6/10");body="345".getBytes();}
            if(path.equals("/chunked-short-range")){status=206;headers.put("Content-Range","bytes 3-6/10");body="345".getBytes();}
            if(path.equals("/chunked-range")){status=206;headers.put("Content-Range","bytes 3-6/10");body="3456".getBytes();}
            if(path.equals("/outside-total")){status=206;headers.put("Content-Range","bytes 3-10/10");body="34567890".getBytes();}
            if(path.equals("/short"))body="012".getBytes();
            if(path.equals("/end")){status=416;headers.put("Content-Range","bytes */10");body=new byte[0];}
            if(path.equals("/forbidden"))status=403;
            if(path.equals("/redirect")){status=302;headers.put("Location","https://qa-other.example/video");}
            if(path.equals("/insecure")){status=302;headers.put("Location","http://qa-other.example/video");}
            if(path.equals("/loop")){status=302;headers.put("Location",url.toString());}
            if(path.equals("/gzip")){headers.put("Content-Encoding","gzip");ByteArrayOutputStream out=new ByteArrayOutputStream();try(GZIPOutputStream gzip=new GZIPOutputStream(out)){gzip.write(video);}body=out.toByteArray();}
        }
        @Override public void setRequestProperty(String name,String value){properties.put(name,value);}
        @Override public String getHeaderField(String name){return headers.get(name);}
        @Override public Map<String,List<String>> getHeaderFields(){Map<String,List<String>> out=new HashMap<>();for(Map.Entry<String,String> h:headers.entrySet())out.put(h.getKey(),Collections.singletonList(h.getValue()));return out;}
        @Override public int getResponseCode(){return status;}
        @Override public String getResponseMessage(){return "QA";}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(body);}
        @Override public long getContentLengthLong(){return url.getPath().startsWith("/chunked-")?-1:body.length;}
        @Override public String getContentEncoding(){return headers.get("Content-Encoding");}
        @Override public void disconnect(){disconnected=true;}
        @Override public boolean usingProxy(){return false;}
        @Override public void connect(){}
    }
    private void check(boolean condition,String name)throws Exception{if(!condition)throw new AssertionError(name);checks.put(name);Bundle status=new Bundle();status.putString("check",name);sendStatus(0,status);}
}
