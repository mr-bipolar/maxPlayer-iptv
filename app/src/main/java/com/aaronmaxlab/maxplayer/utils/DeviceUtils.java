package com.aaronmaxlab.maxplayer.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;

public final class DeviceUtils {

    private DeviceUtils() {
        // Prevent instantiation
    }

    public static boolean isTv(Context context) {
        return (context.getResources()
                .getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK)
                == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    public static boolean hasLeanback(Context context) {
        return context.getPackageManager()
                .hasSystemFeature(
                        PackageManager.FEATURE_LEANBACK
                );
    }
}

