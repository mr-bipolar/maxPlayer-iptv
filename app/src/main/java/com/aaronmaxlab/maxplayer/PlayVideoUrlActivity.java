package com.aaronmaxlab.maxplayer;

import static android.view.View.VISIBLE;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.aaronmaxlab.maxplayer.animations.LoaderAnimator;
import com.aaronmaxlab.maxplayer.databinding.ActivityPlayVideoUrlBinding;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.StreamErrorUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.aaronmaxlab.maxplayer.utils.UrlUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayVideoUrlActivity extends AppCompatActivity {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private ActivityPlayVideoUrlBinding binding;
    private boolean isChecking;
    private View loader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityPlayVideoUrlBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false); // black bottom nav
        UIUtils.applySystemBarPadding(binding.getRoot());

        loader = findViewById(R.id.loaderOverlay);

        ClickUtils.applyTouchEffect(binding.backPlayVideoUrl, v -> finish());
        ClickUtils.applyTouchEffect(binding.playVideoUrl, v -> onPlayClicked());
    }

    private void onPlayClicked() {
        if (isChecking) return;

        EditText urlBox = binding.videoUrlForPlay;
        String videoUrl = urlBox.getText().toString().trim();
        String videoName = binding.videoNameForPlay.getText().toString().trim();


        if (!validateUrl(urlBox, videoUrl)) return;

        LoaderAnimator.startLoader(loader);

        setChecking(true);
        ioExecutor.execute(() -> {
            boolean reachable = UrlUtils.isStreamReachable(videoUrl);
            boolean isStream = reachable && UrlUtils.isMediaStreamUrl(videoUrl);
            runOnUiThread(() -> onReachabilityResult(reachable, isStream, videoUrl, videoName));
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

    private void onReachabilityResult(boolean reachable, boolean isStream,
                                      String url, String name) {
        if (isFinishing() || isDestroyed()) return;

        setChecking(false);

        if (!reachable) {
            LoaderAnimator.stopLoader(loader);
            StreamErrorUtils.show(this);
            return;
        }

        startActivity(PlayerActivity.forStream(this, url, name, isStream));
        LoaderAnimator.stopLoader(loader);
    }

    private void setChecking(boolean checking) {
        isChecking = checking;
        binding.playVideoUrl.setEnabled(!checking);
    }





    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        binding = null;
        super.onDestroy();
    }
}