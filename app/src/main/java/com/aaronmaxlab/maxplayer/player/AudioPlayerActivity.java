package com.aaronmaxlab.maxplayer.player;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
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
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.ActivityAudioPlayerBinding;
import com.aaronmaxlab.maxplayer.service.PlaybackService;
import com.aaronmaxlab.maxplayer.utils.AudioUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import android.content.ComponentName;

import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

public class AudioPlayerActivity extends AppCompatActivity {

    private ActivityAudioPlayerBinding binding;
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private TextView streamLabel;
    private View indicator ;
    private ImageView playPauseBtn ;
    private String streamChannelName ;
    private  TextView stationName;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityAudioPlayerBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

         streamLabel = binding.fmStreamLabel;
         indicator = binding.statusIndicator;
         playPauseBtn = binding.playPauseBtn;
         stationName = binding.audioStationName;

        connectToPlaybackService();
        setupArtworkRotation();


        ClickUtils.applyTouchEffect(playPauseBtn, v -> {

            if (player == null) return;

            if (player.isPlaying()) {
                player.pause();
            } else {
                AudioUtils.setAudioStopped(this, false);

                if (player.getPlaybackState() == Player.STATE_IDLE) {
                    player.prepare();
                }

                player.play();
            }

            boolean shouldShowPlayButton =
                    Util.shouldShowPlayButton(player);

            playPauseBtn.setImageResource(
                    shouldShowPlayButton
                            ? R.drawable.ic_play
                            : R.drawable.ic_pause
            );
        });

        ClickUtils.applyTouchEffect(binding.backAudioStreamBtn, v -> {
            finish();
        });

        ClickUtils.applyTouchEffect(binding.audioStopBtn, v -> {
            if (player != null) {
                player.pause();
                player.seekTo(0);
                AudioUtils.setAudioStopped(this, true);
            }
        });

        // back press
        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        finish();
                    }
                });

    }

    private void updateBitrate(int bitrate) {
        if (bitrate > 0) {
            binding.bitrateLabel.setText(bitrate + " kbps");
            binding.bitrateLabel.setVisibility(View.VISIBLE);
        } else {
            binding.bitrateLabel.setVisibility(View.GONE);
        }
    }


    private ObjectAnimator artworkRotation;

    private void setupArtworkRotation() {
        artworkRotation = ObjectAnimator.ofFloat(
                binding.audioArtwork,
                View.ROTATION,
                0f,
                360f
        );

        artworkRotation.setDuration(8000);
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

    private void initializePlayer() {
        String streamUri = getIntent().getStringExtra("stream_uri");
        String streamName = getIntent().getStringExtra("stream_name");

        if (streamUri == null || streamUri.isEmpty()) {
            return;
        }

        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(streamChannelName)
                .build();

        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(streamUri)
                .setMediaMetadata(metadata)
                .build();

        AudioUtils.setAudioStopped(this, false);

        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();

        if (streamName == null || streamName.isEmpty()) {
            streamChannelName = UIUtils.extractLabel(streamUri);
        }else{
            streamChannelName = streamName;
        }

        stationName.setText(streamChannelName);


        player.addListener(new Player.Listener() {

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {

                if (isPlaying) {
                    startArtworkRotation();
                    playPauseBtn.setImageResource( R.drawable.ic_pause);
                    String label =  getString(R.string.audio_status_playing);
                    indicator.setBackgroundResource(R.drawable.bg_circle_ok);
                    streamLabel.setText(label);
                    streamLabel.setTextColor(
                            ContextCompat.getColor(getApplicationContext(), R.color.ok)
                    );
                } else {
                    stopArtworkRotation();
                    playPauseBtn.setImageResource(R.drawable.ic_play);
                    String label =  getString(R.string.audio_status_stop);
                    indicator.setBackgroundResource(R.drawable.bg_circle_live);
                    streamLabel.setText(label);
                    streamLabel.setTextColor(
                            ContextCompat.getColor(getApplicationContext(), R.color.live)
                    );
                }
            }

            @Override
            public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
                CharSequence title = mediaMetadata.title;

                if (title != null && !title.toString().trim().isEmpty()) {
                    stationName.setText(title);
                } else {
                    stationName.setText(streamChannelName);
                }
            }


            @OptIn(markerClass = UnstableApi.class)
            @Override
            public void onTracksChanged(Tracks tracks) {

                int bitrate = -1;

                for (Tracks.Group group : tracks.getGroups()) {
                    if (group.getType() != C.TRACK_TYPE_AUDIO) {
                        continue;
                    }

                    for (int i = 0; i < group.length; i++) {
                        Format format = group.getTrackFormat(i);

                        if (format.bitrate > 0) {
                            bitrate = format.bitrate / 1000;
                            break;
                        }
                    }

                    if (bitrate > 0) {
                        break;
                    }
                }

                updateBitrate(bitrate);
            }
        });



    }

    private void connectToPlaybackService() {
        SessionToken sessionToken = new SessionToken(
                this,
                new ComponentName(this, PlaybackService.class)
        );

        controllerFuture = new MediaController.Builder(this, sessionToken)
                .buildAsync();

        controllerFuture.addListener(() -> {
            try {
                player = controllerFuture.get();
                initializePlayer();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }


    @Override
    protected void onDestroy() {
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }

        player = null;
        binding = null;

        super.onDestroy();
    }
}