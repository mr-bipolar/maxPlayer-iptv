package com.aaronmaxlab.maxplayer.utils;

import androidx.annotation.NonNull;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class UIUtils {
    public static void setLightNavigationBars(@NonNull android.app.Activity activity, boolean isLight) {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        controller.setAppearanceLightNavigationBars(isLight);
    }
}