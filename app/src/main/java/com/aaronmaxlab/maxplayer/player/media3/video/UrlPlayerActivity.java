package com.aaronmaxlab.maxplayer.player.media3.video;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.media3.ui.PlayerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.ActivityUrlPlayerBinding;
import com.aaronmaxlab.maxplayer.utils.AppToast;

public class UrlPlayerActivity extends BasePlayerActivity {

    private ActivityUrlPlayerBinding binding;
    private String streamUrl;

    @NonNull
    @Override
    protected View inflateContent() {
        binding = ActivityUrlPlayerBinding.inflate(getLayoutInflater());
        return binding.getRoot();
    }

    @NonNull
    @Override
    protected PlayerView getBoundPlayerView() {
        return binding.urlPlayerView;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        streamUrl = getIntent().getStringExtra("playlist_uri");
        if (TextUtils.isEmpty(streamUrl) || streamUrl.trim().isEmpty()) {
            AppToast.warning(this, "Stream URL not found");
            finish();
            return;
        }

        String fileName = getIntent().getStringExtra("playlist_file_name");
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = getString(R.string.video_channel);
        }
        setBanner(fileName, getString(R.string.streaming));

        connectPlayer();
    }

    @Override
    protected void onPlayerConnected() {
        ensureStreamPlaying(streamUrl);
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}