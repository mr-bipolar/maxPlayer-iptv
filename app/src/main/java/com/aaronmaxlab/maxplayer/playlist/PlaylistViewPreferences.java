
package com.aaronmaxlab.maxplayer.playlist;

import android.content.Context;
import android.content.SharedPreferences;

public final class PlaylistViewPreferences {

    private static final String PREFS_NAME = "playlist_view_preferences";
    private static final String KEY_GRID_MODE = "grid_mode";

    private PlaylistViewPreferences() {
    }

    public static boolean isGridEnabled(Context context) {
        return getPreferences(context).getBoolean(KEY_GRID_MODE, false);
    }

    public static void setGridEnabled(Context context, boolean enabled) {
        getPreferences(context).edit()
                .putBoolean(KEY_GRID_MODE, enabled)
                .apply();
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}

