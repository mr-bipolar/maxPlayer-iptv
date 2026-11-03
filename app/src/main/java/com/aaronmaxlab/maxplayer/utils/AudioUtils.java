package com.aaronmaxlab.maxplayer.utils;


import android.content.Context;
import android.content.SharedPreferences;

public final class AudioUtils {

    private static final String PREFS_NAME = "audio_player";
    private static final String KEY_AUDIO_STOPPED = "audio_stopped";

    private AudioUtils() {
        // Utility class.
    }

    public static void setAudioStopped(Context context, boolean stopped) {
        getPreferences(context)
                .edit()
                .putBoolean(KEY_AUDIO_STOPPED, stopped)
                .apply();
    }

    public static boolean isAudioStopped(Context context) {
        return getPreferences(context)
                .getBoolean(KEY_AUDIO_STOPPED, false);
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
