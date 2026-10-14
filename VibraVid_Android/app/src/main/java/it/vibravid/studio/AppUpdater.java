package it.vibravid.studio;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
/** GitHub release APKs are installed through Android's confirmation screen, with the same signing key. */
final class AppUpdater {
    static final String REPO="https://github.com/Lumus122/VibraVid-For-Android";
    static final String RELEASES="https://api.github.com/repos/Lumus122/VibraVid-For-Android/releases?per_page=20";
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("app_update",0);}
    static int compare(String left,String right){
        int[] a=version(left),b=version(right);for(int i=0;i<3;i++){int result=Integer.compare(a[i],b[i]);if(result!=0)return result;}return 0;
    }
    private static int[] version(String value){
        Matcher match=Pattern.compile("(?:^|[^0-9])(\\d+)\\.(\\d+)\\.(\\d+)(?![0-9])").matcher(value==null?"":value);
        if(!match.find())throw new IllegalArgumentException("Versione della release non riconosciuta");
        return new int[]{Integer.parseInt(match.group(1)),Integer.parseInt(match.group(2)),Integer.parseInt(match.group(3))};
    }
    static JSONObject select(JSONArray releases,String installed)throws Exception{
        JSONObject selected=null;String best=installed;
        for(int i=0;i<releases.length();i++){
            JSONObject release=releases.getJSONObject(i);if(release.optBoolean("draft")||release.optBoolean("prerelease"))continue;
            String tag=release.optString("tag_name");try{if(compare(tag,best)<=0)continue;}catch(IllegalArgumentException invalid){continue;}
            JSONArray assets=release.optJSONArray("assets");if(assets==null)continue;
            for(int j=0;j<assets.length();j++){
                JSONObject asset=assets.getJSONObject(j);String url=asset.optString("browser_download_url");long size=asset.optLong("size");
                if(!asset.optString("name").equals("VibraVid_Studio_Android.apk")||!url.startsWith(REPO+"/releases/download/")||size<=0||size>200L*1024*1024)continue;
                SourceCatalog.https(url);selected=new JSONObject(asset.toString()).put("version",tag);best=tag;break;
            }
        }return selected;
    }
    static synchronized JSONObject check(Context context)throws Exception{
        if(prefs(context).getLong("download",-1)>0){prefs(context).edit().putBoolean("offered",false).putBoolean("permissionOffered",false).apply();return new JSONObject().put("message","Aggiornamento già in download. L’installazione sarà proposta quando è pronto.");}
        PackageInfo installed=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
        JSONObject asset=select(new JSONArray(UpdateNetwork.read(RELEASES)),installed.versionName);
        if(asset==null)return new JSONObject().put("message","L’app è già aggiornata rispetto alle release Android pubblicate.");
        File external=context.getExternalFilesDir(null);if(external==null)throw new IOException("Spazio aggiornamenti non disponibile");File directory=new File(external,"updates");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Spazio aggiornamenti non disponibile");
        File file=new File(directory,"update.apk");if(file.exists()&&!file.delete())throw new IOException("Il precedente aggiornamento non è rimovibile");
        DownloadManager manager=(DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request=new DownloadManager.Request(Uri.parse(asset.getString("browser_download_url"))).setTitle("VibraVid "+asset.getString("version")).setMimeType("application/vnd.android.package-archive").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationUri(Uri.fromFile(file));
        long id=manager.enqueue(request);
        if(!prefs(context).edit().putLong("download",id).putLong("size",asset.getLong("size")).putString("version",asset.getString("version")).putString("digest",asset.optString("digest")).putBoolean("ready",false).putBoolean("offered",false).putBoolean("permissionOffered",false).commit()){manager.remove(id);throw new IOException("Salvataggio aggiornamento non riuscito");}
        return new JSONObject().put("message","Scarico l’aggiornamento. Android chiederà conferma per installarlo.");
    }
    static synchronized void complete(Activity activity)throws Exception{
        Context context=activity.getApplicationContext();SharedPreferences prefs=prefs(context);long id=prefs.getLong("download",-1);if(id<=0)return;
        DownloadManager manager=(DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        String target=prefs.getString("version","");
        if(!target.isEmpty()&&compare(context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName,target)>=0){manager.remove(id);prefs.edit().clear().apply();return;}
        try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(id))){
            if(cursor==null||!cursor.moveToFirst()){prefs.edit().clear().apply();return;}
            int status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if(status==DownloadManager.STATUS_FAILED){manager.remove(id);prefs.edit().clear().apply();throw new IOException("Download aggiornamento non riuscito. Controlla connessione e spazio libero, poi tocca Update.");}
            if(status!=DownloadManager.STATUS_SUCCESSFUL)return;
        }
        File file=new File(context.getExternalFilesDir(null),"updates/update.apk");
        try{
            if(file.length()!=prefs.getLong("size",-1)||file.length()>200L*1024*1024)throw new SecurityException("APK incompleto");
            String digest=prefs.getString("digest","");if(!digest.isEmpty()){
                if(!digest.matches("sha256:[a-fA-F0-9]{64}"))throw new SecurityException("Hash release non valido");
                MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)hash.update(bytes,0,n);}
                StringBuilder actual=new StringBuilder();for(byte b:hash.digest())actual.append(String.format(Locale.ROOT,"%02x",b&255));if(!actual.toString().equalsIgnoreCase(digest.substring(7)))throw new SecurityException("Hash APK non corrispondente");
            }
            int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
            PackageManager packages=context.getPackageManager();PackageInfo installed=packages.getPackageInfo(context.getPackageName(),flags),candidate=packages.getPackageArchiveInfo(file.getAbsolutePath(),flags);
            if(candidate==null||!context.getPackageName().equals(candidate.packageName)||code(candidate)<=code(installed)||!sameSignatures(installed,candidate))throw new SecurityException("L’APK non è un aggiornamento firmato da VibraVid");
            prefs.edit().putBoolean("ready",true).apply();
        }catch(Exception error){manager.remove(id);prefs.edit().clear().apply();file.delete();throw error;}
        if(prefs.getBoolean("offered",false))return;
        if(!context.getPackageManager().canRequestPackageInstalls()&&prefs.getBoolean("permissionOffered",false))return;
        prefs.edit().putBoolean("offered",true).apply();
        activity.runOnUiThread(()->{
            if(activity.isFinishing()||activity.isDestroyed()||activity instanceof MainActivity&&!((MainActivity)activity).updateVisible()){prefs.edit().putBoolean("offered",false).apply();return;}
            try{
                if(Build.VERSION.SDK_INT>=26&&!context.getPackageManager().canRequestPackageInstalls()){
                    // Remember the pending APK; returning after granting permission resumes installation.
                    prefs.edit().putBoolean("permissionOffered",true).putBoolean("offered",false).apply();activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+context.getPackageName())));return;
                }
                Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".updates",file);
                activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));prefs.edit().putBoolean("offered",true).apply();
            }catch(RuntimeException error){prefs.edit().putBoolean("offered",false).apply();android.widget.Toast.makeText(activity,AppLanguage.text(context,"L’installazione non si apre. Tocca Update per riprovare."),android.widget.Toast.LENGTH_LONG).show();}
        });
    }
    private static long code(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    private static boolean sameSignatures(PackageInfo a,PackageInfo b){
        Signature[] left=Build.VERSION.SDK_INT>=28&&a.signingInfo!=null?a.signingInfo.getApkContentsSigners():a.signatures;
        Signature[] right=Build.VERSION.SDK_INT>=28&&b.signingInfo!=null?b.signingInfo.getApkContentsSigners():b.signatures;
        return left!=null&&left.length>0&&right!=null&&new HashSet<>(Arrays.asList(left)).equals(new HashSet<>(Arrays.asList(right)));
    }
}
