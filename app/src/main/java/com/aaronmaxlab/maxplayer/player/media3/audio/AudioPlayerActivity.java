package com.aaronmaxlab.maxplayer.player.media3.audio;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.ActivityAudioPlayerBinding;
import com.aaronmaxlab.maxplayer.service.PlaybackService;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.AudioUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.google.common.util.concurrent.ListenableFuture;

public class AudioPlayerActivity extends AppCompatActivity {

    private static final long ARTWORK_ROTATION_MS = 8000L;

    private ActivityAudioPlayerBinding binding;
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private ObjectAnimator artworkRotation;

    private TextView streamLabel;
    private TextView stationName;
    private View indicator;
    private ImageView playPauseBtn;

    private String streamChannelName;

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityAudioPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        streamLabel = binding.fmStreamLabel;
        indicator = binding.statusIndicator;
        playPauseBtn = binding.playPauseBtn;
        stationName = binding.audioStationName;

        setupArtworkRotation();
        setupControls();
        connectToPlaybackService();
    }

    @Override
    protected void onDestroy() {
        if (artworkRotation != null) {
            artworkRotation.cancel();
            artworkRotation = null;
        }
        if (player != null) {
            player.removeListener(playerListener);
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
        player = null;
        binding = null;
        super.onDestroy();
    }

    // ---------------------------------------------------------------------
    // Controls
    // ---------------------------------------------------------------------

    private void setupControls() {
        // Back press uses the default behavior (finish), no custom callback needed.
        ClickUtils.applyTouchEffect(binding.backAudioStreamBtn, v -> finish());

        // The icon is updated by playerListener.onIsPlayingChanged.
        ClickUtils.applyTouchEffect(playPauseBtn, v -> {
            if (player == null) return;

            if (player.isPlaying()) {
                player.pause();
                return;
            }

            AudioUtils.setAudioStopped(this, false);
            if (player.getPlaybackState() == Player.STATE_IDLE) {
                player.prepare();
            }
            player.play();
        });

        ClickUtils.applyTouchEffect(binding.audioStopBtn, v -> {
            if (player == null) return;

            player.pause();
            player.seekTo(0);
            AudioUtils.setAudioStopped(this, true);
        });
    }

    // ---------------------------------------------------------------------
    // Artwork rotation
    // ---------------------------------------------------------------------

    private void setupArtworkRotation() {
        artworkRotation = ObjectAnimator.ofFloat(binding.audioArtwork, View.ROTATION, 0f, 360f);
        artworkRotation.setDuration(ARTWORK_ROTATION_MS);
        artworkRotation.setInterpolator(new LinearInterpolator());
        artworkRotation.setRepeatCount(ValueAnimator.INFINITE);
    }

    private void startArtworkRotation() {
        if (artworkRotation != null && !artworkRotation.isRunning()) {
            artworkRotation.start();
        }
    }

    private void stopArtworkRotation() {
        if (artworkRotation != null) {
            artworkRotation.cancel();
            binding.audioArtwork.setRotation(0f);
        }
    }

    // ---------------------------------------------------------------------
    // Player service
    // ---------------------------------------------------------------------

    private void connectToPlaybackService() {
        SessionToken token = new SessionToken(
                this, new ComponentName(this, PlaybackService.class));

        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(this::onControllerReady,
                ContextCompat.getMainExecutor(this));
    }

    private void onControllerReady() {
        if (isFinishing() || isDestroyed() || controllerFuture == null) return;

        try {
            player = controllerFuture.get();
        } catch (Exception e) {
            AppToast.error(this, "Unable to connect to audio player");
            finish();
            return;
        }

        initializePlayer();
    }

    private void initializePlayer() {
        String streamUri = getIntent().getStringExtra("stream_uri");
        if (TextUtils.isEmpty(streamUri)) {
            AppToast.warning(this, "Stream URL not found");
            finish();
            return;
        }

        String streamName = getIntent().getStringExtra("stream_name");
        streamChannelName = TextUtils.isEmpty(streamName)
                ? UIUtils.extractLabel(streamUri)
                : streamName;
        stationName.setText(streamChannelName);

        player.addListener(playerListener);

        // The service player outlives the activity. Only (re)start when this
        // stream isn't already playing cleanly (e.g. rotation keeps playing).
        if (!isStreamPlaying(streamUri)) {
            AudioUtils.setAudioStopped(this, false);

            player.setMediaItem(new MediaItem.Builder()
                    .setMediaId(streamUri) // survives IPC, used for the check above
                    .setUri(streamUri)
                    .setMediaMetadata(new MediaMetadata.Builder()
                            // Don't set title: MediaItem metadata overrides the
                            // stream's live (ICY) title. Use artist/station instead.
                            .setArtist(streamChannelName)
                            .setStation(streamChannelName)
                            .build())
                    .build());
            player.prepare();
            player.play();
        } else {
            // Already playing (rotation / reopen): no metadata event will fire
            // until the next change, so read the current one now.
            updateTitle(player.getMediaMetadata());
        }

        applyPlayingState(player.isPlaying());
    }

    private boolean isStreamPlaying(String streamUri) {
        MediaItem current = player.getCurrentMediaItem();
        return current != null
                && streamUri.equals(current.mediaId)
                && player.getPlayerError() == null
                && player.isPlaying();
    }

    // ---------------------------------------------------------------------
    // UI state
    // ---------------------------------------------------------------------

    private void applyPlayingState(boolean isPlaying) {
        if (binding == null) return;

        if (isPlaying) {
            startArtworkRotation();
        } else {
            stopArtworkRotation();
        }

        playPauseBtn.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        indicator.setBackgroundResource(isPlaying ? R.drawable.bg_circle_ok : R.drawable.bg_circle_live);
        streamLabel.setText(isPlaying ? R.string.audio_status_playing : R.string.audio_status_stop);
        streamLabel.setTextColor(ContextCompat.getColor(this, isPlaying ? R.color.ok : R.color.live));
    }

    private void updateTitle(@NonNull MediaMetadata metadata) {
        if (binding == null) return;

        CharSequence title = firstNonBlank(metadata.title, metadata.displayTitle);
        stationName.setText(title != null ? title : streamChannelName);
    }

    private static CharSequence firstNonBlank(CharSequence... values) {
        for (CharSequence value : values) {
            if (value != null && !value.toString().trim().isEmpty()) return value;
        }
        return null;
    }

    private void updateBitrate(int bitrateKbps) {
        if (binding == null) return;

        if (bitrateKbps > 0) {
            binding.bitrateLabel.setText(bitrateKbps + " kbps");
            binding.bitrateLabel.setVisibility(View.VISIBLE);
        } else {
            binding.bitrateLabel.setVisibility(View.GONE);
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private static int findAudioBitrateKbps(Tracks tracks) {
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;

            for (int i = 0; i < group.length; i++) {
                Format format = group.getTrackFormat(i);
                if (format.bitrate > 0) {
                    return format.bitrate / 1000;
                }
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------------
    // Player listener
    // ---------------------------------------------------------------------

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            applyPlayingState(isPlaying);
        }

        @Override
        public void onMediaMetadataChanged(@NonNull MediaMetadata metadata) {
            updateTitle(metadata);
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            updateBitrate(findAudioBitrateKbps(tracks));
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            AppToast.error(AudioPlayerActivity.this,
                    error.getMessage() != null ? error.getMessage() : "Playback failed");
        }
    };
}