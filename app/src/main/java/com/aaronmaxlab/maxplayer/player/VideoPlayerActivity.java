package com.aaronmaxlab.maxplayer.player;

import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.ActivityVideoPlayerBinding;
import com.aaronmaxlab.maxplayer.playlist.adapter.PlaylistAdapter;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistChannel;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistInfo;
import com.aaronmaxlab.maxplayer.service.VideoPlaybackService;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.AspectRatioUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.PlayerUtils;
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

public class VideoPlayerActivity extends AppCompatActivity {

    private ActivityVideoPlayerBinding binding;
    private MediaController player;
    private PlayerView playerView;
    private int aspectMode = 0;
    private TextView bufferSize;
    private PlaylistInfo playlistModel;
    private  String streamUrl;
    private int currentChannelPosition = 0;
    private ListenableFuture<MediaController> controllerFuture;
    private boolean wasInPipMode = false;
    private boolean isFullscreen = false;
    private PlaylistAdapter adapter;
    private boolean showSearchBox = false;
    private List<PlaylistChannel> allChannels = new ArrayList<>();
    private String selectedCategory = "All";
    private String searchQuery = "";



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


    private void showChannelsBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.playlist_drawer);

        RecyclerView rvChannels = dialog.findViewById(R.id.rvChannels);
        if (rvChannels == null) {
            return;
        }

        adapter = new PlaylistAdapter();

        rvChannels.setLayoutManager(new LinearLayoutManager(this));
        rvChannels.setAdapter(adapter);
        adapter.setChannels(playlistModel.channels);
        adapter.setPlayingPosition(currentChannelPosition);

        String finalName =
                UIUtils.extractLabel(playlistModel.fileName).toUpperCase();

        TextView topLabel = dialog.findViewById(R.id.topLabelView);
        if (topLabel != null) {
            topLabel.setText(getString(
                    R.string.drawer_header,
                    finalName,
                    playlistModel.channelCount
            ));
        }

        // Category chips
        LinearLayout categoryList = dialog.findViewById(R.id.categoryList);

        if (categoryList != null) {
            setupCategoryChips(categoryList, getCategories());

            View allChip = categoryList.findViewById(R.id.categoryContainer);
            if (allChip != null) {
                allChip.setOnClickListener(v -> {
                    selectedCategory = "All";
                    selectCategoryChip((LinearLayout) allChip, categoryList);
                    applyChannelFilters();
                });
            }
        }

        // Search controls
        ImageView searchBtn = dialog.findViewById(R.id.searchButton);
        TextInputLayout searchBox =
                dialog.findViewById(R.id.channelSearchLayout);
        TextInputEditText searchEditText =
                dialog.findViewById(R.id.etChannelSearch);

        if (searchBtn != null && searchBox != null && searchEditText != null) {
            ClickUtils.applyTouchEffect(searchBtn, v -> {
                if (showSearchBox) {
                    searchEditText.setText("");
                    searchBox.setVisibility(View.GONE);
                    searchBtn.setImageResource(R.drawable.ic_search);
                    showSearchBox = false;
                } else {
                    searchBox.setVisibility(View.VISIBLE);
                    searchBtn.setImageResource(R.drawable.ic_close);
                    showSearchBox = true;
                    searchEditText.requestFocus();
                }
            });

            setupChannelSearch(searchEditText, playlistModel.channels);
        }

        // Channel selection
        adapter.setOnChannelClickListener((channel, position) -> {
            if (channel.url == null || channel.url.trim().isEmpty()) {
                AppToast.error(this, "Invalid channel URL");
                return;
            }

            // Find the channel's original playlist position.
            int originalPosition = playlistModel.channels.indexOf(channel);

            if (originalPosition == -1) {
                AppToast.error(this, "Channel not found in playlist");
                return;
            }

            currentChannelPosition = originalPosition;
            streamUrl = channel.url;

            // Switch the player to the selected stream.
            player.setMediaItem(MediaItem.fromUri(channel.url));
            player.prepare();
            player.play();

            // Update player labels.
            TextView bannerTitle =
                    playerView.findViewById(R.id.playerTopLabel);
            TextView bannerSubtext =
                    playerView.findViewById(R.id.playerSubLabel);
            ImageView playPauseBtn =
                    playerView.findViewById(R.id.play_icon);

            if (bannerTitle != null) {
                bannerTitle.setText(channel.name);
            }

            if (bannerSubtext != null) {
                bannerSubtext.setText(channel.category);
            }

            if (playPauseBtn != null) {
                playPauseBtn.setImageResource(R.drawable.ic_pause);
            }

            // The stored position is the original playlist position.
            adapter.setPlayingPosition(position);

            dialog.dismiss();
        });

        // Bottom sheet system navigation styling.
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();

            if (window != null) {
                window.setNavigationBarColor(
                        ContextCompat.getColor(this, R.color.black)
                );

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.setNavigationBarContrastEnforced(false);
                    window.setNavigationBarDividerColor(Color.TRANSPARENT);
                }

                window.getDecorView().setSystemUiVisibility(
                        window.getDecorView().getSystemUiVisibility()
                                & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                );
            }

            View bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet
            );

            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
            }
        });

        dialog.show();
    }



    @OptIn(markerClass = UnstableApi.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

         binding = ActivityVideoPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            playlistModel = getIntent().getSerializableExtra(
                    "playlist_model", PlaylistInfo.class);
        } else {
            playlistModel = (PlaylistInfo) getIntent()
                    .getSerializableExtra("playlist_model");
        }


        if (playlistModel == null) {
            AppToast.warning(this, "Playlist not found");
            finish();
            return;
        }

        if (playlistModel.channels == null || playlistModel.channels.isEmpty()) {
            AppToast.warning(this, "No channels found in playlist");
            finish();
            return;
        }


        playerView = binding.playerView;
        streamUrl = playlistModel.channels.get(0).url;


        bufferHandler.removeCallbacks(bufferUpdateRunnable);
        bufferHandler.post(bufferUpdateRunnable);


       ImageView playPauseBtn = playerView.findViewById(R.id.play_icon);
       LinearLayout aspectRatio = playerView.findViewById(R.id.aspect_radio);
       LinearLayout playlistDrawer = playerView.findViewById(R.id.playlistDrawer);
       LinearLayout fullscreen = playerView.findViewById(R.id.fullscreen_enter_icon);
       ImageView fullscreenIcon = playerView.findViewById(R.id.fullscreen_icon);
       ImageView backBtn = playerView.findViewById(R.id.backPlayerView);
       TextView exitText = playerView.findViewById(R.id.exit_fullscreen);
        bufferSize = playerView.findViewById(R.id.bufferSize);

        TextView bannerTitle = playerView.findViewById(R.id.playerTopLabel);
        TextView bannerSubtext = playerView.findViewById(R.id.playerSubLabel);

        bannerTitle.setText(playlistModel.channels.get(0).name);
        bannerSubtext.setText(playlistModel.channels.get(0).category);

        connectVideoService(playPauseBtn);


        // play and pause controller
        ClickUtils.applyTouchEffect(playPauseBtn , v -> {
            Util.handlePlayPauseButtonAction(player);
        });


        // aspect ratio setup
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

        // back button
        ClickUtils.applyTouchEffect(backBtn, v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                handleBackPress();
            } else {
                closeAndFinish();
            }
        });

        // drawer
        ClickUtils.applyTouchEffect(playlistDrawer, v -> showChannelsBottomSheet());

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
    private void setupCategoryChips(
            LinearLayout categoryList,
            List<String> categories
    ) {
        // Keep the existing "All" chip.
        categoryList.removeViews(
                1,
                Math.max(0, categoryList.getChildCount() - 1)
        );

        for (String category : categories) {
            LinearLayout chip = new LinearLayout(this);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setBackgroundResource(R.drawable.bg_chip);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            params.setMarginEnd(dp(8));

            TextView label = new TextView(this);
            label.setText(category);
            label.setTextColor(getColor(R.color.ink_dim));
            label.setTextSize(
                    getResources().getDimension(R.dimen.text_body_sm)
                            / getResources().getDisplayMetrics().scaledDensity
            );
            label.setTypeface(
                    android.graphics.Typeface.create(
                            "sans-serif-medium",
                            android.graphics.Typeface.NORMAL
                    )
            );

            chip.addView(label);
            categoryList.addView(chip, params);

            chip.setOnClickListener(v -> {
                selectCategoryChip(chip, categoryList);
                filterChannelsByCategory(category);
            });
        }
    }

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private void selectCategoryChip(
            LinearLayout selectedChip,
            LinearLayout categoryList
    ) {
        for (int i = 0; i < categoryList.getChildCount(); i++) {
            View child = categoryList.getChildAt(i);
            boolean selected = child == selectedChip;

            child.setBackgroundResource(
                    selected
                            ? R.drawable.bg_chip_active
                            : R.drawable.bg_chip
            );

            if (child instanceof ViewGroup) {
                for (int j = 0; j < ((ViewGroup) child).getChildCount(); j++) {
                    View inner = ((ViewGroup) child).getChildAt(j);
                    if (inner instanceof TextView) {
                        ((TextView) inner).setTextColor(
                                getColor(selected
                                        ? R.color.accent
                                        : R.color.ink_dim)
                        );
                    }
                }
            }
        }
    }

    private void filterChannelsByCategory(String category) {
        List<PlaylistChannel> filtered = new ArrayList<>();

        for (PlaylistChannel channel : playlistModel.channels) {
            if (category.equalsIgnoreCase(channel.category)) {
                filtered.add(channel);
            }
        }

        adapter.setChannels(filtered);
    }

    private void setupChannelSearch(
            TextInputEditText searchEditText,
            List<PlaylistChannel> channels
    ) {
        allChannels = new ArrayList<>(channels);

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after
            ) {}

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count
            ) {
                searchQuery = s.toString().trim();
                applyChannelFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void applyChannelFilters() {
        List<PlaylistChannel> filtered = new ArrayList<>();

        for (PlaylistChannel channel : allChannels) {
            boolean matchesCategory =
                    selectedCategory.equals("All")
                            || (selectedCategory.equalsIgnoreCase(channel.category));

            boolean matchesSearch =
                    channel.name != null
                            && channel.name.toLowerCase(Locale.ROOT)
                            .contains(searchQuery.toLowerCase(Locale.ROOT));

            if (matchesCategory && matchesSearch) {
                filtered.add(channel);
            }
        }

        adapter.setChannels(filtered);
    }

    private List<String> getCategories() {
        Set<String> uniqueCategories = new LinkedHashSet<>();

        if (playlistModel == null || playlistModel.channels == null) {
            return new ArrayList<>();
        }

        for (PlaylistChannel channel : playlistModel.channels) {
            if (channel.category != null && !channel.category.trim().isEmpty()) {
                uniqueCategories.add(channel.category.trim());
            }
        }

        return new ArrayList<>(uniqueCategories);
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
                                VideoPlayerActivity.this,
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