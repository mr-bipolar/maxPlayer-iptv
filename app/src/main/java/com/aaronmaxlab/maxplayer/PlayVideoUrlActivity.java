package com.aaronmaxlab.maxplayer;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
import com.aaronmaxlab.maxplayer.databinding.ActivityPlayVideoUrlBinding;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.AppUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.DeviceUtils;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;
import com.aaronmaxlab.maxplayer.utils.StreamErrorUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.aaronmaxlab.maxplayer.utils.UrlUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayVideoUrlActivity extends AppCompatActivity {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private ActivityPlayVideoUrlBinding binding;
    private boolean isChecking;
    private View loader;
    private SqlDbHelper sqlDbHelper;
    private LocalDbRecentViewAdapter adapter;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityPlayVideoUrlBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        final  boolean isTv = DeviceUtils.isTv(this);
        sqlDbHelper = new SqlDbHelper(this);
        UIUtils.setLightNavigationBars(this, false); // black bottom nav
        UIUtils.applySystemBarPadding(binding.getRoot());

        if(!isTv){
            recyclerView = binding.recyclerRecentViews;
            loader = findViewById(R.id.loaderOverlay);
            setupRecentViews(sqlDbHelper);
        }

        ClickUtils.applyTouchEffect(binding.backPlayVideoUrl, v -> finish());
        ClickUtils.applyTouchEffect(binding.playVideoUrl, v -> onPlayClicked());
    }

    private void onPlayClicked() {
        if (isChecking) return;

        EditText urlBox = binding.videoUrlForPlay;
        String videoUrl = urlBox.getText().toString().trim();
        String inputName = binding.videoNameForPlay.getText().toString().trim();

        final String videoName = inputName.isEmpty()
                ? UIUtils.extractLabel(videoUrl)
                : inputName;

        if (!validateUrl(urlBox, videoUrl)) return;

        LoaderAnimator.startLoader(loader);

        setChecking(true);
        ioExecutor.execute(() -> {
            boolean reachable = UrlUtils.isStreamReachable(videoUrl);
            runOnUiThread(() -> onReachabilityResult(reachable, videoUrl, videoName, true));
        });
    }

    private boolean validateUrl(@NonNull EditText input, @NonNull String url) {
        if (url.isEmpty()) {
            showError(input, "Enter a video stream URL");
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

    private void onReachabilityResult(
            boolean reachable,
            String url,
            String name,
            boolean saveRecentView) {

        if (isFinishing() || isDestroyed()) return;

        setChecking(false);

        if (!reachable) {
            LoaderAnimator.stopLoader(loader);
            StreamErrorUtils.show(this);
            return;
        }

        startActivity(PlayerActivity.forStream(this, url, name, null));
        LoaderAnimator.stopLoader(loader);

        if (saveRecentView) {
            LocalDbRecentView recentView =
                    new LocalDbRecentView(name, url, PlaylistType.VOD, null);

            addRecentView(recentView);
        }
    }

    private void setChecking(boolean checking) {
        isChecking = checking;
        binding.playVideoUrl.setEnabled(!checking);
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
                            boolean isStream =
                                    reachable && UrlUtils.isMediaStreamUrl(url);

                            runOnUiThread(() ->
                                    onReachabilityResult(
                                            reachable,
                                            url,
                                            name,
                                            false
                                    )
                            );
                        });

                    }

                    @Override
                    public void onFavoriteClick(LocalDbRecentView item) {
                        // Toggle favorite in your database.
                        LocalDbFavorites favorites = new LocalDbFavorites(item.getChannelName(), item.getChannelType(), item.getChannelUrl(), item.getChannelIcon(), PlaylistType.VOD);
                        boolean result =  sqlDbHelper.setFavorite(favorites);

                        if(result){
                            boolean isFav = sqlDbHelper.isFavorite(item.getChannelUrl());
                            AppToast.success( PlayVideoUrlActivity.this,  isFav ?"Added to favorites." : "Removed from favorites.");
                            ImageView favButton = findViewById(R.id.mediaFav);
                            if (favButton != null) {
                                favButton.setImageResource(isFav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
                            }

                        }
                    }

                    @Override
                    public void onDeleteClick(LocalDbRecentView item) {

                        AppUtils.showDeleteConfirmation(
                                PlayVideoUrlActivity.this,
                                item.getChannelName(),
                                () -> {
                                    try {
                                        boolean result =
                                                sqlDbHelper.removeRecentItem(item.getId());

                                        if (result) {
                                            refreshRecentViews();
                                            AppToast.success(
                                                    PlayVideoUrlActivity.this,
                                                    "Recent view deleted."
                                            );
                                        } else {
                                            AppToast.error(
                                                    PlayVideoUrlActivity.this,
                                                    "Delete failed."
                                            );
                                        }

                                    } catch (Exception e) {
                                        Log.e("RECENT_VIEW", "Delete failed", e);

                                        AppToast.error(
                                                PlayVideoUrlActivity.this,
                                                "Delete failed."
                                        );
                                    }
                                }
                        );
                    }
                }
        );

        if(recyclerView != null){
            recyclerView.setLayoutManager(
                    new LinearLayoutManager(this)
            );
            recyclerView.setAdapter(adapter);
        }
        refreshRecentViews();
    }

    private void refreshRecentViews() {
        try {
            List<LocalDbRecentView> recentViews =
                    sqlDbHelper.getAllRecentViews(PlaylistType.VOD);
            if (binding.recentLabel != null) {
                binding.recentLabel.setVisibility(
                        recentViews.isEmpty() ? View.GONE : View.VISIBLE
                );
            }
            adapter.submitList(recentViews);
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
        if (sqlDbHelper != null) {
            sqlDbHelper.close();
            sqlDbHelper = null;
        }
        binding = null;
        super.onDestroy();
    }
}