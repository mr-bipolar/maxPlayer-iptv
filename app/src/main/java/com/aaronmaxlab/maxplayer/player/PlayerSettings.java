package com.aaronmaxlab.maxplayer.player;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.aaronmaxlab.maxplayer.R;

/**
 * User choices for the player. The engine choice is global: "Media3" => every video opens on Media3,
 * "MPV" => every video opens on mpv. Call {@link #showEnginePicker} from your Settings screen
 * and from the player's "more" menu (PlayerActivity already does the latter).
 */
public final class PlayerSettings {

    public interface OnEngineChanged {
        void onChanged(@NonNull PlayerEngineType newType);
    }

    private static final String PREFS = "player_settings";
    private static final String KEY_ENGINE = "engine";
    private static final String KEY_SUB_SIZE_INDEX = "subtitle_size_index";

    /** 0 = small, 1 = medium, 2 = large, 3 = extra large (same index for both engines). */
    public static final int DEFAULT_SUB_SIZE_INDEX = 1;

    private PlayerSettings() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    public static PlayerEngineType getEngine(Context c) {
        return PlayerEngineType.fromKey(prefs(c).getString(KEY_ENGINE, null), PlayerEngineType.MEDIA3);
    }

    public static void setEngine(Context c, @NonNull PlayerEngineType type) {
        prefs(c).edit().putString(KEY_ENGINE, type.key).apply();
    }

    public static int getSubtitleSizeIndex(Context c) {
        return prefs(c).getInt(KEY_SUB_SIZE_INDEX, DEFAULT_SUB_SIZE_INDEX);
    }

    public static void setSubtitleSizeIndex(Context c, int index) {
        prefs(c).edit().putInt(KEY_SUB_SIZE_INDEX, index).apply();
    }

    /** Radio dialog "Media3 / MPV". Saves the choice and reports it only if it actually changed. */
    public static void showEnginePicker(@NonNull Context context, @Nullable OnEngineChanged listener) {
        final PlayerEngineType[] types = PlayerEngineType.values();
        final PlayerEngineType current = getEngine(context);
        CharSequence[] labels = {
                context.getString(R.string.player_engine_media3),
                context.getString(R.string.player_engine_mpv)
        };
        new AlertDialog.Builder(context, R.style.PlayerDialogTheme)
                .setTitle(R.string.player_engine_title)
                .setSingleChoiceItems(labels, current.ordinal(), (d, which) -> {
                    d.dismiss();
                    PlayerEngineType picked = types[which];
                    if (picked == current) return;
                    setEngine(context, picked);
                    if (listener != null) listener.onChanged(picked);
                })
                .setNegativeButton(R.string.mp_cancel, null)
                .show();
    }
}