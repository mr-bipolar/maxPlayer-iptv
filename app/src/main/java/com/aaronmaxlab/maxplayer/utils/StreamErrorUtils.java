package com.aaronmaxlab.maxplayer.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.aaronmaxlab.maxplayer.R;

public final class StreamErrorUtils {

    private StreamErrorUtils() {
        // Utility class
    }

    @Nullable
    private static View findOverlay(Activity activity) {
        return activity.findViewById(R.id.streamErrorOverlay);
    }

    public static void show(Activity activity) {
        View overlay = findOverlay(activity);
        if (overlay == null) return;

        overlay.setVisibility(View.VISIBLE);

        Window window = activity.getWindow();
        window.setNavigationBarColor(Color.BLACK);
        window.setStatusBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }

        View closeButton = overlay.findViewById(R.id.closeErrorBtn);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> overlay.setVisibility(View.GONE));
        }
    }

    public static void hide(Activity activity) {
        View overlay = findOverlay(activity);
        if (overlay != null && overlay.getVisibility() != View.GONE) {
            overlay.setVisibility(View.GONE);
        }
    }

    public static void showCopyButton(Activity activity, @Nullable String streamUrl, @Nullable String info) {
        View overlay = findOverlay(activity);
        if (overlay == null) return;

        View copyInfoOption = overlay.findViewById(R.id.copyInfoButtons);
        View copyBtn = overlay.findViewById(R.id.urlCopyBtn);
        View infoButton = overlay.findViewById(R.id.streamInfo);
        if (copyInfoOption == null || copyBtn == null || infoButton == null) return;

        copyInfoOption.setVisibility(View.VISIBLE);

        ClickUtils.applyTouchEffect(copyBtn, v -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;

            if (isBlank(streamUrl)) {
                AppToast.warning(activity, "Nothing to copy");
                return;
            }
            copyToClipboard(activity, "Stream URL", streamUrl);
        });

        ClickUtils.applyTouchEffect(infoButton, v -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;

            if (isBlank(info)) {
                AppToast.warning(activity, "Nothing to show");
                return;
            }
            showInfoDialog(activity, info);
        });
    }

    private static void showInfoDialog(Activity activity, String info) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Stream Information")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .setNeutralButton("Copy", (d, which) ->
                        copyToClipboard(activity, "Stream Info", info))
                .create();

        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.black);
            }

            int accent = ContextCompat.getColor(activity, R.color.accent);

            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) {
                message.setTextColor(ContextCompat.getColor(activity, android.R.color.white));
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(accent);
        });

        dialog.show(); // was called twice before
    }

    private static void copyToClipboard(Activity activity, String label, String text) {
        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;

        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        AppToast.success(activity, "Copied to clipboard");
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}