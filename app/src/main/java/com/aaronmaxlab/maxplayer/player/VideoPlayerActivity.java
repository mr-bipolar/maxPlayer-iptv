package com.aaronmaxlab.maxplayer.player;

import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Rational;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.ActivityVideoPlayerBinding;
import com.aaronmaxlab.maxplayer.playlist.PlaylistParser;
import com.aaronmaxlab.maxplayer.playlist.adapter.PlaylistAdapter;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistChannel;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistInfo;
import com.aaronmaxlab.maxplayer.service.VideoPlaybackService;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.AspectRatioUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.PlayerUtils;
import com.aaronmaxlab.maxplayer.utils.StreamErrorUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoPlayerActivity extends AppCompatActivity {

    private static final String CATEGORY_ALL = "All";
    private static final long BUFFER_UPDATE_INTERVAL_MS = 1000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler bufferHandler = new Handler(Looper.getMainLooper());

    private ActivityVideoPlayerBinding binding;
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private PlayerView playerView;

    // Views inside the PlayerView controller layout.
    private ImageView playPauseBtn;
    private TextView bufferSize;
    private TextView bannerTitle;
    private TextView bannerSubtext;
    private TextView bannerIndicator;

    private PlaylistInfo playlistModel;
    private String streamUrl;
    private int currentChannelPosition = 0; // index in playlistModel.channels

    private int aspectMode = 0;
    private boolean isFullscreen = false;
    private boolean wasInPipMode = false;

    // Bottom sheet state (reset every time the sheet opens).
    @Nullable private PlaylistAdapter adapter;
    private boolean showSearchBox = false;
    private String selectedCategory = CATEGORY_ALL;
    private String searchQuery = "";

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


    // Lifecycle

    @OptIn(markerClass = UnstableApi.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityVideoPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // System nav color change
        UIUtils.setLightNavigationBars(this, false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // player controller items
        playerView = binding.playerView;
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setKeepContentOnPlayerReset(false);
        playPauseBtn = playerView.findViewById(R.id.play_icon);
        bufferSize = playerView.findViewById(R.id.bufferSize);
        bannerTitle = playerView.findViewById(R.id.playerTopLabel);
        bannerSubtext = playerView.findViewById(R.id.playerSubLabel);
        bannerIndicator = playerView.findViewById(R.id.indicator);

        // Controller setups
        setupControls();

        // System bar back press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });

        // load playlists
        loadPlaylistAsync();
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
        executor.shutdownNow();

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
        adapter = null;
        binding = null;
        super.onDestroy();
    }


    // Controls
    private void setupControls() {
        LinearLayout aspectRatio = playerView.findViewById(R.id.aspect_radio);
        LinearLayout playlistDrawer = playerView.findViewById(R.id.playlistDrawer);
        LinearLayout fullscreen = playerView.findViewById(R.id.fullscreen_enter_icon);
        ImageView fullscreenIcon = playerView.findViewById(R.id.fullscreen_icon);
        ImageView backBtn = playerView.findViewById(R.id.backPlayerView);
        TextView exitText = playerView.findViewById(R.id.exit_fullscreen);

        // play pause video
        ClickUtils.applyTouchEffect(playPauseBtn, v -> {
            if (player != null) {
                Util.handlePlayPauseButtonAction(player);
            }
        });

        // Aspect Ratio
        ClickUtils.applyTouchEffect(aspectRatio, v ->
                aspectMode = AspectRatioUtils.changeAspectRatio(playerView, aspectMode));

        // Fullscreen  switch action
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

        // Video player back press
        ClickUtils.applyTouchEffect(backBtn, v -> handleBackPress());
        // Open bottom sheet
        ClickUtils.applyTouchEffect(playlistDrawer, v -> showChannelsBottomSheet());
    }

    // Video banner setup
    private void updateBanner(@NonNull PlaylistChannel channel) {
        if (bannerTitle != null) bannerTitle.setText(channel.name);
        if (bannerSubtext != null) bannerSubtext.setText(channel.category);
        if (bannerIndicator != null) {
            bannerIndicator.setText("LIVE");
            bannerIndicator.setBackgroundResource(R.drawable.bg_badge_live);
        }
    }

    // Player top bar indicator
    private void updateIndicator() {
        if (bannerIndicator == null || player == null) return;

        boolean isLive = player.isCurrentMediaItemLive()
                || player.getDuration() == C.TIME_UNSET;

        bannerIndicator.setText(isLive ? "LIVE" : "VIDEO");
        bannerIndicator.setBackgroundResource(
                isLive ? R.drawable.bg_badge_live : R.drawable.bg_badge_video);
    }

    // Playlist loading
    private void showParsingStatus(String status) {
        binding.layoutParsing.setVisibility(View.VISIBLE);
        binding.tvParsingStatus.setText(status);
    }

    private void hideParsingStatus() {
        binding.layoutParsing.setVisibility(View.GONE);
    }

    private void failAndFinish(String message) {
        hideParsingStatus();
        AppToast.warning(this, message);
        finish();
    }

    private void loadPlaylistAsync() {
        playlistModel = null;
        streamUrl = null;

        String url = getIntent().getStringExtra("playlist_url");
        if (url == null || url.trim().isEmpty()) {
            failAndFinish("Playlist URL not found");
            return;
        }

        showParsingStatus("Downloading playlist...");
        String fileName = UIUtils.extractLabel(url);

        executor.execute(() -> {
            PlaylistInfo model;
            try {
                model = PlaylistParser.parseRemoteUrl(url, fileName);
            } catch (Exception e) { // IOException and unexpected parser errors
                model = null;
            }

            PlaylistInfo result = model;
            runOnUiThread(() -> onPlaylistLoaded(result));
        });
    }

    private void onPlaylistLoaded(@Nullable PlaylistInfo model) {
        if (isFinishing() || isDestroyed()) return;

        showParsingStatus("Processing channels...");

        if (model == null) {
            failAndFinish("Failed to load playlist");
            return;
        }
        if (model.channels == null || model.channels.isEmpty()) {
            failAndFinish("No channels found in playlist");
            return;
        }

        playlistModel = model;
        currentChannelPosition = 0;

        PlaylistChannel first = model.channels.get(0);
        streamUrl = first.url;
        updateBanner(first);

        connectVideoService();
        hideParsingStatus();
    }


    // Player service
    @OptIn(markerClass = UnstableApi.class)
    private void connectVideoService() {
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
        playPauseBtn.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);

        // Add the listener only after the controller is connected.
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) StreamErrorUtils.hide(VideoPlayerActivity.this);
                playPauseBtn.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                StreamErrorUtils.show(VideoPlayerActivity.this);

                String info = "Error: " + error.getMessage()
                        + "\nError code: " + error.errorCode
                        + "\nError name: " + error.getErrorCodeName()
                        + "\nStream URL: " + streamUrl;

                StreamErrorUtils.showCopyButton(VideoPlayerActivity.this, streamUrl, info);

                AppToast.error(VideoPlayerActivity.this,
                        error.getMessage() != null ? error.getMessage() : "Playback failed");
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

        boolean needsNewItem = player.getMediaItemCount() == 0
                || player.getPlayerError() != null;

        if (needsNewItem && streamUrl != null && !streamUrl.isEmpty()) {
            playStream(streamUrl);
        }
    }

    private void playStream(String url) {
        StreamErrorUtils.hide(this);
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.play();
    }


    // Channels bottom sheet
    private void showChannelsBottomSheet() {
        if (playlistModel == null || playlistModel.channels == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.playlist_drawer);

        RecyclerView rvChannels = dialog.findViewById(R.id.rvChannels);
        if (rvChannels == null) return;

        // Fresh state every time the sheet opens (views are always recreated).
        selectedCategory = CATEGORY_ALL;
        searchQuery = "";
        showSearchBox = false;

        adapter = new PlaylistAdapter();
        rvChannels.setLayoutManager(new LinearLayoutManager(this));
        rvChannels.setAdapter(adapter);
        adapter.setChannels(playlistModel.channels);
        adapter.setPlayingPosition(currentChannelPosition);

        TextView topLabel = dialog.findViewById(R.id.topLabelView);
        if (topLabel != null) {
            topLabel.setText(getString(
                    R.string.drawer_header,
                    UIUtils.extractLabel(playlistModel.fileName).toUpperCase(),
                    playlistModel.channelCount));
        }

        setupCategoryList(dialog.findViewById(R.id.categoryList));
        setupSearch(dialog);

        adapter.setOnChannelClickListener((channel, position) -> onChannelSelected(channel, dialog));

        dialog.setOnShowListener(d -> styleSheetNavigationBar(dialog));
        dialog.setOnDismissListener(d -> adapter = null);
        dialog.show();
    }

    private void onChannelSelected(PlaylistChannel channel, BottomSheetDialog dialog) {
        if (channel.url == null || channel.url.trim().isEmpty()) {
            AppToast.error(this, "Invalid channel URL");
            return;
        }

        // The adapter position may belong to a filtered list; use the original index.
        int originalPosition = playlistModel.channels.indexOf(channel);
        if (originalPosition == -1) {
            AppToast.error(this, "Channel not found in playlist");
            return;
        }
        if (player == null) {
            AppToast.error(this, "Player is not ready");
            return;
        }

        currentChannelPosition = originalPosition;
        streamUrl = channel.url;

        playStream(channel.url);
        updateBanner(channel);
        playPauseBtn.setImageResource(R.drawable.ic_pause);

        dialog.dismiss();
    }

    private void styleSheetNavigationBar(BottomSheetDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setNavigationBarColor(ContextCompat.getColor(this, R.color.black));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(false);
                window.setNavigationBarDividerColor(Color.TRANSPARENT);
            }

            WindowCompat.getInsetsController(window, window.getDecorView())
                    .setAppearanceLightNavigationBars(false);
        }

        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.setBackgroundColor(Color.TRANSPARENT);
        }
    }


    // Categories
    private void setupCategoryList(@Nullable LinearLayout categoryList) {
        if (categoryList == null) return;

        // Keep the existing "All" chip (child 0), rebuild the rest.
        categoryList.removeViews(1, Math.max(0, categoryList.getChildCount() - 1));

        for (String category : getCategories()) {
            LinearLayout chip = createCategoryChip(category);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(8));
            categoryList.addView(chip, params);

            chip.setOnClickListener(v -> {
                selectedCategory = category;
                selectCategoryChip(chip, categoryList);
                applyChannelFilters();
            });
        }

        View allChip = categoryList.findViewById(R.id.categoryContainer);
        if (allChip != null) {
            allChip.setOnClickListener(v -> {
                selectedCategory = CATEGORY_ALL;
                selectCategoryChip(allChip, categoryList);
                applyChannelFilters();
            });
        }
    }

    private LinearLayout createCategoryChip(String category) {
        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setBackgroundResource(R.drawable.bg_chip);

        TextView label = new TextView(this);
        label.setText(category);
        label.setTextColor(getColor(R.color.ink_dim));
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.text_body_sm));
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        chip.addView(label);
        return chip;
    }

    private void selectCategoryChip(View selectedChip, LinearLayout categoryList) {
        for (int i = 0; i < categoryList.getChildCount(); i++) {
            View child = categoryList.getChildAt(i);
            boolean selected = child == selectedChip;

            child.setBackgroundResource(selected ? R.drawable.bg_chip_active : R.drawable.bg_chip);

            if (child instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) child;
                for (int j = 0; j < group.getChildCount(); j++) {
                    View inner = group.getChildAt(j);
                    if (inner instanceof TextView) {
                        ((TextView) inner).setTextColor(
                                getColor(selected ? R.color.accent : R.color.ink_dim));
                    }
                }
            }
        }
    }

    private List<String> getCategories() {
        Set<String> unique = new LinkedHashSet<>();
        if (playlistModel != null && playlistModel.channels != null) {
            for (PlaylistChannel channel : playlistModel.channels) {
                String category = normalizedCategory(channel);
                if (!category.isEmpty()) {
                    unique.add(category);
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private static String normalizedCategory(PlaylistChannel channel) {
        return channel.category == null ? "" : channel.category.trim();
    }


    // Search + filtering
    private void setupSearch(BottomSheetDialog dialog) {
        ImageView searchBtn = dialog.findViewById(R.id.searchButton);
        TextInputLayout searchBox = dialog.findViewById(R.id.channelSearchLayout);
        TextInputEditText searchEditText = dialog.findViewById(R.id.etChannelSearch);

        if (searchBtn == null || searchBox == null || searchEditText == null) return;

        ClickUtils.applyTouchEffect(searchBtn, v -> {
            showSearchBox = !showSearchBox;
            if (showSearchBox) {
                searchBox.setVisibility(View.VISIBLE);
                searchBtn.setImageResource(R.drawable.ic_close);
                searchEditText.requestFocus();
            } else {
                searchEditText.setText(""); // also clears the filter via TextWatcher
                searchBox.setVisibility(View.GONE);
                searchBtn.setImageResource(R.drawable.ic_search);
            }
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim();
                applyChannelFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    /** Single place that combines category + search filters. */
    private void applyChannelFilters() {
        if (adapter == null || playlistModel == null) return;

        boolean allCategories = CATEGORY_ALL.equals(selectedCategory);
        String query = searchQuery.toLowerCase(Locale.ROOT);

        List<PlaylistChannel> filtered = new ArrayList<>();
        for (PlaylistChannel channel : playlistModel.channels) {
            boolean matchesCategory = allCategories
                    || selectedCategory.equalsIgnoreCase(normalizedCategory(channel));

            boolean matchesSearch = query.isEmpty()
                    || (channel.name != null
                    && channel.name.toLowerCase(Locale.ROOT).contains(query));

            if (matchesCategory && matchesSearch) {
                filtered.add(channel);
            }
        }

        adapter.setChannels(filtered);

        // Keep the "now playing" highlight on the right row of the filtered list.
        int playingIndex = filtered.indexOf(playlistModel.channels.get(currentChannelPosition));
        if (playingIndex >= 0) {
            adapter.setPlayingPosition(playingIndex);
        }
    }


    // Picture-in-picture / back handling
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


    // Helpers
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}