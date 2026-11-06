package com.aaronmaxlab.maxplayer.playlists;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
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
import com.aaronmaxlab.maxplayer.utils.ClickUtils;

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
        grid.setFocusable(true);

        adapter = new TvPlaylistsAdapter(playlist -> {
            String url = playlist.getPlaylistUrl();
            if (url != null && !url.trim().isEmpty()) {
                startActivity(PlayerActivity.forPlaylist(this, url));
            }
        });
        grid.setAdapter(adapter);
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
