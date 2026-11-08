package com.aaronmaxlab.maxplayer.search;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.PlayAudioStreamActivity;
import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioPlayerActivity;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;
import com.aaronmaxlab.maxplayer.utils.StreamLauncher;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class TvSearchActivity extends AppCompatActivity {

    private static final int DEBOUNCE_MS = 300;

    private SqlDbHelper db;
    private TvSearchAdapter adapter;

    private EditText searchInput;
    private ImageButton clearBtn;
    private RecyclerView grid;
    private ProgressBar loading;
    private View promptView;
    private View emptyView;
    private TextView emptyBody;
    private View errorView;
    private TextView resultCount;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger queryEpoch = new AtomicInteger(0);
    private Runnable pendingSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_search);

        db = new SqlDbHelper(this);

        searchInput = findViewById(R.id.tvSearchInput);
        clearBtn = findViewById(R.id.tvSearchClear);
        grid = findViewById(R.id.tvSearchGrid);
        loading = findViewById(R.id.tvSearchLoading);
        promptView = findViewById(R.id.tvSearchPromptView);
        emptyView = findViewById(R.id.tvSearchEmptyView);
        emptyBody = findViewById(R.id.tvSearchEmptyBody);
        errorView = findViewById(R.id.tvSearchErrorView);
        resultCount = findViewById(R.id.tvSearchResultCount);

        // Back button
        View backBtn = findViewById(R.id.backButton);
        if (backBtn != null) {
            ClickUtils.applyTouchEffect(backBtn, v -> finish());
        }

        // Clear button
        ClickUtils.applyTouchEffect(clearBtn, v -> {
            searchInput.setText("");
            searchInput.requestFocus();
        });

        // Adapter
        adapter = new TvSearchAdapter(this::onResultSelected);
        grid.setAdapter(adapter);
        grid.setLayoutManager(new GridLayoutManager(this, 5));

        // Text watcher with debounce
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                clearBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);

                if (pendingSearch != null) mainHandler.removeCallbacks(pendingSearch);

                if (query.isEmpty()) {
                    showPrompt();
                    return;
                }

                pendingSearch = () -> runSearch(query);
                mainHandler.postDelayed(pendingSearch, DEBOUNCE_MS);
            }
        });

        // Initial focus on search field
        searchInput.requestFocus();
        showPrompt();
    }

    private void runSearch(String query) {
        final int epoch = queryEpoch.incrementAndGet();
        showLoading(true);

        executor.execute(() -> {
            try {
                List<SqlDbHelper.UnifiedSearchResult> results = db.searchAllMedia(query);
                mainHandler.post(() -> {
                    if (queryEpoch.get() != epoch || isFinishing()) return;
                    showLoading(false);
                    adapter.submitList(results);

                    if (resultCount != null) {
                        resultCount.setText(results.size() + " results");
                        resultCount.setVisibility(View.VISIBLE);
                    }

                    if (results.isEmpty()) {
                        showEmpty(query);
                    } else {
                        showResults();
                        grid.post(() -> {
                            if (grid.getChildCount() > 0) {
                                grid.getChildAt(0).requestFocus();
                            }
                        });
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (queryEpoch.get() != epoch || isFinishing()) return;
                    showLoading(false);
                    showError();
                });
            }
        });
    }

    private void onResultSelected(SqlDbHelper.UnifiedSearchResult result) {
        String url = result.getUrl();
        String name = result.getName();
        PlaylistType type = result.getType();
        if (url == null || url.isEmpty()) return;

        if (type == PlaylistType.PLAYLIST) {
            startActivity(PlayerActivity.forPlaylist(this, url));
        } else if (type == PlaylistType.AUDIO) {
            startActivity(new Intent(this, AudioPlayerActivity.class)
                    .putExtra(PlayAudioStreamActivity.EXTRA_STREAM_URI, url)
                    .putExtra(PlayAudioStreamActivity.EXTRA_STREAM_NAME, name));
        } else {
            // LIVE or VOD
            StreamLauncher.launch(this, url, name, type == PlaylistType.LIVE);
        }
    }

    // --- State helpers ---

    private void showPrompt() {
        grid.setVisibility(View.GONE);
        loading.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        if (resultCount != null) resultCount.setVisibility(View.GONE);
        promptView.setVisibility(View.VISIBLE);
    }

    private void showLoading(boolean show) {
        loading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            grid.setVisibility(View.GONE);
            promptView.setVisibility(View.GONE);
            emptyView.setVisibility(View.GONE);
            errorView.setVisibility(View.GONE);
        }
    }

    private void showResults() {
        promptView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        loading.setVisibility(View.GONE);
        grid.setVisibility(View.VISIBLE);
    }

    private void showEmpty(String query) {
        grid.setVisibility(View.GONE);
        promptView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        loading.setVisibility(View.GONE);
        if (emptyBody != null) {
            emptyBody.setText(getString(R.string.search_no_results_body, query));
        }
        if (resultCount != null) resultCount.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
    }

    private void showError() {
        grid.setVisibility(View.GONE);
        promptView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        loading.setVisibility(View.GONE);
        if (resultCount != null) resultCount.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        if (db != null) db.close();
    }
}
