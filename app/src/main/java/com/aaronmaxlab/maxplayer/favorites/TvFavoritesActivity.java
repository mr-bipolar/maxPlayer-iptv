
package com.aaronmaxlab.maxplayer.favorites;

import static com.aaronmaxlab.maxplayer.PlayAudioStreamActivity.EXTRA_STREAM_NAME;
import static com.aaronmaxlab.maxplayer.PlayAudioStreamActivity.EXTRA_STREAM_URI;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.models.LocalDbFavorites;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioPlayerActivity;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;
import com.aaronmaxlab.maxplayer.utils.StreamLauncher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TvFavoritesActivity extends AppCompatActivity {

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private SqlDbHelper db;
    private TvFavoritesAdapter adapter;

    private RecyclerView grid;
    private ProgressBar loading;
    private View emptyView;
    private TextView errorView;
    private TextView countView;

    private TextView filterAll;
    private TextView filterLive;
    private TextView filterVod;
    private TextView filterAudio;

    private List<LocalDbFavorites> allFavorites = new ArrayList<>();

    private String activeFilter = "ALL";
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_favorites);

        db = new SqlDbHelper(getApplicationContext());

        initializeViews();
        setupGrid();
        setupFilters();
        setupActions();

    }

    private void initializeViews() {
        grid = findViewById(R.id.tvFavoritesGrid);
        loading = findViewById(R.id.tvFavoritesLoading);
        emptyView = findViewById(R.id.tvFavoritesEmptyView);
        errorView = findViewById(R.id.tvFavoritesErrorView);
        countView = findViewById(R.id.tvFavoritesCount);

        filterAll = findViewById(R.id.filterAll);
        filterLive = findViewById(R.id.filterLive);
        filterVod = findViewById(R.id.filterVod);
        filterAudio = findViewById(R.id.filterAudio);
    }

    private void setupGrid() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        int columns = Math.max(3, Math.min(5, widthDp / 240));

        grid.setLayoutManager(new GridLayoutManager(this, columns));
        grid.setItemAnimator(null);
        grid.setHasFixedSize(true);
        grid.setFocusable(true);

        adapter = new TvFavoritesAdapter(this::openFavorite);
        grid.setAdapter(adapter);
    }

    private void setupFilters() {
        ClickUtils.applyTouchEffect(filterAll,
                v -> selectFilter("ALL"));

        ClickUtils.applyTouchEffect(filterLive,
                v -> selectFilter("LIVE"));

        ClickUtils.applyTouchEffect(filterVod,
                v -> selectFilter("VOD"));

        ClickUtils.applyTouchEffect(filterAudio,
                v -> selectFilter("AUDIO"));

        updateFilterStyles();
    }

    private void setupActions() {
        ImageButton backButton = findViewById(R.id.backButton);

        ClickUtils.applyTouchEffect(backButton, v -> finish());

        View browseButton =
                findViewById(R.id.tvEmptyBtnBrowseChannels);

        if (browseButton != null) {
            ClickUtils.applyTouchEffect(browseButton, v -> finish());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFavorites();
    }

    private void loadFavorites() {
        loading.setVisibility(View.VISIBLE);
        grid.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);

        executor.execute(() -> {
            List<LocalDbFavorites> result;

            try {
                result = db.getAllFavorites();
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

                allFavorites = result != null
                        ? result
                        : new ArrayList<>();

                applyFilter(false);
            });
        });
    }

    private void selectFilter(String filter) {
        if (activeFilter.equals(filter)) return;

        activeFilter = filter;
        updateFilterStyles();
        applyFilter(true);
    }

    private void applyFilter(boolean requestFocus) {
        List<LocalDbFavorites> filtered = new ArrayList<>();

        for (LocalDbFavorites favorite : allFavorites) {
            if (favorite == null) continue;

            PlaylistType type =
                    PlaylistType.fromDisplayName(favorite.getFavType());

            if ("ALL".equals(activeFilter)
                    || type.name().equals(activeFilter)) {
                filtered.add(favorite);
            }
        }

        adapter.submitList(filtered);

        countView.setText(String.valueOf(filtered.size()));

        if (filtered.isEmpty()) {
            grid.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
            grid.setVisibility(View.VISIBLE);

            grid.post(() -> {
                if (destroyed || grid.getChildCount() == 0) return;

                if (requestFocus || !grid.hasFocus()) {
                    grid.getChildAt(0).requestFocus();
                }
            });
        }
    }

    private void updateFilterStyles() {
        updateFilterStyle(filterAll, "ALL");
        updateFilterStyle(filterLive, "LIVE");
        updateFilterStyle(filterVod, "VOD");
        updateFilterStyle(filterAudio, "AUDIO");
    }

    private void updateFilterStyle(TextView view, String filter) {
        boolean selected = activeFilter.equals(filter);

        view.setBackgroundResource(
                selected
                        ? R.drawable.bg_pill_accent
                        : R.drawable.bg_tv_nav_item
        );

        view.setTextColor(ContextCompat.getColor(
                this,
                selected ? R.color.accent_ink : R.color.white
        ));
    }

    private void openFavorite(LocalDbFavorites favorite) {
        if (favorite == null) return;

        String url = favorite.getFavUrl();

        if (url == null || url.trim().isEmpty()) return;

        PlaylistType type =
                PlaylistType.fromDisplayName(favorite.getFavType());

        String title = favorite.getFavName();

        switch (type) {
            case PLAYLIST:
                startActivity(PlayerActivity.forPlaylist(this, url));
                break;

            case AUDIO:
                startActivity(new Intent(this, AudioPlayerActivity.class)
                        .putExtra(EXTRA_STREAM_URI, url)
                        .putExtra(EXTRA_STREAM_NAME, title));
                break;

            case VOD:
                StreamLauncher.launch(this, url, title, false);
                break;

            case LIVE:
            default:
                StreamLauncher.launch(this, url, title, true);
                break;
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();

        if (db != null) {
            db.close();
        }

        super.onDestroy();
    }
}
