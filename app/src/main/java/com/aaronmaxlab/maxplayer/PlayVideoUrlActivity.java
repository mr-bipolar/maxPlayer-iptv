package com.aaronmaxlab.maxplayer;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.aaronmaxlab.maxplayer.databinding.ActivityPlayVideoUrlBinding;
import com.aaronmaxlab.maxplayer.filepicker.FileDetailsActivity;
import com.aaronmaxlab.maxplayer.player.AudioPlayerActivity;
import com.aaronmaxlab.maxplayer.player.UrlPlayerActivity;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.StreamErrorUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.aaronmaxlab.maxplayer.utils.UrlUtils;

public class PlayVideoUrlActivity extends AppCompatActivity {

    ActivityPlayVideoUrlBinding  binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityPlayVideoUrlBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        // bottom nav set black
        UIUtils.setLightNavigationBars(this, false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // back button
        ClickUtils.applyTouchEffect(binding.backPlayVideoUrl, v -> finish());

        // play video
        ClickUtils.applyTouchEffect(binding.playVideoUrl, v -> {

            EditText urlBox = binding.videoUrlForPlay;
            EditText nameBox = binding.videoNameForPlay;
            LinearLayout playVideo = binding.playVideoUrl;

             String videoUrl = urlBox.getText().toString().trim();
             String videoName = nameBox.getText().toString();

            if(videoUrl.isEmpty()){
                urlBox.setError("Enter a video stream URL");
                urlBox.requestFocus();
                return;
            }

            if (!UrlUtils.isValidStreamUrl(videoUrl)) {
                urlBox.setError("Enter a valid HTTP or HTTPS URL");
                urlBox.requestFocus();
                return;
            }

            playVideo.setEnabled(false);

            java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                boolean reachable = UrlUtils.isStreamReachable(videoUrl);

                runOnUiThread(() -> {
                    playVideo.setEnabled(true);


                    if (!reachable) {
                        StreamErrorUtils.show(this);
                        return;
                    }

                    Intent intent = new Intent(this, UrlPlayerActivity.class);
                    intent.putExtra("playlist_uri", videoUrl);
                    intent.putExtra("playlist_file_name", videoName);
                    startActivity(intent);
                });
            });


        });
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}