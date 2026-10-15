package com.aaronmaxlab.maxplayer.player.media3.video;

import android.app.Dialog;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Rational;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.TrackSelectionDialogBuilder;
import androidx.media3.ui.PlayerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.service.VideoPlaybackService;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.AspectRatioUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.PlayerUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Locale;

/**
 * Shared logic for video player screens: window insets, controls, buffer label,
 * MediaController connection, picture-in-picture and back handling.
 *
 * Subclasses provide the layout and call {@link #connectPlayer()} once they know
 * which stream to play.
 */
public abstract class BasePlayerActivity extends AppCompatActivity {

    private static final long BUFFER_UPDATE_INTERVAL_MS = 1000L;

    private final Handler bufferHandler = new Handler(Looper.getMainLooper());

    private ListenableFuture<MediaController> controllerFuture;

    protected MediaController player;
    protected PlayerView playerView;
    protected ImageView playPauseBtn;

    private TextView bufferSize;
    private TextView bannerTitle;
    private TextView bannerSubtext;
    private TextView bannerIndicator;

    private String currentUrl;

    private final ActivityResultLauncher<String[]> subtitlePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onSubtitlePicked);

    private int aspectMode = 0;
    private boolean isFullscreen = false;
    private boolean wasInPipMode = false;

    private final Runnable bufferUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && bufferSize != null) {
                bufferSize.setText(getString(
                        R.string.player_buffer, player.getBufferedPercentage()));
            }
            bufferHandler.postDelayed(this, BUFFER_UPDATE_INTERVAL_MS);
        }
    };

    // ---------------------------------------------------------------------
    // Hooks for subclasses
    // ---------------------------------------------------------------------

    /** Inflate the subclass binding and return its root view. */
    @NonNull
    protected abstract View inflateContent();

    /** The PlayerView of the inflated layout. */
    @NonNull
    protected abstract PlayerView getBoundPlayerView();

    /** Called once the controller is connected and attached to the PlayerView. */
    protected abstract void onPlayerConnected();

    /** Default: no playlist, so the drawer button is hidden. */
    protected void onSetupPlaylistDrawer(@NonNull LinearLayout drawer) {
        drawer.setVisibility(View.INVISIBLE);
    }

    protected void onPlayingChanged(boolean isPlaying) {
    }

    protected void onPlaybackError(@NonNull PlaybackException error) {
        AppToast.error(this,
                error.getMessage() != null ? error.getMessage() : "Playback failed");
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    @OptIn(markerClass = UnstableApi.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(inflateContent());

        UIUtils.setLightNavigationBars(this, false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        playerView = getBoundPlayerView();
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setKeepContentOnPlayerReset(false);

        playPauseBtn = playerView.findViewById(R.id.play_icon);
        bufferSize = playerView.findViewById(R.id.bufferSize);
        bannerTitle = playerView.findViewById(R.id.playerTopLabel);
        bannerSubtext = playerView.findViewById(R.id.playerSubLabel);
        bannerIndicator = playerView.findViewById(R.id.playerIndicator); // use your real id

        setupControls();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        bufferHandler.removeCallbacks(bufferUpdateRunnable);
        bufferHandler.post(bufferUpdateRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        UIUtils.setLightNavigationBars(this, false);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (canEnterPip()) {
            enterPipMode();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        bufferHandler.removeCallbacks(bufferUpdateRunnable);

        // PiP window was closed by the user: stop playback and leave.
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

        // The service player outlives this activity, so wipe its old item/error
        // when the user really leaves (not on rotation).
        if (player != null && isFinishing() && !isChangingConfigurations()) {
            player.stop();
            player.clearMediaItems();
        }

        if (playerView != null) {
            playerView.setPlayer(null);
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
        player = null;
        super.onDestroy();
    }

    // ---------------------------------------------------------------------
    // Controls
    // ---------------------------------------------------------------------

    private void setupControls() {
        LinearLayout aspectRatio = playerView.findViewById(R.id.aspect_radio);
        LinearLayout playlistDrawer = playerView.findViewById(R.id.playlistDrawer);
        LinearLayout fullscreen = playerView.findViewById(R.id.fullscreen_enter_icon);
        ImageView fullscreenIcon = playerView.findViewById(R.id.fullscreen_icon);
        ImageView backBtn = playerView.findViewById(R.id.backPlayerView);
        TextView exitText = playerView.findViewById(R.id.exit_fullscreen);

        ClickUtils.applyTouchEffect(playPauseBtn, v -> {
            if (player != null) {
                Util.handlePlayPauseButtonAction(player);
            }
        });

        ClickUtils.applyTouchEffect(aspectRatio, v ->
                AspectRatioUtils.showMenu(this, playerView, aspectMode, index -> aspectMode = index));

        ClickUtils.applyTouchEffect(fullscreen, v -> {
            isFullscreen = !isFullscreen;
            PlayerUtils.toggleFullscreen(this, isFullscreen);
            exitText.setText(getText(isFullscreen
                    ? R.string.player_ctl_exit_fullscreen
                    : R.string.player_ctl_fullscreen));
            fullscreenIcon.setImageResource(isFullscreen
                    ? R.drawable.ic_close_fullscreen
                    : R.drawable.ic_expand);
        });

        ClickUtils.applyTouchEffect(backBtn, v -> handleBackPress());

        View subtitleBtn = playerView.findViewById(R.id.subtitle_btn);
        if (subtitleBtn != null) {
            ClickUtils.applyTouchEffect(subtitleBtn, v -> showSubtitleMenu());
        }

        onSetupPlaylistDrawer(playlistDrawer);
    }

    protected final void setBanner(CharSequence title, CharSequence subtitle) {
        if (bannerTitle != null) bannerTitle.setText(title);
        if (bannerSubtext != null) bannerSubtext.setText(subtitle);
        if (bannerIndicator != null) { // default until the stream is ready
            bannerIndicator.setText("LIVE");
            bannerIndicator.setBackgroundResource(R.drawable.bg_badge_live);
        }
    }

    private void updateIndicator() {
        if (bannerIndicator == null || player == null
                || player.getPlaybackState() != Player.STATE_READY) return;

        boolean isLive = player.isCurrentMediaItemLive()
                || player.getDuration() == C.TIME_UNSET;

        bannerIndicator.setText(isLive ? "LIVE" : "VIDEO");
        bannerIndicator.setBackgroundResource(
                isLive ? R.drawable.bg_badge_live : R.drawable.bg_badge_video);
    }

    // ---------------------------------------------------------------------
    // Player service
    // ---------------------------------------------------------------------

    @OptIn(markerClass = UnstableApi.class)
    protected final void connectPlayer() {
        SessionToken token = new SessionToken(
                this, new ComponentName(this, VideoPlaybackService.class));

        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(this::onControllerReady,
                ContextCompat.getMainExecutor(this));
    }

    private void onControllerReady() {
        if (isFinishing() || isDestroyed() || controllerFuture == null) return;

        try {
            player = controllerFuture.get();
        } catch (Exception e) {
            AppToast.error(this, "Unable to connect to video player");
            finish();
            return;
        }

        playerView.setPlayer(player);
        updateIndicator();
        playPauseBtn.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);

        // Add the listener only after the controller is connected.
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                playPauseBtn.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                BasePlayerActivity.this.onPlayingChanged(isPlaying);
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                BasePlayerActivity.this.onPlaybackError(error);
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) updateIndicator();
            }

            @Override
            public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
                updateIndicator();
            }
        });

        onPlayerConnected();
    }

    protected final void playStream(String url) {
        if (player == null || TextUtils.isEmpty(url)) return;
        currentUrl = url;

        player.setMediaItem(new MediaItem.Builder()
                .setMediaId(url) // survives IPC, used by ensureStreamPlaying()
                .setUri(url)
                .build());
        player.prepare();
        player.play();
    }

    /**
     * The service player outlives the activity: replace a stale, different or
     * failed item, but keep the current one (e.g. after rotation).
     */
    protected final void ensureStreamPlaying(String url) {
        if (player == null || TextUtils.isEmpty(url)) return;
        currentUrl = url;

        MediaItem current = player.getCurrentMediaItem();
        boolean healthy = current != null
                && url.equals(current.mediaId)
                && player.getPlayerError() == null;

        if (!healthy) {
            playStream(url);
        }
    }

    // ---------------------------------------------------------------------
    // Subtitles
    // ---------------------------------------------------------------------

    @OptIn(markerClass = UnstableApi.class)
    private void showSubtitleMenu() {
        if (player == null) return;

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.PlayerDialogTheme)
                .setTitle("Subtitles")
                .setItems(new String[]{"Subtitle tracks", "Load from device"}, (d, which) -> {
                    if (which == 0) {
                        if (!player.getCurrentTracks().containsType(C.TRACK_TYPE_TEXT)) {
                            AppToast.warning(this, "No subtitle tracks in this stream");
                            return;
                        }
                        Dialog trackDialog = new TrackSelectionDialogBuilder(
                                this, "Subtitle tracks", player, C.TRACK_TYPE_TEXT)
                                .setTheme(R.style.PlayerDialogTheme)
                                .setShowDisableOption(true)
                                .build();
                        trackDialog.show();
                        accentTitle(trackDialog);
                    } else {
                        subtitlePicker.launch(new String[]{"*/*"});
                    }
                })
                .create();

        dialog.show();
        accentTitle(dialog);
    }

    private void accentTitle(Dialog dialog) {
        TextView title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        if (title != null) {
            title.setTextColor(ContextCompat.getColor(this, R.color.accent));
        }
    }

    private void onSubtitlePicked(@Nullable Uri uri) {
        if (uri == null || player == null || currentUrl == null) return;

        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // Not every provider offers a persistable grant; the current one still works.
        }

        String name = queryDisplayName(uri);
        MediaItem.SubtitleConfiguration subtitle =
                new MediaItem.SubtitleConfiguration.Builder(uri)
                        .setMimeType(subtitleMimeType(name))
                        .setLabel(name)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build();

        long position = player.isCurrentMediaItemLive() ? C.TIME_UNSET : player.getCurrentPosition();
        boolean playWhenReady = player.getPlayWhenReady();

        player.setMediaItem(new MediaItem.Builder()
                .setMediaId(currentUrl)
                .setUri(currentUrl)
                .setSubtitleConfigurations(ImmutableList.of(subtitle))
                .build(), position);
        player.prepare();
        player.setPlayWhenReady(playWhenReady);

        AppToast.success(this, "Subtitle added");
    }

    @Nullable
    private String queryDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return uri.getLastPathSegment();
    }

    private static String subtitleMimeType(@Nullable String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".vtt")) return MimeTypes.TEXT_VTT;
        if (n.endsWith(".ass") || n.endsWith(".ssa")) return MimeTypes.TEXT_SSA;
        if (n.endsWith(".ttml") || n.endsWith(".xml") || n.endsWith(".dfxp")) return MimeTypes.APPLICATION_TTML;
        return MimeTypes.APPLICATION_SUBRIP; // .srt and anything unknown
    }

    // ---------------------------------------------------------------------
    // Picture-in-picture / back handling
    // ---------------------------------------------------------------------

    private boolean canEnterPip() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && player != null
                && player.isPlaying()
                && !isInPictureInPictureMode();
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        try {
            enterPictureInPictureMode(new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9))
                    .build());
        } catch (IllegalStateException e) {
            // PiP not supported/allowed on this device or window state.
            finish();
        }
    }

    private void handleBackPress() {
        if (canEnterPip()) {
            enterPipMode();
        } else {
            finish();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(
            boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);

        if (isInPictureInPictureMode) {
            wasInPipMode = true;
        } else if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            // User expanded the PiP window back to the activity: not a close.
            wasInPipMode = false;
        }
    }

    protected final int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}