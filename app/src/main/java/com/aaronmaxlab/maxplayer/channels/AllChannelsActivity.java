package com.aaronmaxlab.maxplayer.channels;

import android.os.Bundle;
import android.view.View;
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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AllChannelsActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private RecyclerView grid;
    private ProgressBar loading;
    private TextView message;
    private TextView count;
    private ChannelGridAdapter adapter;
    private volatile boolean destroyed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_all_channels);
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
        grid.setFocusable(true);
        adapter = new ChannelGridAdapter(channel -> {
            String url = channel.getChannelUrl();
            if (url != null && !url.trim().isEmpty()) {
                startActivity(PlayerActivity.forStream(this, url, channel.getChannelName(), true));
            }
        });
        grid.setAdapter(adapter);
        loadChannels();
    }

    private void loadChannels() {
        loading.setVisibility(View.VISIBLE);
        grid.setVisibility(View.GONE);
        message.setVisibility(View.GONE);
        executor.execute(() -> {
            List<LocalDbChannel> result;
            try {
                result = new SqlDbHelper(getApplicationContext()).getChannels(null);
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
        super.onDestroy();
    }
}
