package com.aaronmaxlab.maxplayer.player.media3.audio;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


final class AudioFavorites {

    private static final String PREFS = "audio_favorites";
    private static final String KEY_URIS = "uris";

    private AudioFavorites() {}

    static boolean isFavorite(@NonNull Context c, @NonNull String uri) {
        return prefs(c).getStringSet(KEY_URIS, Collections.emptySet()).contains(uri);
    }

    /** @return true if the stream is now a favorite. */
    static boolean toggle(@NonNull Context c, @NonNull String uri) {
        SharedPreferences p = prefs(c);
        // never mutate the set returned by SharedPreferences
        Set<String> set = new HashSet<>(p.getStringSet(KEY_URIS, Collections.emptySet()));
        boolean added = set.add(uri);
        if (!added) set.remove(uri);
        p.edit().putStringSet(KEY_URIS, set).apply();
        return added;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}