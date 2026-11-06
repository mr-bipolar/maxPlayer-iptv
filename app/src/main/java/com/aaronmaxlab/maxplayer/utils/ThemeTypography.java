package com.aaronmaxlab.maxplayer.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.FontRes;
import androidx.core.content.res.ResourcesCompat;

import com.aaronmaxlab.maxplayer.R;

/** Typefaces for the Primary theme with the previous system faces retained in Dark mode. */
public final class ThemeTypography {
    private ThemeTypography() { }

    @NonNull
    public static Typeface regular(@NonNull Context context) {
        return resolve(context, R.font.poppins_regular, "sans-serif", Typeface.NORMAL);
    }

    @NonNull
    public static Typeface medium(@NonNull Context context) {
        return resolve(context, R.font.poppins_medium, "sans-serif-medium", Typeface.NORMAL);
    }

    @NonNull
    public static Typeface bold(@NonNull Context context) {
        return resolve(context, R.font.poppins_bold, "sans-serif", Typeface.BOLD);
    }

    @NonNull
    private static Typeface resolve(@NonNull Context context, @FontRes int font,
                                    @NonNull String darkFallback, int style) {
        int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (mode == Configuration.UI_MODE_NIGHT_YES) {
            return Typeface.create(darkFallback, style);
        }
        Typeface loaded = ResourcesCompat.getFont(context, font);
        return loaded == null ? Typeface.create(darkFallback, style) : loaded;
    }
}
