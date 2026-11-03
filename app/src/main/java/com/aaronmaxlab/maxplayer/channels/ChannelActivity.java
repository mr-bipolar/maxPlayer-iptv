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

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.adapter.LocalDbChannelAdapter;
import com.aaronmaxlab.maxplayer.database.models.LocalDbChannel;
import com.aaronmaxlab.maxplayer.database.models.LocalDbPlaylist;
import com.aaronmaxlab.maxplayer.databinding.ActivityChannelBinding;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.playlist.SaveChannelDialog;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.AppUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.StreamLauncher;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.aaronmaxlab.maxplayer.utils.UrlUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChannelActivity extends AppCompatActivity {

    private ActivityChannelBinding binding;
    private SqlDbHelper sqlDbHelper;
    private LocalDbChannelAdapter channelAdapter;
    private GridLayoutManager channelLayoutManager;
    private int spanCount = 1;

    private static final String EXTRA_CATEGORY_ID = "extra_category_id";
    private static final String EXTRA_CATEGORY_NAME = "extra_category_name";

    private static final List<String> IPTV_CATEGORIES = AppUtils.showCategories();

    public static Intent forCategory(Context context, int categoryId, String categoryName) {
        return new Intent(context, ChannelActivity.class)
                .putExtra(EXTRA_CATEGORY_ID, categoryId)
                .putExtra(EXTRA_CATEGORY_NAME, categoryName);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityChannelBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        UIUtils.setLightNavigationBars(this, false); // black bottom nav
        UIUtils.applySystemBarPadding(binding.getRoot());

        sqlDbHelper = new SqlDbHelper(this);


        binding.channelSort.setOnClickListener(v -> {

            channelAdapter.sortUpDown();

            binding.channelSort.setRotation(
                    channelAdapter.isAscending() ? 0f : 180f
            );
        });

        int categoryId = getIntent().getIntExtra(EXTRA_CATEGORY_ID, -1);
        String categoryName = getIntent().getStringExtra(EXTRA_CATEGORY_NAME);

        setupChannels(categoryId , categoryName);
        setupChannelSearch();

        ClickUtils.applyTouchEffect(binding.backChannelList, v -> finish());

        binding.channelsList.setOnClickListener(v -> changeChannelView(false));
        binding.channelsGrid.setOnClickListener(v -> changeChannelView(true));
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
                    public void onChannelClick(LocalDbChannel channel) {
                        StreamLauncher.launch(ChannelActivity.this, channel.getChannelUrl(),
                                channel.getChannelName(), true);
                    }

                    @Override
                    public void onMenuClick(LocalDbChannel channel, View anchor) {

                        Context themedContext = new ContextThemeWrapper(
                                anchor.getContext(),
                                R.style.PlayerPopupMenuTheme
                        );

                        PopupMenu popupMenu = new PopupMenu(themedContext, anchor);

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


    }


    private void deleteChannel(LocalDbChannel channel){
        try {

            final int[] selectedCategoryIndex = {
                    channel.getChannelCategory()
            };

            AppUtils.showDeleteConfirmation(this, channel.getChannelName(), () -> {
                boolean result = sqlDbHelper.deleteChannel(channel.getChannelUrl(), channel.getChannelCategory());

                if(result){
                    AppToast.success(this, "Channel deleted.");
                    refreshChannelList(selectedCategoryIndex);
                }else {
                    AppToast.error(this, "Delete error.");
                }
            });
        } catch (Exception e) {
            AppToast.error(this, e.getMessage());
        }
    }


    private void setupChannelSearch() {

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

        channelAdapter.setGridMode(grid);
        channelLayoutManager.setSpanCount(spanCount);

        binding.channelsList.setBackgroundResource(
                grid ? R.drawable.bg_view_toggle : R.drawable.bg_pill_accent
        );

        binding.channelsGrid.setBackgroundResource(
                grid ? R.drawable.bg_pill_accent : R.drawable.bg_view_toggle
        );

        if(grid) binding.channelsRecyclerView.setPadding(18, 0, 18, 12 );

        ImageViewCompat.setImageTintList(
                binding.gridIcon,
                ColorStateList.valueOf(
                        ContextCompat.getColor(this, grid ? R.color.accent_ink : R.color.ink_faint)
                )
        );

        ImageViewCompat.setImageTintList(
                binding.listIcon,
                ColorStateList.valueOf(
                        ContextCompat.getColor(this, !grid ? R.color.accent_ink : R.color.ink_faint)
                )
        );

        binding.gridLabel.setTextColor(
                ContextCompat.getColor(this, grid ? R.color.accent_ink : R.color.ink_faint)
        );

        binding.listLabel.setTextColor(
                ContextCompat.getColor(this, !grid ? R.color.accent_ink : R.color.ink_faint)
        );
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

        category.setOnClickListener(v -> SaveChannelDialog.showCategoryPicker(
                this,
                IPTV_CATEGORIES,
                selectedCategoryIndex[0],
                index -> {
                    selectedCategoryIndex[0] = index;
                    txtCategory.setText(IPTV_CATEGORIES.get(index));
                }
        ));

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {

            String url = urlInput.getText().toString().trim();
            String name = channelName.getText().toString().trim();

            if (url.isEmpty()) {
                urlInput.setError("URL is required");
                return;
            }

            if(name.isEmpty()){
                channelName.setError("Name is required");
                return;
            }

            int categoryId = selectedCategoryIndex[0];
            LocalDbChannel updateChannel = new LocalDbChannel( channel.getId(), name, channel.getChannelImage(), url, categoryId);
            boolean  result =  sqlDbHelper.updateChannel(updateChannel, channel.getChannelCategory());

            if (result) {
                AppToast.success(this, "Channel updated.");
                refreshChannelList(selectedCategoryIndex);
            } else {
                AppToast.error(this, "Update error.");
            }

            dialog.dismiss();
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(
                    android.R.color.transparent
            );
        }
    }

    private  void refreshChannelList( int[] selectedCategoryIndex ){
        int selectedCatId = selectedCategoryIndex[0] + 1;
        setupChannels(
                selectedCatId,
                IPTV_CATEGORIES.get(selectedCategoryIndex[0])
        );
        changeChannelView(false);
    }
}