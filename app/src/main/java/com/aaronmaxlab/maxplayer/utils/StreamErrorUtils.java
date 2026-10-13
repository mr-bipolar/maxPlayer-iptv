
package com.aaronmaxlab.maxplayer.utils;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

import com.aaronmaxlab.maxplayer.R;

public final class StreamErrorUtils {

    private StreamErrorUtils() {
        // Utility class
    }

    public static void show(Activity activity) {
        View errorOverlay = activity.findViewById(R.id.streamErrorOverlay);

        if (errorOverlay == null) {
            return;
        }

        errorOverlay.setVisibility(View.VISIBLE);

        Window window = activity.getWindow();
        window.setNavigationBarColor(Color.BLACK);
        window.setStatusBarColor(Color.BLACK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }

        View closeButton = errorOverlay.findViewById(R.id.closeErrorBtn);

        if (closeButton != null) {
            closeButton.setOnClickListener(view ->
                    errorOverlay.setVisibility(View.GONE)
            );
        }
    }

    public static void hide(Activity activity) {
        View errorOverlay = activity.findViewById(R.id.streamErrorOverlay);

        if (errorOverlay != null) {
            errorOverlay.setVisibility(View.GONE);
        }
    }
}

