package com.aaronmaxlab.maxplayer.utils;

import android.content.Context;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

/** Optional window blur for elevated glass surfaces; callers should keep a drawable fallback. */
public final class GlassEffects {
    private GlassEffects() { }

    public static boolean applyWindowBlur(Window window, int radiusPx) {
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        Context context = window.getContext();
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (manager == null || !manager.isCrossWindowBlurEnabled()) return false;
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        window.setBackgroundBlurRadius(Math.max(0, radiusPx));
        return true;
    }
}
