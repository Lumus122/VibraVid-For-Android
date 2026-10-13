package it.vibravid.studio;

import android.net.Uri;
import android.webkit.CookieManager;
import androidx.media3.common.C;
import androidx.media3.datasource.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;

/** Range-aware HTTPS transport; re-scopes cookies and authorization on every redirect. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class SourceHttpDataSource extends BaseDataSource {
    private final JSONObject metadata;
    private HttpURLConnection connection;
    private InputStream input;
    private Uri uri;
    private long remaining;
    private boolean opened;
    SourceHttpDataSource(JSONObject metadata){super(true);this.metadata=metadata;}
    @Override public long open(DataSpec spec)throws IOException {
        transferInitializing(spec);uri=spec.uri;
        try{
            if(spec.httpMethod!=DataSpec.HTTP_METHOD_GET)throw new IOException("Richiesta video non supportata");
            int code=0;
            for(int redirects=0;redirects<=10;redirects++){
                try{SourceCatalog.https(uri.toString());}catch(IllegalArgumentException error){throw new IOException("La fonte ha indicato un collegamento non sicuro",error);}
                connection=(HttpURLConnection)new URL(uri.toString()).openConnection();
                connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(20000);
                connection.setRequestProperty("Accept-Encoding","identity");
                JSONObject headers=metadata.optJSONObject("headers");
                if(headers!=null){Iterator<String> keys=headers.keys();while(keys.hasNext()){
                    String key=keys.next();
                    if(!key.equalsIgnoreCase("Authorization")||Objects.equals(uri.getScheme()+"://"+uri.getEncodedAuthority(),metadata.optString("headerOrigin")))connection.setRequestProperty(key,headers.optString(key));
                }}
                String cookie=CookieManager.getInstance().getCookie(uri.toString());if(cookie!=null&&!cookie.isEmpty())connection.setRequestProperty("Cookie",cookie);
                if(spec.position!=0||spec.length!=C.LENGTH_UNSET){
                    String range="bytes="+spec.position+"-"+(spec.length==C.LENGTH_UNSET?"":Long.toString(Math.addExact(spec.position,spec.length-1)));
                    connection.setRequestProperty("Range",range);
                }
                code=connection.getResponseCode();
                for(Map.Entry<String,List<String>> header:connection.getHeaderFields().entrySet())if("Set-Cookie".equalsIgnoreCase(header.getKey()))for(String value:header.getValue())CookieManager.getInstance().setCookie(uri.toString(),value);
                if(code!=301&&code!=302&&code!=303&&code!=307&&code!=308)break;
                String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Redirect senza destinazione");
                URL destination=new URL(new URL(uri.toString()),location);connection.disconnect();connection=null;uri=Uri.parse(destination.toString());
                if(redirects==10)throw new IOException("Troppi redirect della fonte");
            }
            if(code==416){
                String range=connection.getHeaderField("Content-Range");
                if(range!=null&&range.matches("bytes \\*/[0-9]+")&&spec.position==Long.parseLong(range.substring(range.indexOf('/')+1))){
                    remaining=0;opened=true;transferStarted(spec);return 0;
                }
            }
            if(code<200||code>299)throw new HttpDataSource.InvalidResponseCodeException(code,connection.getResponseMessage(),null,connection.getHeaderFields(),spec,new byte[0]);
            long rangeLength=C.LENGTH_UNSET;
            if(code==206){
                String range=connection.getHeaderField("Content-Range");
                java.util.regex.Matcher match=java.util.regex.Pattern.compile("bytes ([0-9]+)-([0-9]+)/(?:[0-9]+|\\*)").matcher(range==null?"":range);
                try{
                    if(!match.matches())throw new IllegalArgumentException();
                    long start=Long.parseLong(match.group(1)),end=Long.parseLong(match.group(2));
                    if(start!=spec.position||end<start)throw new IllegalArgumentException();
                    rangeLength=Math.addExact(Math.subtractExact(end,start),1);
                    String total=range.substring(range.indexOf('/')+1);
                    if(!total.equals("*")&&end>=Long.parseLong(total))throw new IllegalArgumentException();
                }catch(IllegalArgumentException|ArithmeticException error){throw new IOException("La fonte ha restituito un intervallo video non valido",error);}
            }
            input=connection.getInputStream();
            boolean gzip="gzip".equalsIgnoreCase(connection.getContentEncoding());
            if(gzip){if(code==206)throw new IOException("Intervallo video compresso non supportato dalla fonte");input=new java.util.zip.GZIPInputStream(input);}
            long skip=code==200?spec.position:0;
            long content=gzip?C.LENGTH_UNSET:connection.getContentLengthLong();
            if(rangeLength!=C.LENGTH_UNSET){
                if(content>=0&&content!=rangeLength)throw new IOException("La fonte ha restituito una lunghezza video incoerente");
                content=rangeLength;
            }
            remaining=spec.length!=C.LENGTH_UNSET?spec.length:content<0?C.LENGTH_UNSET:Math.max(0,content-skip);
            opened=true;transferStarted(spec);
            if(skip>0){byte[] buffer=new byte[8192];while(skip>0){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                int n=input.read(buffer,0,(int)Math.min(buffer.length,skip));if(n<0)throw new EOFException();skip-=n;bytesTransferred(n);
            }}
            return remaining;
        }catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    @Override public int read(byte[] buffer,int offset,int length)throws IOException{
        if(length==0)return 0;if(remaining==0)return C.RESULT_END_OF_INPUT;
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
        int read=input.read(buffer,offset,remaining==C.LENGTH_UNSET?length:(int)Math.min(length,remaining));
        if(read<0){if(remaining>0)throw new EOFException("Video incompleto");return C.RESULT_END_OF_INPUT;}
        if(remaining!=C.LENGTH_UNSET)remaining-=read;bytesTransferred(read);return read;
    }
    @Override public Uri getUri(){return uri;}
    @Override public Map<String,List<String>> getResponseHeaders(){return connection==null?Collections.emptyMap():connection.getHeaderFields();}
    @Override public void close()throws IOException {
        try{if(input!=null)input.close();}finally{
            input=null;if(connection!=null)connection.disconnect();connection=null;uri=null;
            if(opened){opened=false;transferEnded();}
        }
    }
}
