
package com.aaronmaxlab.maxplayer.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.aaronmaxlab.maxplayer.player.PlayerActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StreamLauncher {

    private static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(2);

    private static final Handler MAIN_HANDLER =
            new Handler(Looper.getMainLooper());

    private StreamLauncher() {
    }

    public static void launch(
            Context context,
            String url,
            @Nullable String title,
            @Nullable Boolean live,
            @Nullable Long position
    ) {
        if (context == null ||
                url == null ||
                url.trim().isEmpty()) {
            return;
        }

        Context appContext = context.getApplicationContext();

        EXECUTOR.execute(() -> {

            boolean reachable =
                    UrlUtils.isStreamReachable(url);

            boolean isStream = reachable
                    && UrlUtils.isMediaStreamUrl(url);

            MAIN_HANDLER.post(() -> {

                Intent intent = PlayerActivity.forStream(
                        context,
                        url,
                        title,
                        isStream,
                        position
                );

                context.startActivity(intent);
            });
        });
    }

    public static void launch(
            Context context,
            String url,
            @Nullable String title,
            @Nullable Boolean live
    ) {
        launch(context, url, title, live, null);
    }

    public static void launch(
            Context context,
            String url,
            @Nullable String title
    ) {
        launch(context, url, title, null, null);
    }
}
