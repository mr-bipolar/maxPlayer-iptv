package com.aaronmaxlab.maxplayer;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.aaronmaxlab.maxplayer.databinding.ActivityPlayVideoUrlBinding;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

public class PlayVideoUrlActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        ActivityPlayVideoUrlBinding  binding = ActivityPlayVideoUrlBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        // bottom nav set black
        UIUtils.setLightNavigationBars(this, false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ClickUtils.applyTouchEffect(binding.backPlayVideoUrl, v -> finish());
        ClickUtils.applyTouchEffect(binding.playVideoUrl, v -> {
            final String videoUrl = binding.videoUrlForPlay.getText().toString();
            final String videoName = binding.videoNameForPlay.getText().toString();

            if(videoUrl.trim().isEmpty()){
                Toast.makeText(this, "Enter a video URL.", Toast.LENGTH_SHORT).show();
                return;
            }

            // later add this on local db and move to player
        });
    }
}