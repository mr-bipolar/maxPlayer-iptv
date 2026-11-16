package com.aaronmaxlab.maxplayer;

import static android.view.View.VISIBLE;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.animations.LoaderAnimator;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.adapter.LocalDbRecentViewAdapter;
import com.aaronmaxlab.maxplayer.database.models.LocalDbFavorites;
import com.aaronmaxlab.maxplayer.database.models.LocalDbRecentView;
import com.aaronmaxlab.maxplayer.databinding.ActivityPlayAudioStreamBinding;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioMiniPlayer;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioPlayerActivity;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.AppUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;
import com.aaronmaxlab.maxplayer.utils.StreamErrorUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.aaronmaxlab.maxplayer.utils.UrlUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayAudioStreamActivity extends AppCompatActivity {

    public static final String EXTRA_STREAM_URI = "stream_uri";
    public static final String EXTRA_STREAM_NAME = "stream_name";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private ActivityPlayAudioStreamBinding binding;
    private AudioMiniPlayer audioMiniPlayer;
    private boolean isChecking;
    private View loader;
    private SqlDbHelper sqlDbHelper;
    private LocalDbRecentViewAdapter adapter;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityPlayAudioStreamBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        loader = findViewById(R.id.loaderOverlay);
        recyclerView = binding.recyclerViewStream;
        sqlDbHelper = new SqlDbHelper(this);
        setupRecentViews(sqlDbHelper);

        UIUtils.setLightNavigationBars(this, false); // black bottom nav
        UIUtils.applySystemBarPadding(binding.getRoot());

        audioMiniPlayer = new AudioMiniPlayer(
                this,
                binding.getRoot().findViewById(R.id.audioPlayerOverlay)
        );
        audioMiniPlayer.attach();

        // audio player bottom margin
        View view = binding.audioPlayerOverlay.miniPlayerContainer;
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        int margin16dp = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        params.setMargins(margin16dp, margin16dp, margin16dp, margin16dp);
        view.setLayoutParams(params);

        ClickUtils.applyTouchEffect(binding.backPlayAudioStream, v -> finish());
        ClickUtils.applyTouchEffect(binding.playAudioPlayerStream, v -> onPlayClicked());
    }

    private void onPlayClicked() {
        if (isChecking) return;

        EditText urlInput = binding.audioStreamUrl;
        String streamUrl = urlInput.getText().toString().trim();
        String inputName = binding.audioStreamName.getText().toString().trim();

        final String streamName = inputName.isEmpty()
                ? UIUtils.extractLabel(streamUrl)
                : inputName;

        if (!validateUrl(urlInput, streamUrl)) return;

        LoaderAnimator.startLoader(loader);

        setChecking(true);
        ioExecutor.execute(() -> {
            boolean reachable = UrlUtils.isStreamReachable(streamUrl);
            runOnUiThread(() -> onReachabilityResult(reachable, streamUrl, streamName, true));
        });
    }

    private boolean validateUrl(@NonNull EditText input, @NonNull String url) {
        if (url.isEmpty()) {
            showError(input, "Enter an audio stream URL");
            return false;
        }
        if (!UrlUtils.isValidStreamUrl(url)) {
            showError(input, "Enter a valid HTTP or HTTPS URL");
            return false;
        }
        return true;
    }

    private static void showError(@NonNull EditText input, @NonNull String message) {
        input.setError(message);
        input.requestFocus();
    }

    private void onReachabilityResult(boolean reachable, String url, String name,  boolean saveRecentView) {
        if (isFinishing() || isDestroyed()) return;

        setChecking(false);

        if (!reachable) {
            LoaderAnimator.stopLoader(loader);
            StreamErrorUtils.show(this);
            return;
        }

        startActivity(new Intent(this, AudioPlayerActivity.class)
                .putExtra(EXTRA_STREAM_URI, url)
                .putExtra(EXTRA_STREAM_NAME, name));
        LoaderAnimator.stopLoader(loader);

        if (saveRecentView) {
            LocalDbRecentView recentView =
                    new LocalDbRecentView(name, url, PlaylistType.AUDIO, null);
            addRecentView(recentView);
        }
    }

    private void setChecking(boolean checking) {
        isChecking = checking;
        binding.playAudioPlayerStream.setEnabled(!checking);
    }


    private void setupRecentViews(SqlDbHelper helper) {
        this.sqlDbHelper = helper;

        adapter = new LocalDbRecentViewAdapter(
                this,
                new LocalDbRecentViewAdapter.Listener() {
                    @Override
                    public void onItemClick(LocalDbRecentView item) {
                        if (isChecking) return;
                        String url = item.getChannelUrl();
                        String name = item.getChannelName();
                        LoaderAnimator.startLoader(loader);
                        setChecking(true);
                        ioExecutor.execute(() -> {
                            boolean reachable = UrlUtils.isStreamReachable(url);
                            runOnUiThread(() -> onReachabilityResult(reachable, url, name, false)
                            );
                        });

                    }

                    @Override
                    public void onFavoriteClick(LocalDbRecentView item) {

                        LocalDbFavorites favorites = new LocalDbFavorites(item.getChannelName(), "Internet radio", item.getChannelUrl(),
                                null, PlaylistType.AUDIO
                        );

                        boolean result = sqlDbHelper.setFavorite(favorites);
                        if (result) {
                            boolean isFav = sqlDbHelper.isFavorite(item.getChannelUrl());
                            AppToast.success(
                                    PlayAudioStreamActivity.this, isFav ? "Added to favorites." : "Removed from favorites.");

                            ImageView favButton = findViewById(R.id.mediaFav);
                            if (favButton != null) {
                                favButton.setImageResource(isFav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
                            }
                        }
                    }

                    @Override
                    public void onDeleteClick(LocalDbRecentView item) {

                        AppUtils.showDeleteConfirmation(
                                PlayAudioStreamActivity.this,
                                item.getChannelName(),
                                () -> {
                                    try {
                                        boolean result =
                                                sqlDbHelper.removeRecentItem(item.getId());

                                        if (result) {
                                            refreshRecentViews();
                                            AppToast.success(
                                                    PlayAudioStreamActivity.this,
                                                    "Recent view deleted."
                                            );
                                        } else {
                                            AppToast.error(
                                                    PlayAudioStreamActivity.this,
                                                    "Delete failed."
                                            );
                                        }

                                    } catch (Exception e) {
                                        Log.e("RECENT_VIEW", "Delete failed", e);
                                        AppToast.error(
                                                PlayAudioStreamActivity.this,
                                                "Delete failed."
                                        );
                                    }
                                }
                        );
                    }
                }
        );

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        refreshRecentViews();
    }

    private void refreshRecentViews() {
        try {
            List<LocalDbRecentView> recentViews =
                    sqlDbHelper.getAllRecentViews(PlaylistType.AUDIO);

            binding.recentLabel.setVisibility( recentViews.isEmpty() ? View.GONE : VISIBLE);

            adapter.submitList(recentViews);
            Log.d("RECENT_VIEW", "Loaded recent views: " + recentViews.size());

        } catch (Exception e) {
            Log.e("RECENT_VIEW", "Failed to load recent views", e);
        }
    }

    private void addRecentView(LocalDbRecentView recentView) {
        try {
            sqlDbHelper.setRecentViews(recentView);
            // Reload the RecyclerView after inserting.
            refreshRecentViews();
        } catch (Exception e) {
            Log.e("RECENT_VIEW", "Failed to add recent view", e);
        }
    }


    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        if (audioMiniPlayer != null) {
            audioMiniPlayer.release();
            audioMiniPlayer = null;
        }
        if (sqlDbHelper != null) {
            sqlDbHelper.close();
            sqlDbHelper = null;
        }
        binding = null;
        super.onDestroy();
    }
}