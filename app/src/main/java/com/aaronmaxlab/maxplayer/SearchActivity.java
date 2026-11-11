package com.aaronmaxlab.maxplayer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.adapter.LocalDbChannelAdapter;
import com.aaronmaxlab.maxplayer.database.models.LocalDbChannel;
import com.aaronmaxlab.maxplayer.databinding.ActivitySearchBinding;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioPlayerActivity;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.StreamLauncher;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SearchActivity extends AppCompatActivity {

    private static final int DEBOUNCE_MS = 300;

    private ActivitySearchBinding binding;
    private SqlDbHelper db;
    private LocalDbChannelAdapter adapter;

    private EditText searchInput;
    private ImageButton clearBtn;
    private RecyclerView recyclerView;
    private ProgressBar loading;
    private View promptView;
    private View emptyView;
    private TextView emptyBody;
    private View errorView;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger queryEpoch = new AtomicInteger(0);

    private Runnable pendingSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        UIUtils.setLightNavigationBars(this, false);
        UIUtils.applySystemBarPadding(binding.getRoot(), false);

        db = new SqlDbHelper(this);

        searchInput = binding.searchInput;
        clearBtn = binding.searchClearBtn;
        recyclerView = binding.searchRecyclerView;
        loading = binding.searchLoading;
        promptView = binding.searchPromptView;
        emptyView = binding.searchEmptyView;
        emptyBody = binding.searchEmptyBody;
        errorView = binding.searchErrorView;

        // Back button
        ClickUtils.applyTouchEffect(findViewById(R.id.searchBackBtn), v -> finish());

        // Clear button
        ClickUtils.applyTouchEffect(clearBtn, v -> {
            searchInput.setText("");
            searchInput.requestFocus();
        });

        // Adapter – reuse all channels (null = no category filter), no menu
        adapter = new LocalDbChannelAdapter(
                this,
                false,
                Collections.emptyList(),
                new LocalDbChannelAdapter.OnChannelClickListener() {
                    @Override
                    public void onChannelClick(LocalDbChannel channel, boolean isRadio) {
                        if (isRadio) {
                            startActivity(new Intent(SearchActivity.this, AudioPlayerActivity.class)
                                    .putExtra(PlayAudioStreamActivity.EXTRA_STREAM_URI, channel.getChannelUrl())
                                    .putExtra(PlayAudioStreamActivity.EXTRA_STREAM_NAME, channel.getChannelName()));
                        } else {
                            StreamLauncher.launch(SearchActivity.this,
                                    channel.getChannelUrl(), channel.getChannelName(), true);
                        }
                    }

                    @Override
                    public void onMenuClick(LocalDbChannel channel, View anchor) {
                        // No menu in search
                    }
                }
        );

        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter.setGridMode(true);
        recyclerView.setAdapter(adapter);

        // Text watcher with debounce
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                clearBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);

                if (pendingSearch != null) {
                    mainHandler.removeCallbacks(pendingSearch);
                }

                if (query.isEmpty()) {
                    showPrompt();
                    return;
                }

                pendingSearch = () -> runSearch(query);
                mainHandler.postDelayed(pendingSearch, DEBOUNCE_MS);
            }
        });

        // Show keyboard immediately
        searchInput.requestFocus();
        mainHandler.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        }, 150);

        showPrompt();
    }

    private void runSearch(String query) {
        final int epoch = queryEpoch.incrementAndGet();
        showLoading(true);

        executor.execute(() -> {
            try {
                // Use getChannels(null) to load all channels, then filter in-memory via adapter.search()
                // This avoids touching the DB on every keystroke for small to medium libraries.
                // For the initial load we fetch all channels once; subsequent searches reuse the list.
                List<LocalDbChannel> all = db.getChannels(null);

                mainHandler.post(() -> {
                    if (queryEpoch.get() != epoch || isFinishing()) return;
                    showLoading(false);

                    adapter = new LocalDbChannelAdapter(
                            this,
                            false,
                            all,
                            new LocalDbChannelAdapter.OnChannelClickListener() {
                                @Override
                                public void onChannelClick(LocalDbChannel channel, boolean isRadio) {
                                    if (isRadio) {
                                        startActivity(new Intent(SearchActivity.this, AudioPlayerActivity.class)
                                                .putExtra(PlayAudioStreamActivity.EXTRA_STREAM_URI, channel.getChannelUrl())
                                                .putExtra(PlayAudioStreamActivity.EXTRA_STREAM_NAME, channel.getChannelName()));
                                    } else {
                                        StreamLauncher.launch(SearchActivity.this,
                                                channel.getChannelUrl(), channel.getChannelName(), true);
                                    }
                                }

                                @Override
                                public void onMenuClick(LocalDbChannel channel, View anchor) {}
                            }
                    );
                    adapter.setGridMode(true);
                    recyclerView.setAdapter(adapter);
                    adapter.search(query);

                    int count = adapter.getItemCount();
                    if (count == 0) {
                        showEmpty(query);
                    } else {
                        showResults();
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

    // --- State helpers ---

    private void showPrompt() {
        recyclerView.setVisibility(View.GONE);
        loading.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        promptView.setVisibility(View.VISIBLE);
    }

    private void showLoading(boolean show) {
        loading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            recyclerView.setVisibility(View.GONE);
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
        recyclerView.setVisibility(View.VISIBLE);
    }

    private void showEmpty(String query) {
        recyclerView.setVisibility(View.GONE);
        promptView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        loading.setVisibility(View.GONE);
        if (emptyBody != null) {
            emptyBody.setText(getString(R.string.search_no_results_body, query));
        }
        emptyView.setVisibility(View.VISIBLE);
    }

    private void showError() {
        recyclerView.setVisibility(View.GONE);
        promptView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        loading.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (db != null) db.close();
        binding = null;
        super.onDestroy();
    }
}
