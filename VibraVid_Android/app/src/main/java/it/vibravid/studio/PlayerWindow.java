package it.vibravid.studio;

import android.os.Build;
import android.view.*;
import androidx.core.view.*;

/** Video extends beneath cutouts; only interactive chrome is inset. */
final class PlayerWindow {
    private static final java.util.Map<Window,Integer> cutouts=new java.util.WeakHashMap<>();
    static void immersive(Window window){
        WindowCompat.setDecorFitsSystemWindows(window,false);
        if(Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams p=window.getAttributes();if(!cutouts.containsKey(window))cutouts.put(window,p.layoutInDisplayCutoutMode);p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;window.setAttributes(p);}
        WindowInsetsControllerCompat controller=WindowCompat.getInsetsController(window,window.getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }
    static void restore(Window window){
        Integer original=cutouts.remove(window);if(Build.VERSION.SDK_INT>=28&&original!=null){WindowManager.LayoutParams p=window.getAttributes();p.layoutInDisplayCutoutMode=original;window.setAttributes(p);}
        WindowCompat.setDecorFitsSystemWindows(window,true);
        WindowCompat.getInsetsController(window,window.getDecorView()).show(WindowInsetsCompat.Type.systemBars());
    }
}
