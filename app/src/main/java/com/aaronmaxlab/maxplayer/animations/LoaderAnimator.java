package com.aaronmaxlab.maxplayer.animations;


import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;

import com.aaronmaxlab.maxplayer.R;

public final class LoaderAnimator {

    private LoaderAnimator() {
        // Utility class
    }

    public static void start(View view) {
        if (view == null) return;

        view.clearAnimation();

        RotateAnimation rotation = new RotateAnimation(
                0f, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );

        rotation.setDuration(900);
        rotation.setInterpolator(new LinearInterpolator());
        rotation.setRepeatCount(Animation.INFINITE);
        rotation.setRepeatMode(Animation.RESTART);
        rotation.setFillAfter(false);

        view.startAnimation(rotation);
    }

    public static void stop(View view) {
        if (view == null) return;
        view.clearAnimation();
    }

    public static void startLoader(View view) {
        if (view == null) return;

        View loading = view.findViewById(R.id.loadingView);
        if (loading == null) return;

        view.setVisibility(View.VISIBLE);
        view.bringToFront();

        start(loading);
    }

    public static void stopLoader(View view) {
        if (view == null) return;

        View loading = view.findViewById(R.id.loadingView);

        if (loading != null) {
            stop(loading);
        }

        view.setVisibility(View.GONE);
    }
}

