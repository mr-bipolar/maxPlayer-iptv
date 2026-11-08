package com.aaronmaxlab.maxplayer.playlists;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.AddPlaylistActivity;
import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.models.LocalDbPlaylist;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.AppUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.aaronmaxlab.maxplayer.utils.UrlUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TvPlaylistsActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SqlDbHelper db;
    private TvPlaylistsAdapter adapter;

    private RecyclerView grid;
    private ProgressBar loading;
    private View emptyView;
    private TextView errorView;
    private TextView countView;
    private View btnAddPlaylist;
    private ImageButton backBtn;

    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_playlists);

        db = new SqlDbHelper(getApplicationContext());

        grid = findViewById(R.id.tvPlaylistsGrid);
        loading = findViewById(R.id.tvPlaylistsLoading);
        emptyView = findViewById(R.id.tvPlaylistsEmptyView);
        errorView = findViewById(R.id.tvPlaylistsErrorView);
        countView = findViewById(R.id.tvPlaylistsCount);
        btnAddPlaylist = findViewById(R.id.tvBtnAddPlaylist);
        backBtn = findViewById(R.id.backButton);

        ClickUtils.applyTouchEffect(backBtn, v -> finish());

        View emptyAddBtn = findViewById(R.id.tvEmptyBtnAddPlaylist);
        if (emptyAddBtn != null) {
            ClickUtils.applyTouchEffect(emptyAddBtn, v ->
                    startActivity(new Intent(this, AddPlaylistActivity.class)));
        }

        if (btnAddPlaylist != null) {
            ClickUtils.applyTouchEffect(btnAddPlaylist, v ->
                    startActivity(new Intent(this, AddPlaylistActivity.class)));
        }

        int widthDp = getResources().getConfiguration().screenWidthDp;
        int columns = Math.max(3, Math.min(5, widthDp / 240));
        grid.setLayoutManager(new GridLayoutManager(this, columns));
        grid.setItemAnimator(null);
        grid.setHasFixedSize(true);
        grid.setFocusable(false);
        grid.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);

        adapter = new TvPlaylistsAdapter(this::showTvPlaylistActions);
        grid.setAdapter(adapter);
    }

    private void showTvPlaylistActions(LocalDbPlaylist playlist) {
        android.util.Log.d("MaxPlayerTest", "showTvPlaylistActions called for: " + (playlist != null ? playlist.getPlaylistName() : "null"));
        if (playlist == null || isFinishing() || isDestroyed()) return;

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

        String name = playlist.getPlaylistName();
        if (name == null || name.trim().isEmpty()) {
            name = UIUtils.extractLabel(playlist.getPlaylistUrl());
        }
        title.setText(UIUtils.toCapitalize(name));

        int count = Math.max(0, playlist.getPlaylistCount());
        String channelCount = getResources().getQuantityString(
                R.plurals.playlist_channel_count, count, count);
        subtitle.setText(channelCount + " • Playlist");

        actionPrimaryIcon.setImageResource(R.drawable.ic_nav_playlists);
        actionPrimaryText.setText("Open Playlist");
        actionEditText.setText("Edit Playlist");
        actionDeleteText.setText("Delete Playlist");

        ClickUtils.applyTouchEffect(actionPrimary, v -> {
            dialog.dismiss();
            String url = playlist.getPlaylistUrl();
            if (url != null && !url.trim().isEmpty()) {
                startActivity(PlayerActivity.forPlaylist(this, url));
                executor.execute(() -> db.lastOpenUpdate(playlist.getId()));
            }
        });

        ClickUtils.applyTouchEffect(actionEdit, v -> {
            dialog.dismiss();
            showEditTvPlaylistDialog(playlist);
        });

        ClickUtils.applyTouchEffect(actionDelete, v -> {
            dialog.dismiss();
            AppUtils.showDeleteConfirmation(this, playlist.getPlaylistName(), () -> {
                executor.execute(() -> {
                    boolean ok = db.deletePlaylist(playlist.getId());
                    runOnUiThread(() -> {
                        if (destroyed) return;
                        if (ok) {
                            AppToast.success(this, "Playlist deleted.");
                            loadPlaylists();
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

    private void showEditTvPlaylistDialog(LocalDbPlaylist playlist) {
        if (playlist == null || isFinishing() || isDestroyed()) return;

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_playlist, null);

        EditText nameInput = view.findViewById(R.id.playlistNameInput);
        EditText urlInput = view.findViewById(R.id.playlistUrlInput);
        Button updateButton = view.findViewById(R.id.updateButton);
        Button cancelButton = view.findViewById(R.id.cancelButton);

        nameInput.setText(playlist.getPlaylistName());
        urlInput.setText(playlist.getPlaylistUrl());

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.PlayerDialogTheme)
                .setView(view)
                .create();

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        updateButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String url = urlInput.getText().toString().trim();

            if (name.isEmpty()) {
                nameInput.setError("Enter playlist name");
                nameInput.requestFocus();
                return;
            }

            if (url.isEmpty()) {
                urlInput.setError("Enter playlist URL");
                urlInput.requestFocus();
                return;
            }

            if (!UrlUtils.isValidPlaylistUrl(url)) {
                urlInput.setError("Enter a valid HTTP or HTTPS URL");
                urlInput.requestFocus();
                return;
            }

            dialog.dismiss();

            LocalDbPlaylist updated = new LocalDbPlaylist(
                    playlist.getId(),
                    name,
                    url,
                    playlist.getPlaylistCount(),
                    playlist.getCreatedAt()
            );

            executor.execute(() -> {
                boolean ok = db.updatePlaylist(updated);
                runOnUiThread(() -> {
                    if (destroyed) return;
                    if (ok) {
                        AppToast.success(this, "Playlist updated.");
                        loadPlaylists();
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

        nameInput.post(() -> nameInput.requestFocus());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPlaylists();
    }

    private void loadPlaylists() {
        loading.setVisibility(View.VISIBLE);
        grid.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);

        executor.execute(() -> {
            List<LocalDbPlaylist> playlists;
            try {
                playlists = db.getAllPlaylists();
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    loading.setVisibility(View.GONE);
                    errorView.setVisibility(View.VISIBLE);
                });
                return;
            }

            runOnUiThread(() -> {
                if (destroyed) return;
                loading.setVisibility(View.GONE);
                adapter.submitList(playlists);

                if (playlists == null || playlists.isEmpty()) {
                    countView.setText("");
                    emptyView.setVisibility(View.VISIBLE);
                    emptyView.post(() -> {
                        View emptyBtn = findViewById(R.id.tvEmptyBtnAddPlaylist);
                        if (emptyBtn != null) emptyBtn.requestFocus();
                    });
                } else {
                    countView.setText(getResources().getQuantityString(
                            R.plurals.playlist_channel_count, playlists.size(), playlists.size()));
                    grid.setVisibility(View.VISIBLE);
                    grid.post(() -> {
                        if (!destroyed && grid.getChildCount() > 0) {
                            grid.getChildAt(0).requestFocus();
                        }
                    });
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        if (db != null) db.close();
        super.onDestroy();
    }
}
