package com.aaronmaxlab.maxplayer;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.aaronmaxlab.maxplayer.databinding.ActivityPlayAudioStreamBinding;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioMiniPlayer;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioPlayerActivity;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.StreamErrorUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.aaronmaxlab.maxplayer.utils.UrlUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayAudioStreamActivity extends AppCompatActivity {

    public static final String EXTRA_STREAM_URI = "stream_uri";
    public static final String EXTRA_STREAM_NAME = "stream_name";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private ActivityPlayAudioStreamBinding binding;
    private AudioMiniPlayer audioMiniPlayer;
    private boolean isChecking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityPlayAudioStreamBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false); // black bottom nav
        UIUtils.applySystemBarPadding(binding.getRoot());

        audioMiniPlayer = new AudioMiniPlayer(
                this,
                binding.getRoot().findViewById(R.id.audioPlayerOverlay)
        );
        audioMiniPlayer.attach();

        ClickUtils.applyTouchEffect(binding.backPlayAudioStream, v -> finish());
        ClickUtils.applyTouchEffect(binding.playAudioPlayerStream, v -> onPlayClicked());
    }

    private void onPlayClicked() {
        if (isChecking) return;

        EditText urlInput = binding.audioStreamUrl;
        String streamUrl = urlInput.getText().toString().trim();
        String streamName = binding.audioStreamName.getText().toString().trim();

        if (!validateUrl(urlInput, streamUrl)) return;

        setChecking(true);
        ioExecutor.execute(() -> {
            boolean reachable = UrlUtils.isStreamReachable(streamUrl);
            runOnUiThread(() -> onReachabilityResult(reachable, streamUrl, streamName));
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

    private void onReachabilityResult(boolean reachable, String url, String name) {
        if (isFinishing() || isDestroyed()) return;

        setChecking(false);

        if (!reachable) {
            StreamErrorUtils.show(this);
            return;
        }

        startActivity(new Intent(this, AudioPlayerActivity.class)
                .putExtra(EXTRA_STREAM_URI, url)
                .putExtra(EXTRA_STREAM_NAME, name));
    }

    private void setChecking(boolean checking) {
        isChecking = checking;
        binding.playAudioPlayerStream.setEnabled(!checking);
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        if (audioMiniPlayer != null) {
            audioMiniPlayer.release();
            audioMiniPlayer = null;
        }
        binding = null;
        super.onDestroy();
    }
}