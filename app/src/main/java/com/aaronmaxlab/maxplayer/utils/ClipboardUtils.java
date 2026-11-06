package com.aaronmaxlab.maxplayer.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;


public final class ClipboardUtils {

    private ClipboardUtils() {
        // Prevent object creation
    }

    public static void copyText(Activity activity, String text) {

        if (activity == null || text == null || text.isEmpty()) {
            return;
        }

        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(
                        Context.CLIPBOARD_SERVICE
                );

        if (clipboard == null) {
            return;
        }

        ClipData clip = ClipData.newPlainText("MaxPlayer", text);
        clipboard.setPrimaryClip(clip);

        AppToast.success(activity, "Copied to clipboard");
    }
}
