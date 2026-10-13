package com.aaronmaxlab.maxplayer.utils;

import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class UIUtils {
    public static void setLightNavigationBars(@NonNull android.app.Activity activity, boolean isLight) {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        controller.setAppearanceLightNavigationBars(isLight);
    }

    public static String extractLabel(String url){
        String fileName = Uri.parse(url).getLastPathSegment();

        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "playlist";
        } else {
            // Remove query parameters if present
            int queryIndex = fileName.indexOf("?");
            if (queryIndex >= 0) {
                fileName = fileName.substring(0, queryIndex);
            }

            // Remove any file extension
            int dotIndex = fileName.lastIndexOf(".");
            if (dotIndex > 0) {
                fileName = fileName.substring(0, dotIndex);
            }
        }

        return fileName;
    }

    // Adjust bottom navigation icon and text spacing
    public static void adjustBottomNavGap(BottomNavigationView bottomNav) {
        bottomNav.post(() -> {
            View menuView = bottomNav.getChildAt(0);

            if (!(menuView instanceof ViewGroup)) {
                return;
            }

            ViewGroup menuViewGroup = (ViewGroup) menuView;

            for (int i = 0; i < menuViewGroup.getChildCount(); i++) {
                View itemView = menuViewGroup.getChildAt(i);

                View labelGroup = itemView.findViewById(
                        com.google.android.material.R.id.navigation_bar_item_labels_group
                );

                if (labelGroup == null) {
                    continue;
                }

                ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) labelGroup.getLayoutParams();

                params.topMargin = (int) (
                        4 * bottomNav.getResources()
                                .getDisplayMetrics().density
                );

                labelGroup.setLayoutParams(params);
            }
        });
    }

}