package com.aaronmaxlab.maxplayer.utils;

import android.app.Activity;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.aaronmaxlab.maxplayer.R;

public final class AspectRatioUtils {

    private static final String[] LABELS = {
            "Fit", "Fixed width", "Fixed height", "Fill", "Zoom"
    };

    @OptIn(markerClass = UnstableApi.class)
    private static final int[] RESIZE_MODES = {
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    };

    public interface OnModeSelected {
        void onSelected(int index);
    }

    private AspectRatioUtils() {
    }

    /** Shows a single-choice menu; applies the pick and reports its index. */
    public static void showMenu(
            @NonNull Activity activity,
            @NonNull PlayerView playerView,
            int selectedIndex,
            @NonNull OnModeSelected callback
    ) {
        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.PlayerDialogTheme)
                .setTitle("Aspect ratio")
                .setSingleChoiceItems(LABELS, selectedIndex, (d, which) -> {
                    apply(playerView, which);
                    callback.onSelected(which);
                    d.dismiss();
                })
                .create();

        dialog.show();

        TextView title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        if (title != null) {
            title.setTextColor(ContextCompat.getColor(activity, R.color.accent));
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    public static void apply(@NonNull PlayerView playerView, int index) {
        if (index >= 0 && index < RESIZE_MODES.length) {
            playerView.setResizeMode(RESIZE_MODES[index]);
        }
    }
}