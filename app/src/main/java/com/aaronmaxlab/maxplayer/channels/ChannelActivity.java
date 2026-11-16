package com.aaronmaxlab.maxplayer.channels;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import com.aaronmaxlab.maxplayer.MaxPlayerApplication;
import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.adapter.LocalDbChannelAdapter;
import com.aaronmaxlab.maxplayer.database.models.LocalDbChannel;
import com.aaronmaxlab.maxplayer.database.models.LocalDbPlaylist;
import com.aaronmaxlab.maxplayer.databinding.ActivityChannelBinding;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioPlayerActivity;
import com.aaronmaxlab.maxplayer.playlist.SaveChannelDialog;
import com.aaronmaxlab.maxplayer.remote.RemoteKeyController;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.AppUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.DeviceUtils;
import com.aaronmaxlab.maxplayer.utils.StreamLauncher;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.aaronmaxlab.maxplayer.utils.UrlUtils;

import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChannelActivity extends AppCompatActivity {

    private ActivityChannelBinding binding;
    private SqlDbHelper sqlDbHelper;
    private LocalDbChannelAdapter channelAdapter;
    private TvCategoryChannelAdapter tvChannelAdapter;
    private GridLayoutManager channelLayoutManager;
    private int spanCount = 1;
    private int currentCategoryId = -1;
    private String currentCategoryName;

    private static final String EXTRA_CATEGORY_ID = "extra_category_id";
    private static final String EXTRA_CATEGORY_NAME = "extra_category_name";
    public static final String EXTRA_STREAM_URI = "stream_uri";
    public static final String EXTRA_STREAM_NAME = "stream_name";

    private static final List<String> IPTV_CATEGORIES = AppUtils.showCategories();

    public static Intent forCategory(Context context, int categoryId, String categoryName) {
        return new Intent(context, ChannelActivity.class)
                .putExtra(EXTRA_CATEGORY_ID, categoryId)
                .putExtra(EXTRA_CATEGORY_NAME, categoryName);
    }

   private RemoteKeyController remoteKeys;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityChannelBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        boolean isTv = DeviceUtils.isTv(this);

        if (!isTv) {
            UIUtils.setLightNavigationBars(this, false); // black bottom nav
            UIUtils.applySystemBarPadding(binding.getRoot());
        }

        remoteKeys = ((MaxPlayerApplication) getApplication()).getRemoteKeyController();

        sqlDbHelper = new SqlDbHelper(this);

        currentCategoryId = getIntent().getIntExtra(EXTRA_CATEGORY_ID, -1);
        currentCategoryName = getIntent().getStringExtra(EXTRA_CATEGORY_NAME);

        if (isTv) {
            setupTvChannels(currentCategoryId, currentCategoryName);
        } else {
            if (binding.channelSort != null) {
                binding.channelSort.setOnClickListener(v -> {
                    channelAdapter.sortUpDown();
                    assert binding.channelSort != null;
                    binding.channelSort.setRotation(
                            channelAdapter.isAscending() ? 0f : 180f
                    );
                });
            }

            setupChannels(currentCategoryId, currentCategoryName);
            if (binding.channelSearch != null) {
                setupChannelSearch();
            }

            if (binding.channelsList != null) {
                binding.channelsList.setOnClickListener(v -> changeChannelView(false));
            }
            if (binding.channelsGrid != null) {
                binding.channelsGrid.setOnClickListener(v -> changeChannelView(true));
            }
        }

        ClickUtils.applyTouchEffect(binding.backChannelList, v -> finish());
    }

    private void setupTvChannels(int catId, String catName) {
        Integer categoryIndex = catId == -1 ? null : catId - 1;

        binding.categoryTitle.setText(catName != null ? catName : "");

        int widthDp = getResources().getConfiguration().screenWidthDp;
        int columns = Math.max(3, Math.min(6, widthDp / 240));

        channelLayoutManager = new GridLayoutManager(this, columns);
        binding.channelsRecyclerView.setLayoutManager(channelLayoutManager);
        binding.channelsRecyclerView.setItemAnimator(null);
        binding.channelsRecyclerView.setHasFixedSize(true);
        binding.channelsRecyclerView.setFocusable(true);
        binding.channelsRecyclerView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        binding.channelsRecyclerView.setClipChildren(false);
        binding.channelsRecyclerView.setClipToPadding(false);

        tvChannelAdapter = new TvCategoryChannelAdapter((channel, isRadio) -> {
            showTvChannelActions(channel, isRadio, catName, categoryIndex);
        });
        tvChannelAdapter.setCategoryName(catName);
        binding.channelsRecyclerView.setAdapter(tvChannelAdapter);

        try {
            List<LocalDbChannel> channels = sqlDbHelper.getChannels(categoryIndex);
            if (channels == null || channels.isEmpty()) {
                binding.channelsCount.setText("0 channels");
                binding.emptyChannelsView.setVisibility(View.VISIBLE);
                binding.channelsRecyclerView.setVisibility(View.GONE);
            } else {
                binding.channelsCount.setText(getResources().getQuantityString(
                        R.plurals.playlist_channel_count, channels.size(), channels.size()));
                binding.emptyChannelsView.setVisibility(View.GONE);
                binding.channelsRecyclerView.setVisibility(View.VISIBLE);
                tvChannelAdapter.submitList(channels);

                binding.channelsRecyclerView.post(() -> {
                    if (!isFinishing() && binding.channelsRecyclerView.getChildCount() > 0) {
                        binding.channelsRecyclerView.getChildAt(0).requestFocus();
                    }
                });
            }
        } catch (Exception e) {
            binding.channelsRecyclerView.setVisibility(View.GONE);
            binding.emptyChannelsView.setVisibility(View.GONE);
            if (binding.tvChannelsErrorView != null) {
                binding.tvChannelsErrorView.setVisibility(View.VISIBLE);
            }
        }
    }


    private void setupChannels(int catId, String catName) {

        Integer categoryIndex = catId == -1
                ? null
                : catId - 1;

        List<LocalDbChannel> channels = sqlDbHelper.getChannels(categoryIndex);
        channelAdapter = new LocalDbChannelAdapter(
                this,
                true,
                channels,
                new LocalDbChannelAdapter.OnChannelClickListener() {
                    @Override
                    public void onChannelClick(LocalDbChannel channel, boolean isRadio) {
                        if(isRadio){
                            startActivity(new Intent(ChannelActivity.this, AudioPlayerActivity.class)
                                    .putExtra(EXTRA_STREAM_URI, channel.getChannelUrl())
                                    .putExtra(EXTRA_STREAM_NAME, channel.getChannelName()));
                        }else {
                            StreamLauncher.launch(ChannelActivity.this, channel.getChannelUrl(),
                                    channel.getChannelName(), true);
                        }
                    }

                    @Override
                    public void onMenuClick(LocalDbChannel channel, View anchor) {

                        Context themedContext = new ContextThemeWrapper(
                                anchor.getContext(),
                                R.style.PlayerPopupMenuTheme
                        );

                        Log.d("MaxPlayer", "Opening channel menu for: " + channel.getChannelName());
                        PopupMenu popupMenu = new PopupMenu(themedContext, anchor);
                        popupMenu.setGravity(android.view.Gravity.END);

                        popupMenu.getMenu().add(0, 1, 0, "Edit");
                        popupMenu.getMenu().add(0, 2, 1, "Delete");

                        popupMenu.setOnMenuItemClickListener(item -> {

                            switch (item.getItemId()) {
                                case 1:
                                    // Edit channel
                                    showUpdateChannelDialog(channel);
                                    return true;

                                case 2:
                                    // Delete channel
                                    deleteChannel(channel);
                                    return true;

                                default:
                                    return false;
                            }
                        });

                        popupMenu.show();
                    }

                }
        );

        channelAdapter.setCategoryName(catName);
        binding.categoryTitle.setText(catName);
        binding.channelsCount.setText(String.valueOf(channels.size() + "  Channels "));

        channelLayoutManager = new GridLayoutManager(this, spanCount);

        binding.channelsRecyclerView.setLayoutManager(channelLayoutManager);
        binding.channelsRecyclerView.setHasFixedSize(true);
        binding.channelsRecyclerView.setAdapter(channelAdapter);

        if (channels.isEmpty()) {
            binding.emptyChannelsView.setVisibility(View.VISIBLE);
            binding.channelsRecyclerView.setVisibility(View.GONE);
        } else {
            binding.emptyChannelsView.setVisibility(View.GONE);
            binding.channelsRecyclerView.setVisibility(View.VISIBLE);
        }


    }


    private void showTvChannelActions(LocalDbChannel channel, boolean isRadio, String catName, Integer categoryIndex) {
        Log.d("MaxPlayerTest", "showTvChannelActions in ChannelActivity for: " + (channel != null ? channel.getChannelName() : "null"));
        if (channel == null || isFinishing() || isDestroyed()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_tv_actions, null);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.PlayerDialogTheme)
                .setView(dialogView)
                .create();

        TextView title = dialogView.findViewById(R.id.tvActionTitle);
        TextView subtitle = dialogView.findViewById(R.id.tvActionSubtitle);
        View actionPrimary = dialogView.findViewById(R.id.tvActionPrimary);
        android.widget.ImageView actionPrimaryIcon = dialogView.findViewById(R.id.tvActionPrimaryIcon);
        TextView actionPrimaryText = dialogView.findViewById(R.id.tvActionPrimaryText);
        View actionEdit = dialogView.findViewById(R.id.tvActionEdit);
        TextView actionEditText = dialogView.findViewById(R.id.tvActionEditText);
        View actionDelete = dialogView.findViewById(R.id.tvActionDelete);
        TextView actionDeleteText = dialogView.findViewById(R.id.tvActionDeleteText);
        View actionCancel = dialogView.findViewById(R.id.tvActionCancel);

        String name = channel.getChannelName();
        title.setText(name != null ? UIUtils.toCapitalize(name) : "");

        String sub = catName != null ? catName : "";
        sub += isRadio ? " • Radio" : " • Live Stream";
        subtitle.setText(sub);

        actionPrimaryIcon.setImageResource(isRadio ? R.drawable.ic_radio : R.drawable.ic_play);
        actionPrimaryText.setText(isRadio ? "Play Audio" : "Play Stream");
        actionEditText.setText("Edit Channel");
        actionDeleteText.setText("Delete Channel");

        ClickUtils.applyTouchEffect(actionPrimary, v -> {
            dialog.dismiss();
            if (isRadio) {
                startActivity(new Intent(ChannelActivity.this, AudioPlayerActivity.class)
                        .putExtra(EXTRA_STREAM_URI, channel.getChannelUrl())
                        .putExtra(EXTRA_STREAM_NAME, channel.getChannelName()));
            } else {
                StreamLauncher.launch(ChannelActivity.this, channel.getChannelUrl(),
                        channel.getChannelName(), true);
            }
        });

        ClickUtils.applyTouchEffect(actionEdit, v -> {
            dialog.dismiss();
            showUpdateChannelDialog(channel);
        });

        ClickUtils.applyTouchEffect(actionDelete, v -> {
            dialog.dismiss();
            deleteChannel(channel);
        });

        ClickUtils.applyTouchEffect(actionCancel, v -> dialog.dismiss());
        dialog.show();

        remoteKeys.registerDialog(dialog);
        dialog.setOnDismissListener(d -> remoteKeys.unregisterDialog(dialog));

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        actionPrimary.post(actionPrimary::requestFocus);
    }

    private void deleteChannel(LocalDbChannel channel) {
        if (channel == null) return;
        try {
            AppUtils.showDeleteConfirmation(this, channel.getChannelName(), () -> {
                boolean result = sqlDbHelper.deleteChannel(channel.getChannelUrl(), channel.getChannelCategory());

                if (result) {
                    AppToast.success(this, "Channel deleted.");
                    refreshChannelList();
                } else {
                    AppToast.error(this, "Delete error.");
                }
            });
        } catch (Exception e) {
            AppToast.error(this, e.getMessage());
        }
    }


    private void setupChannelSearch() {

        assert binding.channelSearch != null;
        binding.channelSearch.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {

                if (channelAdapter != null) {
                    channelAdapter.search(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void changeChannelView(boolean grid) {

        spanCount = grid ? 3 : 1;

        if (channelAdapter != null) {
            channelAdapter.setGridMode(grid);
        }
        if (channelLayoutManager != null) {
            channelLayoutManager.setSpanCount(spanCount);
        }

        if (binding.channelsList != null) {
            binding.channelsList.setBackgroundResource(
                    grid ? R.drawable.bg_view_toggle : R.drawable.bg_pill_accent
            );
        }

        if (binding.channelsGrid != null) {
            binding.channelsGrid.setBackgroundResource(
                    grid ? R.drawable.bg_pill_accent : R.drawable.bg_view_toggle
            );
        }

        if (grid) binding.channelsRecyclerView.setPadding(18, 0, 18, 12 );

        if (binding.gridIcon != null) {
            ImageViewCompat.setImageTintList(
                    binding.gridIcon,
                    ColorStateList.valueOf(
                            ContextCompat.getColor(this, grid ? R.color.accent_ink : R.color.ink_faint)
                    )
            );
        }

        if (binding.listIcon != null) {
            ImageViewCompat.setImageTintList(
                    binding.listIcon,
                    ColorStateList.valueOf(
                            ContextCompat.getColor(this, !grid ? R.color.accent_ink : R.color.ink_faint)
                    )
            );
        }

        if (binding.gridLabel != null) {
            binding.gridLabel.setTextColor(
                    ContextCompat.getColor(this, grid ? R.color.accent_ink : R.color.ink_faint)
            );
        }

        if (binding.listLabel != null) {
            binding.listLabel.setTextColor(
                    ContextCompat.getColor(this, !grid ? R.color.accent_ink : R.color.ink_faint)
            );
        }
    }



    private void showUpdateChannelDialog(LocalDbChannel channel) {

        View view = LayoutInflater.from(this)
                .inflate(R.layout.channel_update, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        final int[] selectedCategoryIndex = {
                channel.getChannelCategory()
        };

        EditText urlInput = view.findViewById(R.id.channelUrlInput);
        EditText channelName = view.findViewById(R.id.channelNameInput);
        TextView btnCancel = view.findViewById(R.id.btnCancel);
        TextView btnSubmit = view.findViewById(R.id.btnSubmit);
        LinearLayout category = view.findViewById(R.id.categoryField);


        // Populate existing channel details
        urlInput.setText(channel.getChannelUrl());
        channelName.setText(channel.getChannelName());

        // Category picker
        TextView txtCategory = view.findViewById(R.id.txtCategory);

        txtCategory.setText(
                IPTV_CATEGORIES.get(selectedCategoryIndex[0])
        );

        View.OnClickListener openCategoryPicker = v ->
                SaveChannelDialog.showCategoryPicker(
                        this,
                        IPTV_CATEGORIES,
                        selectedCategoryIndex[0],
                        index -> {
                            selectedCategoryIndex[0] = index;
                            txtCategory.setText(IPTV_CATEGORIES.get(index));
                        }
                );

        category.setOnClickListener(openCategoryPicker);
        txtCategory.setOnClickListener(openCategoryPicker);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {

            String url = urlInput.getText().toString().trim();
            String name = channelName.getText().toString().trim();

            if (url.isEmpty()) {
                urlInput.setError("URL is required");
                urlInput.requestFocus();
                return;
            }

            if (!UrlUtils.isValidStreamUrl(url)) {
                urlInput.setError("Enter a valid stream URL");
                urlInput.requestFocus();
                return;
            }

            if (name.isEmpty()) {
                channelName.setError("Name is required");
                channelName.requestFocus();
                return;
            }

            int categoryId = selectedCategoryIndex[0];
            LocalDbChannel updateChannel = new LocalDbChannel(channel.getId(), name, channel.getChannelImage(), url, categoryId);
            boolean result = sqlDbHelper.updateChannel(updateChannel, channel.getChannelCategory());

            if (result) {
                AppToast.success(this, "Channel updated.");
                refreshChannelList();
            } else {
                AppToast.error(this, "Update error.");
            }

            dialog.dismiss();
        });

        dialog.show();
        remoteKeys.registerDialog(dialog);
        dialog.setOnDismissListener(d -> remoteKeys.unregisterDialog(dialog));

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(
                    android.R.color.transparent
            );
        }

        channelName.post(channelName::requestFocus);
    }

    private void refreshChannelList() {
        if (DeviceUtils.isTv(this)) {
            setupTvChannels(currentCategoryId, currentCategoryName);
        } else {
            setupChannels(currentCategoryId, currentCategoryName);
            changeChannelView(spanCount == 3);
        }
    }

    @Override
    protected void onDestroy() {
        if (sqlDbHelper != null) {
            sqlDbHelper.close();
            sqlDbHelper = null;
        }
        super.onDestroy();
    }
}