package com.aaronmaxlab.maxplayer.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

public class PlaybackService extends MediaSessionService {

    private ExoPlayer player;
    private MediaSession mediaSession;
    private boolean audioStoppedByUser = false;

    @Override
    public void onCreate() {
        super.onCreate();

        player = new ExoPlayer.Builder(this).build();

        mediaSession = new MediaSession.Builder(this, player)
                .setId("audio_session")
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
            player = null;
        }

        super.onDestroy();
    }


    public boolean isAudioStoppedByUser() {
        return audioStoppedByUser;
    }

    public void setAudioStoppedByUser(boolean stopped) {
        audioStoppedByUser = stopped;
    }
}
