package it.vibravid.studio;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.*;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local request filter. Immutable rules are shared by WebView request threads. */
public final class AdBlocker {
    private static volatile AdBlocker shared;
    private final Set<String> blocked=new HashSet<>(),allowed=new HashSet<>(),exactAllowed=new HashSet<>(),important=new HashSet<>();
    private AdBlocker(Context context)throws IOException{
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(context.getAssets().open("adblock/domains.txt"),StandardCharsets.UTF_8))){
            String line;while((line=reader.readLine())!=null){
                if(line.length()<3||line.charAt(1)!=' ')continue;
                String host=line.substring(2);
                switch(line.charAt(0)){case 'B':blocked.add(host);break;case 'A':allowed.add(host);break;case 'E':exactAllowed.add(host);break;case 'I':important.add(host);break;}
            }
        }
        if(blocked.isEmpty())throw new IOException("Filtri pubblicità non disponibili.");
    }
    public static AdBlocker get(Context context)throws IOException{
        AdBlocker result=shared;if(result!=null)return result;
        synchronized(AdBlocker.class){if(shared==null)shared=new AdBlocker(context.getApplicationContext());return shared;}
    }
    public boolean blocks(String url){
        Uri uri=Uri.parse(url);String scheme=uri.getScheme();
        if(!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))return false;
        String host=uri.getHost();if(host==null)return false;
        try{host=IDN.toASCII(host.toLowerCase(Locale.ROOT));}catch(IllegalArgumentException e){return false;}
        while(host.endsWith("."))host=host.substring(0,host.length()-1);
        if(matches(important,host))return true;
        if(exactAllowed.contains(host)||matches(allowed,host))return false;
        return matches(blocked,host);
    }
    private static boolean matches(Set<String> rules,String host){
        for(String part=host;;){
            if(rules.contains(part))return true;
            int dot=part.indexOf('.');if(dot<0)return false;part=part.substring(dot+1);
        }
    }
    public WebResourceResponse emptyResponse(){
        Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");headers.put("Access-Control-Allow-Origin","*");
        return new WebResourceResponse("text/plain","UTF-8",204,"No Content",headers,new ByteArrayInputStream(new byte[0]));
    }
}
