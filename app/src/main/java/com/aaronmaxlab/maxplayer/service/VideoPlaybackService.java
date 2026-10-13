
package com.aaronmaxlab.maxplayer.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

@UnstableApi
public class VideoPlaybackService extends MediaSessionService {

    private ExoPlayer player;
    private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();

        player = new ExoPlayer.Builder(getApplicationContext()).build();

        mediaSession = new MediaSession.Builder(this, player)
                .setId("video_session")
                .build();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(
            @NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }

        // MediaSession releases the player it owns.
        player = null;

        super.onDestroy();
    }
}
