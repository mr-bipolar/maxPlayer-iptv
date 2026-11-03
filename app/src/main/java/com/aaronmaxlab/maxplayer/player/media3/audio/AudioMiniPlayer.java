package com.aaronmaxlab.maxplayer.player.media3.audio;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.service.PlaybackService;
import com.aaronmaxlab.maxplayer.utils.AudioUtils;
import com.google.common.util.concurrent.ListenableFuture;

public class AudioMiniPlayer {

    private final Activity activity;
    private final View overlay;

    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;
    private Player.Listener playerListener;

    private final TextView stationName;
    private final TextView status;
    private final ImageView playPause;
    private final ImageView expand;

    public AudioMiniPlayer(Activity activity, View overlay) {
        this.activity = activity;
        this.overlay = overlay;

        stationName = overlay.findViewById(R.id.miniPlayerStationName);
        status = overlay.findViewById(R.id.miniPlayerStatus);
        playPause = overlay.findViewById(R.id.miniPlayerPlayPause);
        expand = overlay.findViewById(R.id.miniPlayerExpand);

        overlay.setVisibility(View.GONE);
    }

    public void attach() {
        SessionToken token = new SessionToken(
                activity,
                new ComponentName(activity, PlaybackService.class)
        );

        controllerFuture = new MediaController.Builder(activity, token)
                .buildAsync();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            controllerFuture.addListener(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    MediaController.releaseFuture(controllerFuture);
                    return;
                }

                try {
                    mediaController = controllerFuture.get();
                    setupControls();
                    update();
                } catch (Exception e) {
                    e.printStackTrace();
                    overlay.setVisibility(View.GONE);
                }
            }, activity.getMainExecutor());
        }
    }

    private void setupControls() {
        playerListener = new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                update();
            }

            @Override
            public void onMediaItemTransition(
                    @Nullable MediaItem mediaItem,
                    int reason
            ) {
                update();
            }

            @Override
            public void onMediaMetadataChanged(MediaMetadata metadata) {
                update();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                update();
            }
        };

        mediaController.addListener(playerListener);

        playPause.setOnClickListener(v -> {
            if (mediaController == null) return;

            if (mediaController.isPlaying()) {
                mediaController.pause();
            } else {
                AudioUtils.setAudioStopped(activity, false);

                if (mediaController.getPlaybackState() == Player.STATE_IDLE) {
                    mediaController.prepare();
                }

                mediaController.play();
            }
        });

        expand.setOnClickListener(v -> openPlayer());
    }

    private void update() {


        if (mediaController == null) {
            overlay.setVisibility(View.GONE);
            return;
        }

        MediaItem item = mediaController.getCurrentMediaItem();
        boolean stopped = AudioUtils.isAudioStopped(activity);


        if (item == null || stopped) {
            overlay.setVisibility(View.GONE);
            return;
        }

        overlay.setVisibility(View.VISIBLE);

        CharSequence title = mediaController.getMediaMetadata().title;

        if (title != null && !title.toString().trim().isEmpty()) {
            stationName.setText(title);
        } else {
            stationName.setText(R.string.audio_station_name);
        }

        boolean isPlaying = mediaController.isPlaying();

        playPause.setImageResource(
                isPlaying ? R.drawable.ic_pause : R.drawable.ic_play
        );

        playPause.setContentDescription(
                activity.getString(
                        isPlaying ? R.string.cd_pause : R.string.cd_play
                )
        );

        status.setText(
                isPlaying
                        ? R.string.audio_status_playing
                        : R.string.audio_status_stop
        );
    }

    private void openPlayer() {
        if (mediaController == null) return;

        MediaItem item = mediaController.getCurrentMediaItem();

        if (item == null || item.localConfiguration == null) return;

        Intent intent = new Intent(activity, AudioPlayerActivity.class);
        intent.putExtra(
                "stream_uri",
                item.localConfiguration.uri.toString()
        );

        CharSequence title = mediaController.getMediaMetadata().title;
        intent.putExtra("stream_name", title != null ? title.toString() : "");

        activity.startActivity(intent);
    }

    public void release() {
        if (mediaController != null && playerListener != null) {
            mediaController.removeListener(playerListener);
            playerListener = null;
        }

        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }

        mediaController = null;
    }
}
