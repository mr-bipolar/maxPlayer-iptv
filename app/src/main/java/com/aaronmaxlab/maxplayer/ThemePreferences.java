package com.aaronmaxlab.maxplayer;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** Stores the app's visual theme and applies it through AppCompat before Activity creation. */
public final class ThemePreferences {
    public static final String SYSTEM = "system";
    public static final String PRIMARY = "primary";
    /** Kept as a source-compatible alias for the previous Primary setting value. */
    public static final String LIGHT = PRIMARY;
    public static final String DARK = "dark";
    private static final String LEGACY_LIGHT = "light";
    private static final String PREFS = "appearance_preferences";
    private static final String KEY_THEME = "selected_theme";

    private ThemePreferences() { }

    public static String getSelected(Context context) {
        String saved = preferences(context).getString(KEY_THEME, PRIMARY);
        if (LEGACY_LIGHT.equals(saved)) return PRIMARY;
        return SYSTEM.equals(saved) || DARK.equals(saved) ? saved : PRIMARY;
    }

    public static void applySaved(Context context) {
        apply(getSelected(context));
    }

    public static void setSelected(Context context, String theme) {
        String safeTheme = SYSTEM.equals(theme) || DARK.equals(theme) ? theme : PRIMARY;
        preferences(context).edit().putString(KEY_THEME, safeTheme).apply();
        apply(safeTheme);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void apply(String theme) {
        int mode = SYSTEM.equals(theme)
                ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                : DARK.equals(theme)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO;
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
