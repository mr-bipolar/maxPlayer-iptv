package com.aaronmaxlab.maxplayer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.channels.ChannelActivity;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.TvSectionDataSource;
import com.aaronmaxlab.maxplayer.database.adapter.TvMainCardAdapter;
import com.aaronmaxlab.maxplayer.database.models.LocalDbCategory;
import com.aaronmaxlab.maxplayer.database.models.LocalDbHistory;
import com.aaronmaxlab.maxplayer.database.models.LocalDbPlaylist;
import com.aaronmaxlab.maxplayer.databinding.ActivityExploreBinding;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioPlayerActivity;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.DeviceUtils;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;
import com.aaronmaxlab.maxplayer.utils.StreamLauncher;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExploreActivity extends AppCompatActivity {

    private ActivityExploreBinding binding;
    private SqlDbHelper sqlDbHelper;
    private TvMainCardAdapter historyTvAdapter;
    private TvMainCardAdapter playlistTvAdapter;
    private TvMainCardAdapter categoryTvAdapter;
    private boolean initialTvFocusAssigned;
    private boolean isTv;
    private final ExecutorService tvDataExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityExploreBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        UIUtils.applySystemBarPadding(binding.getRoot(), false);
        sqlDbHelper = new SqlDbHelper(this);
        isTv = DeviceUtils.isTv(this);
        setupTvRows();
        ClickUtils.applyTouchEffect(binding.backExploreBtn, view -> finish());
    }


    @Override protected void onResume() {
        super.onResume();
        if (isTv && historyTvAdapter != null) loadTvData();
    }

    @Override protected void onDestroy() {
        tvDataExecutor.shutdownNow();
        if (sqlDbHelper != null) {
            sqlDbHelper.close();
            sqlDbHelper = null;
        }
        binding = null;
        super.onDestroy();
    }


        private void setupTvRows() {
        RecyclerView history = binding.tvHistoryRecycler;
        RecyclerView playlists = binding.tvPlaylistRecycler;
        RecyclerView categories = binding.tvCategoryRecycler;
        setupHorizontalRow(history);
        setupHorizontalRow(playlists);
        setupHorizontalRow(categories);

        TvMainCardAdapter.Listener listener = new TvMainCardAdapter.Listener() {
            @Override public void onHistoryClick(LocalDbHistory item) {
                openHistoryItem(item);
            }
            @Override public void onPlaylistClick(LocalDbPlaylist item) {
                startActivity(PlayerActivity.forPlaylist(ExploreActivity.this, item.getPlaylistUrl()));
            }
            @Override public void onCategoryClick(LocalDbCategory item) {
                int categoryId = item.getId() == null ? -1 : item.getId();
                if (categoryId != -1) startActivity(ChannelActivity.forCategory(ExploreActivity.this, categoryId, item.getCategoryName()));
            }
            @Override public void onAddPlaylistClick() {
                startActivity(new Intent(ExploreActivity.this, AddPlaylistActivity.class));
            }
            @Override public void onSeeMoreClick(TvMainCardAdapter.Kind kind) {
                openTvFullList(kind);
            }
        };

        historyTvAdapter = new TvMainCardAdapter(TvMainCardAdapter.Kind.HISTORY, listener, R.dimen.tv_card_wide_h);
        playlistTvAdapter = new TvMainCardAdapter(TvMainCardAdapter.Kind.PLAYLIST, listener, R.dimen.tv_card_h);
        categoryTvAdapter = new TvMainCardAdapter(TvMainCardAdapter.Kind.CATEGORY, listener, R.dimen.tv_card_h);
        history.setAdapter(historyTvAdapter);
        playlists.setAdapter(playlistTvAdapter);
        categories.setAdapter(categoryTvAdapter);
    }

    private void openTvFullList(TvMainCardAdapter.Kind kind) {
        if (kind == TvMainCardAdapter.Kind.CATEGORY) {
            startActivity(new Intent(this, com.aaronmaxlab.maxplayer.categories.TvCategoriesActivity.class));
        } else {
            startActivity(new Intent(this, TvSectionActivity.class).putExtra(TvSectionActivity.EXTRA_KIND, kind.name()));
        }
    }

    private void setupHorizontalRow(RecyclerView recyclerView) {
        recyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setFocusable(false);
        recyclerView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

        private void loadTvData() {
        if (!isTv || isFinishing()) return;
        setTvLoadingState();
        View focusedView = getCurrentFocus();
        RecyclerView focusedRow = focusedView != null && focusedView.getParent() instanceof RecyclerView
                ? (RecyclerView) focusedView.getParent() : null;
        int focusedPosition = focusedRow == null ? RecyclerView.NO_POSITION
                : focusedRow.getChildAdapterPosition(focusedView);
        tvDataExecutor.execute(() -> {
          try {
            List<?> history = TvSectionDataSource.load(sqlDbHelper, TvMainCardAdapter.Kind.HISTORY, true);
            List<?> playlists = TvSectionDataSource.load(sqlDbHelper, TvMainCardAdapter.Kind.PLAYLIST, true);
            List<?> categories = TvSectionDataSource.load(sqlDbHelper, TvMainCardAdapter.Kind.CATEGORY, true);
            mainHandler.post(() -> {
                if (isFinishing() || binding == null) return;
                historyTvAdapter.submitPreview(history, TvSectionDataSource.MAIN_SCREEN_LIMIT);
                playlistTvAdapter.submitPreview(playlists, TvSectionDataSource.MAIN_SCREEN_LIMIT);
                categoryTvAdapter.submitPreview(categories, TvSectionDataSource.MAIN_SCREEN_LIMIT);
                binding.tvHistorySection.setVisibility(history.isEmpty() ? View.GONE : View.VISIBLE);
                binding.tvPlaylistSection.setVisibility(playlists.isEmpty() ? View.GONE : View.VISIBLE);
                binding.tvCategorySection.setVisibility(categories.isEmpty() ? View.GONE : View.VISIBLE);
                binding.tvHistoryRecyclerEmpty.setVisibility(View.GONE);
                binding.tvPlaylistRecyclerEmpty.setVisibility(View.GONE);
                binding.tvCategoryRecyclerEmpty.setVisibility(View.GONE);
                boolean allEmpty = history.isEmpty() && playlists.isEmpty() && categories.isEmpty();
                binding.exploreEmptyView.setVisibility(allEmpty ? View.VISIBLE : View.GONE);
                if (focusedRow != null && focusedPosition != RecyclerView.NO_POSITION) {
                    RecyclerView restoreRow = focusedRow;
                    int restorePosition = focusedPosition;
                    restoreRow.post(() -> {
                        if (binding != null && !binding.getRoot().hasFocus()) {
                            if(restoreRow.getAdapter() != null) {
                                if (restorePosition < restoreRow.getAdapter().getItemCount()
                                        && restoreRow.findViewHolderForAdapterPosition(restorePosition) != null) {
                                    Objects.requireNonNull(restoreRow.findViewHolderForAdapterPosition(restorePosition)).itemView.requestFocus();
                                }
                            }
                        }
                    });
                }
                if (!initialTvFocusAssigned) binding.tvHistoryRecycler.post(() -> {
                    if (binding == null || initialTvFocusAssigned) return;
                    RecyclerView target = !history.isEmpty() ? binding.tvHistoryRecycler
                            : !playlists.isEmpty() ? binding.tvPlaylistRecycler
                            : !categories.isEmpty() ? binding.tvCategoryRecycler : null;
                    if (target != null && target.getChildCount() > 0) {
                        target.getChildAt(0).requestFocus();
                        initialTvFocusAssigned = true;
                    }
                });
            });
          } catch (Exception error) {
            mainHandler.post(() -> {
                if (binding == null || isFinishing()) return;
                binding.tvHistorySection.setVisibility(View.VISIBLE);
                binding.tvPlaylistSection.setVisibility(View.VISIBLE);
                binding.tvCategorySection.setVisibility(View.VISIBLE);
                showTvLoadError(binding.tvHistoryRecyclerEmpty);
                showTvLoadError(binding.tvPlaylistRecyclerEmpty);
                showTvLoadError(binding.tvCategoryRecyclerEmpty);
            });
          }
        });
    }

    private void setTvLoadingState() {
        binding.tvHistoryRecyclerEmpty.setText(R.string.tv_loading);
        binding.tvPlaylistRecyclerEmpty.setText(R.string.tv_loading);
        binding.tvCategoryRecyclerEmpty.setText(R.string.tv_loading);
        binding.tvHistoryRecyclerEmpty.setVisibility(View.VISIBLE);
        binding.tvPlaylistRecyclerEmpty.setVisibility(View.VISIBLE);
        binding.tvCategoryRecyclerEmpty.setVisibility(View.VISIBLE);
    }


    private void showTvLoadError(TextView status) {
        status.setText(R.string.tv_load_error);
        status.setVisibility(View.VISIBLE);
    }

    private void openHistoryItem(LocalDbHistory item) {
        String type = item.getHistoryType();
        String url = item.getHistoryUrl();
        if (url == null || url.trim().isEmpty()) return;
        if (PlaylistType.PLAYLIST.getDisplayName().equalsIgnoreCase(type)) {
            startActivity(PlayerActivity.forPlaylist(this, url));
        } else if (PlaylistType.AUDIO.getDisplayName().equalsIgnoreCase(type)) {
            startActivity(new Intent(this, AudioPlayerActivity.class)
                    .putExtra(PlayAudioStreamActivity.EXTRA_STREAM_URI, url)
                    .putExtra(PlayAudioStreamActivity.EXTRA_STREAM_NAME, item.getHistoryName()));
        } else {
            Long position = PlaylistType.VOD.getDisplayName().equalsIgnoreCase(type) ? item.getHistoryPosition() : null;
            StreamLauncher.launch(this, url, item.getHistoryName(),
                    PlaylistType.LIVE.getDisplayName().equalsIgnoreCase(type), position);
        }
    }
}