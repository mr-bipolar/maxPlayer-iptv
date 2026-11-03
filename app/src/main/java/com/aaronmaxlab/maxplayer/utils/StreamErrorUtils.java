package com.aaronmaxlab.maxplayer.utils;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.annotation.Nullable;

import com.aaronmaxlab.maxplayer.R;

public final class StreamErrorUtils {

    private StreamErrorUtils() {
        // Utility class
    }

    @Nullable
    private static View findOverlay(Activity activity) {
        return activity.findViewById(R.id.streamErrorOverlay);
    }

    public static void show(Activity activity) {
        View overlay = findOverlay(activity);
        if (overlay == null) return;

        overlay.setVisibility(View.VISIBLE);

        Window window = activity.getWindow();
        window.setNavigationBarColor(Color.BLACK);
        window.setStatusBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }

        View closeButton = overlay.findViewById(R.id.closeErrorBtn);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> overlay.setVisibility(View.GONE));
        }
    }

}