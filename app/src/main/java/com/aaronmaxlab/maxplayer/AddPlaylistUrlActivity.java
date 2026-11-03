package com.aaronmaxlab.maxplayer;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.aaronmaxlab.maxplayer.animations.LoaderAnimator;
import com.aaronmaxlab.maxplayer.databinding.ActivityAddPlaylistUrlBinding;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.playlist.PlaylistParser;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistInfo;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.Playlist;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.aaronmaxlab.maxplayer.utils.UrlUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddPlaylistUrlActivity extends AppCompatActivity {

    /** One worker for the whole screen (the old code created a new, never-closed executor on every click). */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityAddPlaylistUrlBinding binding;

    /** Set only after a playlist was loaded successfully; "Open stream" uses it. */
    @Nullable private String loadedPlaylistUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityAddPlaylistUrlBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false);
        UIUtils.applySystemBarPadding(binding.getRoot());

        binding.errorCard.setVisibility(INVISIBLE);

        ClickUtils.applyTouchEffect(binding.backAddPlayListUrlOpen, v -> finish());
        ClickUtils.applyTouchEffect(binding.btnAddPlaylistCall, v -> onAddPlaylistClicked());
        ClickUtils.applyTouchEffect(binding.openPlaylistStream, v -> onOpenStreamClicked());
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }


    // Actions
    private void onAddPlaylistClicked() {
        String url = String.valueOf(binding.inputPlaylistUrl.getText()).trim();

        if (url.isEmpty()) {
            binding.inputPlaylistUrl.setError("Enter a playlist URL");
            binding.inputPlaylistUrl.requestFocus();
            return;
        }
        if (!UrlUtils.isValidPlaylistUrl(url))  {
            binding.inputPlaylistUrl.setError("Enter a valid HTTP or HTTPS URL");
            binding.inputPlaylistUrl.requestFocus();
            return;
        }

        loadedPlaylistUrl = null;
        showLoading();

        executor.execute(() -> {
            try {
                PlaylistInfo info = PlaylistParser.parseRemoteUrl(url, UIUtils.extractLabel(url));
                Playlist.loadRemoteMetadata(url, info);                 // populate name and size into 'info'
                runOnUiThread(() -> onLoaded(url, info));
            } catch (Exception e) {
                runOnUiThread(() -> onLoadFailed(e));
            }
        });
    }

    private void onOpenStreamClicked() {
        if (loadedPlaylistUrl == null) {
            AppToast.warning(this, "Please load a playlist first.");
            return;
        }
        startActivity(PlayerActivity.forPlaylist(this, loadedPlaylistUrl));
    }

    // UI states
    private void showLoading() {
        binding.loadingState.setVisibility(VISIBLE);
        binding.statesLabel.setVisibility(INVISIBLE);
        binding.errorCard.setVisibility(INVISIBLE);
        binding.successCard.setVisibility(GONE);
        binding.btnAddPlaylistCall.setEnabled(false);
    }

    private boolean isUiGone() {
        return isFinishing() || isDestroyed();     // the request can finish after the user left the screen
    }

    private void onLoaded(@NonNull String url, @NonNull PlaylistInfo info) {
        if (isUiGone()) return;

        binding.loadingState.setVisibility(GONE);
        binding.btnAddPlaylistCall.setEnabled(true);

        if (info.isPlaylist && info.channelCount >= 1) {
            loadedPlaylistUrl = url;
            binding.playlistAddedCount.setText(String.valueOf(info.channelCount));
            binding.playlistAddedCatCount.setText(String.valueOf(info.categoryNames.size()));
            binding.statesLabel.setVisibility(VISIBLE);
            binding.successCard.setVisibility(VISIBLE);
            AppToast.success(this, "Playlist added.");
        } else {
            AppToast.info(this, "It's not a playlist.");
        }
    }

    private void onLoadFailed(@NonNull Exception e) {
        if (isUiGone()) return;

        String message = (e.getMessage() != null ? e.getMessage() + "\n" : "")
                + getString(R.string.error_parse_playlist);

        binding.errorCard.setText(message);
        binding.errorCard.setVisibility(VISIBLE);
        binding.statesLabel.setVisibility(VISIBLE);
        binding.loadingState.setVisibility(GONE);
        binding.btnAddPlaylistCall.setEnabled(true);
    }

}