package com.aaronmaxlab.maxplayer.player.media3.audio;

import android.content.ComponentName;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.aaronmaxlab.maxplayer.PlayAudioStreamActivity;
import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.service.PlaybackService;
import com.aaronmaxlab.maxplayer.utils.AudioUtils;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Mini player overlay. Connects to the playback service while the host activity
 * is started and disconnects when it stops. Call {@link #attach()} once;
 * {@link #release()} is optional and safe to call more than once.
 */
public class AudioMiniPlayer implements DefaultLifecycleObserver {

    private final ComponentActivity activity;
    private final View overlay;
    private final TextView stationName;
    private final TextView status;
    private final ImageView playPause;

    private final String textPlaying;
    private final String textStopped;
    private final String descPause;
    private final String descPlay;

    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private Boolean lastPlaying; // null until first render

    public AudioMiniPlayer(@NonNull ComponentActivity activity, @NonNull View overlay) {
        this.activity = activity;
        this.overlay = overlay;

        stationName = overlay.findViewById(R.id.miniPlayerStationName);
        status = overlay.findViewById(R.id.miniPlayerStatus);
        playPause = overlay.findViewById(R.id.miniPlayerPlayPause);
        ImageView expand = overlay.findViewById(R.id.miniPlayerExpand);

        // resolve strings once instead of on every update
        textPlaying = activity.getString(R.string.audio_status_playing);
        textStopped = activity.getString(R.string.audio_status_stop);
        descPause = activity.getString(R.string.cd_pause);
        descPlay = activity.getString(R.string.cd_play);

        playPause.setOnClickListener(v -> togglePlayback());
        expand.setOnClickListener(v -> openPlayer());

        overlay.setVisibility(View.GONE);
    }


    // Lifecycle
    public void attach() {
        activity.getLifecycle().addObserver(this);
    }

    public void release() {
        disconnect();
        activity.getLifecycle().removeObserver(this);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        connect();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        disconnect();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        release();
    }


    // Connection
    private void connect() {
        if (controllerFuture != null) return;

        SessionToken token = new SessionToken(
                activity, new ComponentName(activity, PlaybackService.class));

        ListenableFuture<MediaController> future =
                new MediaController.Builder(activity, token).buildAsync();
        controllerFuture = future;
        future.addListener(() -> onConnected(future), ContextCompat.getMainExecutor(activity));
    }

    private void onConnected(@NonNull ListenableFuture<MediaController> future) {
        if (future != controllerFuture) return; // released while connecting

        try {
            controller = future.get();
        } catch (Exception e) {
            overlay.setVisibility(View.GONE);
            return;
        }

        controller.addListener(playerListener);
        update(); // fresh state on every (re)connect
    }

    private void disconnect() {
        if (controller != null) {
            controller.removeListener(playerListener);
            controller = null;
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
    }


    // Actions
    private void togglePlayback() {
        MediaController c = controller;
        if (c == null) return;

        if (c.isPlaying()) {
            c.pause();
            return;
        }

        AudioUtils.setAudioStopped(activity, false);
        if (c.getPlaybackState() == Player.STATE_IDLE) {
            c.prepare();
        }
        c.play();
    }

    private void openPlayer() {
        MediaController c = controller;
        if (c == null) return;

        MediaItem item = c.getCurrentMediaItem();
        if (item == null) return;

        /** localConfiguration can be stripped over IPC;
          * the full player sets mediaId = stream URI
         */
        String uri = item.localConfiguration != null
                ? item.localConfiguration.uri.toString()
                : item.mediaId;
        if (TextUtils.isEmpty(uri)) return;

        MediaMetadata md = c.getMediaMetadata();
        CharSequence station = AudioPlayerActivity.firstNonBlank(md.station, md.artist);

        activity.startActivity(new Intent(activity, AudioPlayerActivity.class)
                .putExtra(PlayAudioStreamActivity.EXTRA_STREAM_URI, uri)
                .putExtra(PlayAudioStreamActivity.EXTRA_STREAM_NAME,
                        station != null ? station.toString() : ""));
    }


    // UI
    private void update() {
        MediaController c = controller;

        if (c == null || c.getCurrentMediaItem() == null || AudioUtils.isAudioStopped(activity)) {
            overlay.setVisibility(View.GONE);
            return;
        }

        overlay.setVisibility(View.VISIBLE);

        MediaMetadata md = c.getMediaMetadata();
        CharSequence name = AudioPlayerActivity.firstNonBlank(
                md.title, md.displayTitle, md.station, md.artist);
        if (name == null) {
            name = activity.getString(R.string.audio_station_name);
        }
        if (!TextUtils.equals(name, stationName.getText())) {
            stationName.setText(name);
        }

        boolean playing = c.isPlaying();
        if (lastPlaying == null || lastPlaying != playing) {
            lastPlaying = playing;
            playPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            playPause.setContentDescription(playing ? descPause : descPlay);
            status.setText(playing ? textPlaying : textStopped);
        }
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
            // one callback per batch of changes instead of four separate ones
            if (events.containsAny(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                update();
            }
        }
    };
}