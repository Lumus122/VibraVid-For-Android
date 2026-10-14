package it.vibravid.studio;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;

/** Modern MediaStore contract, interrupted writes, and service startup failures. No codec mocking. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class GallerySaveTest {
    Context context;StrictMediaProvider provider;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("gallery_pending",0).edit().clear().commit();
        AppLanguage.set(context,"it");
        provider=new StrictMediaProvider();android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="media";provider.attachInfo(context,info);
        ShadowContentResolver.registerProviderInternal("media",provider);
    }
    @Test public void usesConcreteVolumeAndPublishesOnlyPendingFlag()throws Exception{
        Uri saved;
        try(GalleryWriter writer=new GalleryWriter(context,"A/B: Film","video/mp4",".mp4")){
            assertEquals(1,provider.row.getAsInteger("is_pending").intValue());
            assertEquals("Movies/VibraVid",provider.row.getAsString("relative_path"));
            assertFalse(provider.row.getAsString("_display_name").contains("A/B:"));
            try(OutputStream output=writer.open()){output.write(new byte[]{1,2,3,4});}
            saved=writer.publishValidated();
        }
        assertEquals(Uri.parse("content://media/external_primary/video/media/1"),saved);
        assertEquals(Collections.singleton("is_pending"),provider.lastUpdate.keySet());
        assertEquals(0,provider.row.getAsInteger("is_pending").intValue());
        assertEquals(4,provider.file.length());assertEquals(0,provider.deletes);
        assertTrue(context.getSharedPreferences("gallery_pending",0).getAll().isEmpty());
    }
    @Test public void closingIncompleteWriteRollsBackEntry()throws Exception{
        try(GalleryWriter writer=new GalleryWriter(context,null,"video/mp4",".mp4")){
            try(OutputStream output=writer.open()){output.write(7);}
        }
        assertEquals(1,provider.deletes);assertNull(provider.row);assertFalse(provider.file.exists());
        assertTrue(context.getSharedPreferences("gallery_pending",0).getAll().isEmpty());
    }
    @Test public void recoveryNeverDeletesPublishedMovie()throws Exception{
        Uri uri;
        try(GalleryWriter writer=new GalleryWriter(context,"Film","video/mp4",".mp4")){uri=writer.publishValidated();}
        context.getSharedPreferences("gallery_pending",0).edit().putString("uri",uri.toString()).commit();
        GalleryWriter.recover(context);assertEquals(0,provider.deletes);assertNotNull(provider.row);
        assertTrue(context.getSharedPreferences("gallery_pending",0).getAll().isEmpty());
    }
    @Test public void recoveryRemovesInterruptedPendingEntry()throws Exception{
        GalleryWriter writer=new GalleryWriter(context,"Film","video/mp4",".mp4");
        GalleryWriter.recover(context);assertEquals(1,provider.deletes);assertNull(provider.row);
        writer.close();
    }
    @Test public void failedPublishIsRolledBack()throws Exception{
        provider.rejectUpdate=true;
        try(GalleryWriter writer=new GalleryWriter(context,"Film","video/mp4",".mp4")){
            try{writer.publishValidated();fail("Expected failure");}catch(IOException expected){assertTrue(expected.getMessage().contains("confermato"));}
        }
        assertEquals(1,provider.deletes);assertNull(provider.row);
    }
    @Test public void failedCleanupRetainsRecoveryRecord()throws Exception{
        GalleryWriter writer=new GalleryWriter(context,"Film","video/mp4",".mp4");
        provider.rejectDelete=true;writer.close();assertFalse(context.getSharedPreferences("gallery_pending",0).getAll().isEmpty());
        provider.rejectDelete=false;GalleryWriter.recover(context);assertNull(provider.row);
        assertTrue(context.getSharedPreferences("gallery_pending",0).getAll().isEmpty());
    }
    @Test public void foregroundStartupFailureBecomesVisibleError()throws Exception{
        org.robolectric.android.controller.ServiceController<GalleryExportService> controller=Robolectric.buildService(GalleryExportService.class).create();
        GalleryExportService service=controller.get();
        Field error=GalleryExportService.class.getDeclaredField("startupError");error.setAccessible(true);error.set(service,new SecurityException("denied"));
        Intent intent=new Intent(context,GalleryExportService.class).putExtra("download_id","fixture").putExtra("name","My film");
        assertEquals(android.app.Service.START_NOT_STICKY,service.onStartCommand(intent,0,1));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(GalleryExportService.state().active);assertEquals("offline-fixture",GalleryExportService.state().key);
        assertNull(GalleryExportService.state().uri);assertTrue(GalleryExportService.state().message.contains("accesso"));
        assertNull(provider.row);controller.destroy();
    }
    @Test public void cancelIsHandledWhileServiceIsBusy()throws Exception{
        org.robolectric.android.controller.ServiceController<GalleryExportService> controller=Robolectric.buildService(GalleryExportService.class).create();GalleryExportService service=controller.get();
        Field busy=GalleryExportService.class.getDeclaredField("busy");busy.setAccessible(true);busy.setBoolean(service,true);
        service.onStartCommand(new Intent().setAction("it.vibravid.studio.CANCEL_GALLERY"),0,1);
        assertFalse(GalleryExportService.state().active);assertEquals("Salvataggio annullato",GalleryExportService.state().message);
        controller.destroy();
    }
    public static class StrictMediaProvider extends ContentProvider {
        ContentValues row,lastUpdate;File file;int deletes;boolean rejectUpdate,rejectDelete;
        @Override public boolean onCreate(){return true;}
        @Override public Uri insert(Uri uri,ContentValues values){
            if(!uri.toString().equals("content://media/external_primary/video/media"))throw new IllegalArgumentException("Writing to aggregate volume is unsupported");
            row=new ContentValues(values);file=new File(getContext().getCacheDir(),"gallery-contract.mp4");return Uri.withAppendedPath(uri,"1");
        }
        @Override public int update(Uri uri,ContentValues values,String where,String[] arguments){
            lastUpdate=new ContentValues(values);
            if(!values.keySet().equals(Collections.singleton("is_pending")))throw new IllegalArgumentException("Read-only media metadata");
            if(rejectUpdate)return 0;row.putAll(values);return 1;
        }
        @Override public int delete(Uri uri,String where,String[] arguments){
            if(rejectDelete)throw new SecurityException("Temporary failure");
            if(row==null)return 0;deletes++;row=null;if(file!=null)file.delete();return 1;
        }
        @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
            MatrixCursor cursor=new MatrixCursor(new String[]{"is_pending"});if(row!=null)cursor.addRow(new Object[]{row.getAsInteger("is_pending")});return cursor;
        }
        @Override public String getType(Uri uri){return "video/mp4";}
        @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_READ_WRITE);
        }
    }
}
