package com.aaronmaxlab.maxplayer.utils;

import android.app.Activity;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public final class UIUtils {

    private static final String DEFAULT_LABEL = "playlist";

    private UIUtils() {}

    public static void setLightNavigationBars(@NonNull Activity activity, boolean isLight) {
        Window window = activity.getWindow();
        WindowCompat.getInsetsController(window, window.getDecorView())
                .setAppearanceLightNavigationBars(isLight);
    }

    /** "https://x/y/list.m3u8?token=1" -> "list"; content://.../primary%3ADownload%2FTelegram%2Fplaylist.m3u -> "playlist". */
    @NonNull
    public static String extractLabel(@Nullable String url) {
        if (url == null) return DEFAULT_LABEL;

        String name = Uri.parse(url).getLastPathSegment();   // already excludes the query string
        if (name == null) return DEFAULT_LABEL;

        // document ids look like "primary:Download/Telegram/playlist.m3u": keep only the file name
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf(':')) + 1);

        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);

        name = name.trim();
        return name.isEmpty() ? DEFAULT_LABEL : name;
    }

    /** Adjust bottom navigation icon and text spacing. */
    public static void adjustBottomNavGap(@NonNull BottomNavigationView bottomNav) {
        final int gap = Math.round(4 * bottomNav.getResources().getDisplayMetrics().density);

        bottomNav.post(() -> {
            if (!(bottomNav.getChildAt(0) instanceof ViewGroup)) return;
            ViewGroup menu = (ViewGroup) bottomNav.getChildAt(0);

            for (int i = 0; i < menu.getChildCount(); i++) {
                View labels = menu.getChildAt(i)
                        .findViewById(com.google.android.material.R.id.navigation_bar_item_labels_group);
                if (labels == null || !(labels.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) continue;

                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) labels.getLayoutParams();
                if (params.topMargin == gap) continue;      // already set: skip a needless layout pass
                params.topMargin = gap;
                labels.setLayoutParams(params);
            }
        });
    }

    /** Pads the view away from the status bar, navigation bar and display cutout (edge-to-edge screens). */
    public static void applySystemBarPadding(@NonNull View root, boolean includeBottom) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, includeBottom ? bars.bottom : 0);
            return insets;
        });
    }

    public static void applySystemBarPadding(@NonNull View root) {
        applySystemBarPadding(root, true);
    }

    public static String toCapitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}