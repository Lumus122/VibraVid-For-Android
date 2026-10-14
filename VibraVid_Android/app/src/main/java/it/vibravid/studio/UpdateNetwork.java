package it.vibravid.studio;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
final class UpdateNetwork {
    static String read(String url)throws IOException{
        for(int redirects=0;redirects<5;redirects++){
            SourceCatalog.https(url);HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
            try{
                connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(3000);connection.setReadTimeout(4000);
                connection.setRequestProperty("User-Agent","Mozilla/5.0");connection.setRequestProperty("Accept","application/json");
                int code=connection.getResponseCode();
                if(code>=300&&code<400){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Redirect incompleto");url=new URL(new URL(url),location).toString();continue;}
                if(code!=200)throw new IOException("Servizio aggiornamenti non disponibile ("+code+"). Riprova più tardi.");
                try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>2*1024*1024)throw new IOException("Risposta aggiornamenti troppo grande");out.write(buffer,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);
                }
            }finally{connection.disconnect();}
        }throw new IOException("Troppi redirect degli aggiornamenti");
    }
}
