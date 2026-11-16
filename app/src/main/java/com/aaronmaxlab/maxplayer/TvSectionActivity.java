package com.aaronmaxlab.maxplayer;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.TvSectionDataSource;
import com.aaronmaxlab.maxplayer.database.adapter.TvMainCardAdapter;
import com.aaronmaxlab.maxplayer.database.models.LocalDbCategory;
import com.aaronmaxlab.maxplayer.database.models.LocalDbHistory;
import com.aaronmaxlab.maxplayer.database.models.LocalDbPlaylist;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioPlayerActivity;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;
import com.aaronmaxlab.maxplayer.utils.StreamLauncher;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full database-backed list for a TV home section. */
public class TvSectionActivity extends AppCompatActivity {
    public static final String EXTRA_KIND = "tv_section_kind";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SqlDbHelper db;
    private TvMainCardAdapter adapter;
    private TextView status;
    private ImageButton backBtn;
    private RecyclerView list;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_tv_section);
        db = new SqlDbHelper(this);
        list = findViewById(R.id.tvSectionRecycler);
        status = findViewById(R.id.tvSectionStatus);
        backBtn = findViewById(R.id.backButton);
        String kindName = getIntent().getStringExtra(EXTRA_KIND);
        TvMainCardAdapter.Kind kind;
        try { kind = TvMainCardAdapter.Kind.valueOf(kindName); }
        catch (Exception e) { finish(); return; }
        if (kind == TvMainCardAdapter.Kind.CATEGORY) {
            startActivity(new android.content.Intent(this, com.aaronmaxlab.maxplayer.categories.TvCategoriesActivity.class));
            finish();
            return;
        }
        ((TextView)findViewById(R.id.tvSectionTitle)).setText(TvSectionDataSource.titleRes(kind));
        list.setLayoutManager(new GridLayoutManager(this, 5));
        list.setFocusable(true);
        list.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        adapter = new TvMainCardAdapter(kind, new TvMainCardAdapter.Listener() {
            @Override public void onHistoryClick(LocalDbHistory item) { openHistory(item); }
            @Override public void onPlaylistClick(LocalDbPlaylist item) { startActivity(PlayerActivity.forPlaylist(TvSectionActivity.this, item.getPlaylistUrl())); }
            @Override public void onCategoryClick(LocalDbCategory item) {
                if (item.getId() != null) startActivity(com.aaronmaxlab.maxplayer.channels.ChannelActivity.forCategory(TvSectionActivity.this, item.getId(), item.getCategoryName()));
            }
            @Override public void onAddPlaylistClick() { }
            @Override public void onSeeMoreClick(TvMainCardAdapter.Kind ignored) { }
        }, R.dimen.tv_card_wide_h, false);
        list.setAdapter(adapter);
        onBackClick();
        load(kind);
    }

    private void load(TvMainCardAdapter.Kind kind) {
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.tv_loading);
        list.setVisibility(View.GONE);
        executor.execute(() -> {
            try {
                List<?> rows = TvSectionDataSource.load(db, kind, false);
                runOnUiThread(() -> {
                    adapter.submitList(rows);
                    status.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                    if (rows.isEmpty()) status.setText(R.string.tv_no_content);
                    else { list.setVisibility(View.VISIBLE); list.post(() -> { if (list.getChildCount() > 0) list.getChildAt(0).requestFocus(); }); }
                });
            } catch (Exception error) {
                runOnUiThread(() -> { status.setVisibility(View.VISIBLE); status.setText(R.string.tv_load_error); });
            }
        });
    }

    private void openHistory(LocalDbHistory item) {
        String url = item.getHistoryUrl();
        if (url == null || url.trim().isEmpty()) return;
        String type = item.getHistoryType();
        if (PlaylistType.PLAYLIST.getDisplayName().equalsIgnoreCase(type)) startActivity(PlayerActivity.forPlaylist(this, url));
        else if (PlaylistType.AUDIO.getDisplayName().equalsIgnoreCase(type)) startActivity(new android.content.Intent(this, AudioPlayerActivity.class).putExtra(PlayAudioStreamActivity.EXTRA_STREAM_URI, url).putExtra(PlayAudioStreamActivity.EXTRA_STREAM_NAME, item.getHistoryName()));
        else StreamLauncher.launch(this, url, item.getHistoryName(), PlaylistType.LIVE.getDisplayName().equalsIgnoreCase(type), PlaylistType.VOD.getDisplayName().equalsIgnoreCase(type) ? item.getHistoryPosition() : null);
    }

    private void onBackClick(){
        ClickUtils.applyTouchEffect(backBtn, view -> finish());
    }
    @Override protected void onDestroy() { executor.shutdownNow(); if (db != null) db.close(); super.onDestroy(); }
}
