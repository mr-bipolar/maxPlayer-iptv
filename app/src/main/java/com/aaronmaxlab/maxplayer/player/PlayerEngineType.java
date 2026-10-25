package com.aaronmaxlab.maxplayer.player;

import androidx.annotation.NonNull;

/** The two playback engines the single {@link PlayerActivity} can run on. */
public enum PlayerEngineType {
    MEDIA3("media3"),
    MPV("mpv");

    public final String key;

    PlayerEngineType(String key) {
        this.key = key;
    }

    @NonNull
    public static PlayerEngineType fromKey(String key, @NonNull PlayerEngineType fallback) {
        for (PlayerEngineType t : values()) {
            if (t.key.equals(key)) return t;
        }
        return fallback;
    }
}