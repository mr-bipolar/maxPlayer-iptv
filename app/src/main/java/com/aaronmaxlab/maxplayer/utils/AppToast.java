
package com.aaronmaxlab.maxplayer.utils;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;

import com.aaronmaxlab.maxplayer.R;

public final class AppToast {

    private static final Handler HANDLER =
            new Handler(Looper.getMainLooper());

    private static Toast currentToast;

    private AppToast() {
    }

    public static void success(Activity activity, String message) {
        show(activity, "Success", message,
                R.drawable.ic_check,
                Color.rgb(57, 201, 138));
    }

    public static void error(Activity activity, String message) {
        show(activity, "Error", message,
                R.drawable.ic_alert,
                Color.rgb(241, 108, 108));
    }

    public static void info(Activity activity, String message) {
        show(activity, "Information", message,
                R.drawable.ic_info,
                Color.rgb(109, 184, 255));
    }

    public static void warning(Activity activity, String message) {
        show(activity, "Warning", message,
                R.drawable.ic_alert,
                Color.rgb(232, 184, 91));
    }

    private static void show(
            Activity activity,
            String title,
            String message,
            int iconRes,
            @ColorInt int accentColor) {

        if (activity == null || activity.isFinishing()
                || activity.isDestroyed()) {
            return;
        }

        HANDLER.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }

            View view = activity.getLayoutInflater()
                    .inflate(R.layout.toast_stream_error, null);

            TextView titleView = view.findViewById(R.id.toastTitle);
            TextView messageView =
                    view.findViewById(R.id.toastErrorMessage);
            ImageView iconView = view.findViewById(R.id.toastIcon);
            View iconBackground = (View) iconView.getParent();

            titleView.setText(title);
            messageView.setText(message);
            iconView.setImageResource(iconRes);
            iconView.setColorFilter(accentColor);

            GradientDrawable iconShape = new GradientDrawable();
            iconShape.setShape(GradientDrawable.OVAL);
            iconShape.setColor(Color.argb(
                    35,
                    Color.red(accentColor),
                    Color.green(accentColor),
                    Color.blue(accentColor)
            ));
            iconBackground.setBackground(iconShape);

            if (currentToast != null) {
                currentToast.cancel();
            }

            currentToast = new Toast(activity.getApplicationContext());
            currentToast.setDuration(Toast.LENGTH_LONG);
            currentToast.setGravity(
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                    0,
                    dp(activity, 100)
            );
            currentToast.setView(view);
            currentToast.show();
        });
    }

    private static int dp(Activity activity, int value) {
        return (int) (value
                * activity.getResources()
                .getDisplayMetrics().density + 0.5f);
    }
}
