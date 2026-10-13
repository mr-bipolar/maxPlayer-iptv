package com.aaronmaxlab.maxplayer.player;

import static android.view.View.INVISIBLE;

import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.ActivityUrlPlayerBinding;
import com.aaronmaxlab.maxplayer.service.VideoPlaybackService;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.AspectRatioUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.PlayerUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.google.common.util.concurrent.ListenableFuture;

public class UrlPlayerActivity extends AppCompatActivity {


    private ActivityUrlPlayerBinding binding;
    private MediaController player;
    private PlayerView playerView;
    private TextView bufferSize;
    private int aspectMode = 0;
    private ListenableFuture<MediaController> controllerFuture;
    private boolean wasInPipMode = false;
    private  String streamUrl;
    private boolean isFullscreen = false;

    private final Handler bufferHandler = new Handler(Looper.getMainLooper());

    private final Runnable bufferUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && bufferSize != null) {
                int percentage = player.getBufferedPercentage();

                bufferSize.setText(
                        getString(R.string.player_buffer, percentage)
                );
            }

            bufferHandler.postDelayed(this, 1000);
        }
    };


    @OptIn(markerClass = UnstableApi.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityUrlPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        streamUrl = getIntent().getStringExtra("playlist_uri");
        String fileName = getIntent().getStringExtra("playlist_file_name");

        if( fileName == null || fileName.trim().isEmpty()){
            fileName = getString(R.string.video_channel);
        }

        playerView = binding.urlPlayerView;

        // Update the player labels.
        TextView bannerTitle = playerView.findViewById(R.id.playerTopLabel);
        TextView bannerSubtext = playerView.findViewById(R.id.playerSubLabel);
        ImageView playPauseBtn = playerView.findViewById(R.id.play_icon);

        connectVideoService(playPauseBtn);

        bannerTitle.setText(fileName);
        bannerSubtext.setText(getString(R.string.streaming));

        bufferHandler.removeCallbacks(bufferUpdateRunnable);
        bufferHandler.post(bufferUpdateRunnable);

        LinearLayout aspectRatio = playerView.findViewById(R.id.aspect_radio);
        LinearLayout playlistDrawer = playerView.findViewById(R.id.playlistDrawer);
        ImageView backBtn = playerView.findViewById(R.id.backPlayerView);
        LinearLayout fullscreen = playerView.findViewById(R.id.fullscreen_enter_icon);
        ImageView fullscreenIcon = playerView.findViewById(R.id.fullscreen_icon);
        TextView exitText = playerView.findViewById(R.id.exit_fullscreen);
        bufferSize = playerView.findViewById(R.id.bufferSize);

        // hide playlist drawer
        playlistDrawer.setVisibility(INVISIBLE);


        // play and pause controller
        ClickUtils.applyTouchEffect(playPauseBtn , v -> {
            Util.handlePlayPauseButtonAction(player);
        });

        // back button
        ClickUtils.applyTouchEffect(backBtn, v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                handleBackPress();
            } else {
                closeAndFinish();
            }
        });

        // aspect ratio
        ClickUtils.applyTouchEffect(aspectRatio, v -> {
          aspectMode =  AspectRatioUtils.changeAspectRatio(playerView, aspectMode);
        });

        // fullscreen
        ClickUtils.applyTouchEffect(fullscreen, v ->{
            isFullscreen = !isFullscreen;
            PlayerUtils.toggleFullscreen(this, isFullscreen);
            exitText.setText( getText(isFullscreen ? R.string.player_ctl_exit_fullscreen : R.string.player_ctl_fullscreen));
            fullscreenIcon.setImageResource(isFullscreen ? R.drawable.ic_close_fullscreen : R.drawable.ic_expand);
        });

        // back press
        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            handleBackPress();
                        } else {
                            closeAndFinish();
                        }
                    }
                });

    }

    @OptIn(markerClass = UnstableApi.class)
    private void connectVideoService(ImageView playPauseBtn) {
        SessionToken sessionToken = new SessionToken(
                this,
                new ComponentName(this, VideoPlaybackService.class)
        );

        controllerFuture = new MediaController.Builder(this, sessionToken)
                .buildAsync();

        controllerFuture.addListener(() -> {
            try {
                player = controllerFuture.get();
                playerView.setPlayer(player);

                // Add listener only after the controller is connected.
                player.addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        playPauseBtn.setImageResource(
                                isPlaying
                                        ? R.drawable.ic_pause
                                        : R.drawable.ic_play
                        );
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                        AppToast.error(
                                UrlPlayerActivity.this,
                                error.getMessage()
                        );
                    }
                });

                if (player.getMediaItemCount() == 0
                        && streamUrl != null
                        && !streamUrl.isEmpty()) {

                    player.setMediaItem(MediaItem.fromUri(streamUrl));
                    player.prepare();
                    player.play();
                }

            } catch (Exception e) {
                AppToast.error(this, "Unable to connect to video player");
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }


    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams params =
                    new PictureInPictureParams.Builder()
                            .setAspectRatio(new Rational(16, 9))
                            .build();

            enterPictureInPictureMode(params);
        }
    }

    private void handleBackPress() {
        if (player != null
                && player.isPlaying()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            enterPipMode();

        } else {
            finish();
        }
    }

    private void closeAndFinish() {
        player = null;
        finish();
    }

    @Override
    public void onPictureInPictureModeChanged(
            boolean isInPictureInPictureMode,
            Configuration newConfig) {
        super.onPictureInPictureModeChanged(
                isInPictureInPictureMode, newConfig);

        if (isInPictureInPictureMode) {
            wasInPipMode = true;
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipMode();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        UIUtils.setLightNavigationBars(this, false);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (wasInPipMode && !isChangingConfigurations()) {
            if (player != null) {
                player.pause();
                player.clearMediaItems();
            }

            finish();
        }
    }



    @Override
    protected void onDestroy() {
        bufferHandler.removeCallbacks(bufferUpdateRunnable);
        player = null;
        binding = null;
        super.onDestroy();
    }
}