package com.aaronmaxlab.maxplayer.player.media3.video;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.media3.common.PlaybackException;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.ActivityVideoPlayerBinding;
import com.aaronmaxlab.maxplayer.playlist.PlaylistParser;
import com.aaronmaxlab.maxplayer.playlist.adapter.PlaylistAdapter;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistChannel;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistInfo;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.StreamErrorUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoPlayerActivity extends BasePlayerActivity {

    private static final String CATEGORY_ALL = "All";
    private static final String STATE_CHANNEL_POSITION = "channel_position";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityVideoPlayerBinding binding;

    private PlaylistInfo playlistModel;
    private String streamUrl;
    private int currentChannelPosition = 0; // index in playlistModel.channels

    // Bottom sheet state (reset every time the sheet opens).
    @Nullable private PlaylistAdapter adapter;
    private boolean showSearchBox = false;
    private String selectedCategory = CATEGORY_ALL;
    private String searchQuery = "";

    // ---------------------------------------------------------------------
    // Lifecycle / base hooks
    // ---------------------------------------------------------------------

    @NonNull
    @Override
    protected View inflateContent() {
        binding = ActivityVideoPlayerBinding.inflate(getLayoutInflater());
        return binding.getRoot();
    }

    @NonNull
    @Override
    protected PlayerView getBoundPlayerView() {
        return binding.playerView;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            currentChannelPosition = savedInstanceState.getInt(STATE_CHANNEL_POSITION, 0);
        }
        loadPlaylistAsync();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CHANNEL_POSITION, currentChannelPosition);
    }

    @Override
    protected void onSetupPlaylistDrawer(@NonNull LinearLayout drawer) {
        ClickUtils.applyTouchEffect(drawer, v -> showChannelsBottomSheet());
    }

    @Override
    protected void onPlayerConnected() {
        ensureStreamPlaying(streamUrl);
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isPlaying) StreamErrorUtils.hide(this);
    }

    @Override
    protected void onPlaybackError(@NonNull PlaybackException error) {
        StreamErrorUtils.show(this);

        String info = "Error: " + error.getMessage()
                + "\nError code: " + error.errorCode
                + "\nError name: " + error.getErrorCodeName()
                + "\nStream URL: " + streamUrl;

        StreamErrorUtils.showCopyButton(this, streamUrl, info);

        super.onPlaybackError(error); // toast
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        adapter = null;
        binding = null;
        super.onDestroy();
    }

    // ---------------------------------------------------------------------
    // Playlist loading
    // ---------------------------------------------------------------------

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

        // Restored after rotation, otherwise the first channel.
        if (currentChannelPosition < 0 || currentChannelPosition >= model.channels.size()) {
            currentChannelPosition = 0;
        }

        PlaylistChannel channel = model.channels.get(currentChannelPosition);
        streamUrl = channel.url;
        updateBanner(channel);

        connectPlayer();
        hideParsingStatus();
    }

    private void updateBanner(@NonNull PlaylistChannel channel) {
        setBanner(channel.name, channel.category);
    }

    // ---------------------------------------------------------------------
    // Channels bottom sheet
    // ---------------------------------------------------------------------

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

        StreamErrorUtils.hide(this);
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

    // ---------------------------------------------------------------------
    // Categories
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // Search + filtering
    // ---------------------------------------------------------------------

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
}