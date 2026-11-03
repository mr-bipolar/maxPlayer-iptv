package com.aaronmaxlab.maxplayer.utils;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.view.View;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;


public class PlayerUtils {

    public static void setFullscreen(Activity activity, boolean fullscreen) {
        View decorView = activity.getWindow().getDecorView();

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(
                        activity.getWindow(),
                        decorView
                );

        if (fullscreen) {
            activity.setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            );

            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat
                            .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }  else {
        controller.show(WindowInsetsCompat.Type.systemBars());

        activity.setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        );

            activity.getWindow().getDecorView().post(() ->
                    UIUtils.setLightNavigationBars(activity, false)
            );
    }
    }

    public static void toggleFullscreen(Activity activity, boolean fullscreen) {
        setFullscreen(activity, fullscreen);
    }
}
