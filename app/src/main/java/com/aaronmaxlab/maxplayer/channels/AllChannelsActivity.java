package com.aaronmaxlab.maxplayer.channels;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.adapter.ChannelGridAdapter;
import com.aaronmaxlab.maxplayer.database.models.LocalDbChannel;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.playlist.SaveChannelDialog;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.AppUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.aaronmaxlab.maxplayer.utils.UrlUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AllChannelsActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SqlDbHelper db;
    private RecyclerView grid;
    private ProgressBar loading;
    private TextView message;
    private TextView count;
    private ChannelGridAdapter adapter;
    private volatile boolean destroyed;

    private static final List<String> IPTV_CATEGORIES = AppUtils.showCategories();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_all_channels);

        db = new SqlDbHelper(getApplicationContext());

        grid = findViewById(R.id.channelGrid);
        loading = findViewById(R.id.loading);
        message = findViewById(R.id.message);
        count = findViewById(R.id.count);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        int widthDp = getResources().getConfiguration().screenWidthDp;
        int columns = Math.max(2, Math.min(6, widthDp / 210));
        grid.setLayoutManager(new GridLayoutManager(this, columns));
        grid.setItemAnimator(null);
        grid.setHasFixedSize(false);
        grid.setFocusable(false);
        grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        adapter = new ChannelGridAdapter(this::showTvChannelActions);
        grid.setAdapter(adapter);
        loadChannels();
    }

    private void showTvChannelActions(LocalDbChannel channel) {
        android.util.Log.d("MaxPlayerTest", "showTvChannelActions called for: " + (channel != null ? channel.getChannelName() : "null"));
        if (channel == null || isFinishing() || isDestroyed()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_tv_actions, null);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.PlayerDialogTheme)
                .setView(dialogView)
                .create();

        TextView title = dialogView.findViewById(R.id.tvActionTitle);
        TextView subtitle = dialogView.findViewById(R.id.tvActionSubtitle);
        View actionPrimary = dialogView.findViewById(R.id.tvActionPrimary);
        ImageView actionPrimaryIcon = dialogView.findViewById(R.id.tvActionPrimaryIcon);
        TextView actionPrimaryText = dialogView.findViewById(R.id.tvActionPrimaryText);
        View actionEdit = dialogView.findViewById(R.id.tvActionEdit);
        TextView actionEditText = dialogView.findViewById(R.id.tvActionEditText);
        View actionDelete = dialogView.findViewById(R.id.tvActionDelete);
        TextView actionDeleteText = dialogView.findViewById(R.id.tvActionDeleteText);
        View actionCancel = dialogView.findViewById(R.id.tvActionCancel);

        String name = channel.getChannelName();
        title.setText(name != null ? UIUtils.toCapitalize(name) : "");

        int catIdx = channel.getChannelCategory() != null ? channel.getChannelCategory() : 0;
        String catName = (catIdx >= 0 && catIdx < IPTV_CATEGORIES.size()) ? IPTV_CATEGORIES.get(catIdx) : "Live TV";
        subtitle.setText(catName + " • Channel");

        actionPrimaryIcon.setImageResource(R.drawable.ic_play);
        actionPrimaryText.setText("Play Stream");
        actionEditText.setText("Edit Channel");
        actionDeleteText.setText("Delete Channel");

        ClickUtils.applyTouchEffect(actionPrimary, v -> {
            android.util.Log.d("MaxPlayerTest", "actionPrimary clicked (Play Stream)");
            dialog.dismiss();
            String url = channel.getChannelUrl();
            if (url != null && !url.trim().isEmpty()) {
                startActivity(PlayerActivity.forStream(this, url, channel.getChannelName(), true));
            }
        });

        ClickUtils.applyTouchEffect(actionEdit, v -> {
            dialog.dismiss();
            showTvEditChannelDialog(channel);
        });

        ClickUtils.applyTouchEffect(actionDelete, v -> {
            dialog.dismiss();
            AppUtils.showDeleteConfirmation(this, channel.getChannelName(), () -> {
                executor.execute(() -> {
                    boolean ok = db.deleteChannel(channel.getChannelUrl(), channel.getChannelCategory());
                    runOnUiThread(() -> {
                        if (destroyed) return;
                        if (ok) {
                            AppToast.success(this, "Channel deleted.");
                            loadChannels();
                        } else {
                            AppToast.error(this, "Delete failed.");
                        }
                    });
                });
            });
        });

        ClickUtils.applyTouchEffect(actionCancel, v -> dialog.dismiss());

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        actionPrimary.post(() -> actionPrimary.requestFocus());
    }

    private void showTvEditChannelDialog(LocalDbChannel channel) {
        if (channel == null || isFinishing() || isDestroyed()) return;

        View view = LayoutInflater.from(this).inflate(R.layout.channel_update, null);

        EditText urlInput = view.findViewById(R.id.channelUrlInput);
        EditText channelName = view.findViewById(R.id.channelNameInput);
        TextView btnCancel = view.findViewById(R.id.btnCancel);
        TextView btnSubmit = view.findViewById(R.id.btnSubmit);
        LinearLayout category = view.findViewById(R.id.categoryField);
        TextView txtCategory = view.findViewById(R.id.txtCategory);

        final int[] selectedCategoryIndex = {
                channel.getChannelCategory() != null ? channel.getChannelCategory() : 0
        };

        urlInput.setText(channel.getChannelUrl());
        channelName.setText(channel.getChannelName());

        if (selectedCategoryIndex[0] >= 0 && selectedCategoryIndex[0] < IPTV_CATEGORIES.size()) {
            txtCategory.setText(IPTV_CATEGORIES.get(selectedCategoryIndex[0]));
        }

        category.setOnClickListener(v -> SaveChannelDialog.showCategoryPicker(
                this,
                IPTV_CATEGORIES,
                selectedCategoryIndex[0],
                index -> {
                    selectedCategoryIndex[0] = index;
                    txtCategory.setText(IPTV_CATEGORIES.get(index));
                }
        ));

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.PlayerDialogTheme)
                .setView(view)
                .create();

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

            dialog.dismiss();

            LocalDbChannel updated = new LocalDbChannel(
                    channel.getId(),
                    name,
                    channel.getChannelImage(),
                    url,
                    selectedCategoryIndex[0]
            );

            executor.execute(() -> {
                boolean ok = db.updateChannel(updated, channel.getChannelCategory());
                runOnUiThread(() -> {
                    if (destroyed) return;
                    if (ok) {
                        AppToast.success(this, "Channel updated.");
                        loadChannels();
                    } else {
                        AppToast.error(this, "Update failed.");
                    }
                });
            });
        });

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        channelName.post(() -> channelName.requestFocus());
    }

    private void loadChannels() {
        loading.setVisibility(View.VISIBLE);
        grid.setVisibility(View.GONE);
        message.setVisibility(View.GONE);
        executor.execute(() -> {
            List<LocalDbChannel> result;
            try {
                result = db.getChannels(null);
            } catch (Exception error) {
                runOnUiThread(() -> showMessage(getString(R.string.tv_channels_load_error)));
                return;
            }
            runOnUiThread(() -> {
                if (destroyed) return;
                loading.setVisibility(View.GONE);
                adapter.submitList(result);
                if (result.isEmpty()) {
                    count.setText("0 channels");
                    showMessage(getString(R.string.tv_no_channels));
                } else {
                    count.setText(getResources().getQuantityString(R.plurals.playlist_channel_count, result.size(), result.size()));
                    grid.setVisibility(View.VISIBLE);
                    grid.post(() -> {
                        if (!destroyed && grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
                    });
                }
            });
        });
    }

    private void showMessage(String text) {
        if (destroyed) return;
        loading.setVisibility(View.GONE);
        grid.setVisibility(View.GONE);
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        if (db != null) db.close();
        super.onDestroy();
    }
}
