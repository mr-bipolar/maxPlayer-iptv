package com.aaronmaxlab.maxplayer;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.aaronmaxlab.maxplayer.databinding.ActivityPlayAudioStreamBinding;
import com.aaronmaxlab.maxplayer.player.AudioMiniPlayer;
import com.aaronmaxlab.maxplayer.player.AudioPlayerActivity;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.StreamErrorUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.aaronmaxlab.maxplayer.utils.UrlUtils;

public class PlayAudioStreamActivity extends AppCompatActivity {

    private ActivityPlayAudioStreamBinding binding;
    private AudioMiniPlayer audioMiniPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

         binding = ActivityPlayAudioStreamBinding.inflate(getLayoutInflater());

        // bottom nav set black
        UIUtils.setLightNavigationBars(this, false);

        setContentView(binding.getRoot());

        audioMiniPlayer = new AudioMiniPlayer(
                this,
                findViewById(R.id.audioPlayerOverlay)
        );

        audioMiniPlayer.attach();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // on back pressed
        ClickUtils.applyTouchEffect(binding.backPlayAudioStream, v -> finish());

        // play audio stream
        ClickUtils.applyTouchEffect(binding.playAudioPlayerStream, v -> {

            EditText urlInput = binding.audioStreamUrl;
            EditText nameInput = binding.audioStreamName;
            LinearLayout playAudioStream = binding.playAudioPlayerStream;

            String streamUrl = urlInput.getText().toString().trim();
            String streamName = nameInput.getText().toString().trim();

            if (streamUrl.isEmpty()) {
                urlInput.setError("Enter an audio stream URL");
                urlInput.requestFocus();
                return;
            }

            if (!UrlUtils.isValidStreamUrl(streamUrl)) {
                nameInput.setError("Enter a valid HTTP or HTTPS URL");
                nameInput.requestFocus();
                return;
            }

            playAudioStream.setEnabled(false);

            java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                boolean reachable = UrlUtils.isStreamReachable(streamUrl);

                runOnUiThread(() -> {
                    playAudioStream.setEnabled(true);

                    if (!reachable) {
                        StreamErrorUtils.show(this);
                        return;
                    }

                    Intent intent = new Intent(this, AudioPlayerActivity.class);
                    intent.putExtra("stream_uri", streamUrl);
                    intent.putExtra("stream_name", streamName);
                    startActivity(intent);
                });
            });
        });

    }

    @Override
    protected void onDestroy() {
        if (audioMiniPlayer != null) {
            audioMiniPlayer.release();
            audioMiniPlayer = null;
        }
        binding = null;
        super.onDestroy();
    }

}