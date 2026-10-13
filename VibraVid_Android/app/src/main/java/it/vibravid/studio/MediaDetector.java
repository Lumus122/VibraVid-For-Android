package it.vibravid.studio;

import android.net.Uri;
import java.util.Locale;

/** A segment is never offered as a complete movie. Query parameters remain intact. */
public final class MediaDetector {
    public static String type(String url,String hint){
        try{
            Uri uri=SourceCatalog.https(url);String path=uri.getPath()==null?"":uri.getPath().toLowerCase(Locale.ROOT);
            String mime=hint==null?"":hint.toLowerCase(Locale.ROOT).split(";")[0].trim();
            if(path.endsWith(".ts")||path.endsWith(".m4s")||path.endsWith(".aac")||path.matches(".*(?:^|/)(?:init|initialization)[^/]*\\.mp4$"))return null;
            if(path.endsWith(".m3u8"))return "application/x-mpegURL";
            if(path.endsWith(".mpd"))return "application/dash+xml";
            if(mime.equals("application/vnd.apple.mpegurl")||mime.equals("application/x-mpegurl"))return "application/x-mpegURL";
            if(mime.equals("application/dash+xml"))return "application/dash+xml";
            if(path.endsWith(".mp4")||path.endsWith(".m4v")||path.endsWith(".webm")||path.endsWith(".mkv")||mime.startsWith("video/"))return path.endsWith(".webm")?"video/webm":"video/mp4";
        }catch(IllegalArgumentException ignored){}
        return null;
    }
    public static String label(String mime){return "application/x-mpegURL".equals(mime)?"HLS":"application/dash+xml".equals(mime)?"DASH":"Video";}
}
