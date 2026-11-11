package com.aaronmaxlab.maxplayer.player.media3.audio;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.models.LocalDbFavorites;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;

import java.util.Objects;


final class AudioFavorites {
    private AudioFavorites() {}

    static boolean isFavorite(@NonNull Context c,  @NonNull String uri) {
       try {
           SqlDbHelper sqlDbHelper = new SqlDbHelper(c);
           return  sqlDbHelper.isFavorite(uri);
       } catch (Exception e) {
           Log.e("AUDIO", Objects.requireNonNull(e.getMessage()));
           return  false;
       }
    }

    /** @return true if the stream is now a favorite. */
    static boolean toggle(@NonNull Context c,  @NonNull String streamName, @NonNull String uri) {
        try {
            SqlDbHelper sqlDbHelper = new SqlDbHelper(c);
            LocalDbFavorites favorites = new LocalDbFavorites(streamName, "Audio Stream", uri, null, PlaylistType.AUDIO);

            return sqlDbHelper.setFavorite(favorites);
        } catch (Exception e) {
            Log.e("AUDIO", Objects.requireNonNull(e.getMessage()));
            return  false;
        }

    }

}