package com.aaronmaxlab.maxplayer.utils;

import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;

public class AspectRatioUtils {

    @OptIn(markerClass = UnstableApi.class)
    public static int changeAspectRatio(PlayerView playerView, int aspectMode) {

        aspectMode = (aspectMode % 4) + 1;

        switch (aspectMode) {
            case 1:
                playerView.setResizeMode(
                        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);
                break;

            case 2:
                playerView.setResizeMode(
                        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT);
                break;

            case 3:
                playerView.setResizeMode(
                        AspectRatioFrameLayout.RESIZE_MODE_FILL);
                break;

            case 4:
                playerView.setResizeMode(
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                break;
        }

        return aspectMode;
    }
}