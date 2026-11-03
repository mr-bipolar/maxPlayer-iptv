package com.aaronmaxlab.maxplayer;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.aaronmaxlab.maxplayer.databinding.ActivityPlayAudioStreamBinding;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

public class PlayAudioStreamActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        ActivityPlayAudioStreamBinding binding = ActivityPlayAudioStreamBinding.inflate(getLayoutInflater());

        // bottom nav set black
        UIUtils.setLightNavigationBars(this, false);

        setContentView(binding.getRoot());



        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        ClickUtils.applyTouchEffect(binding.backPlayAudioStream, v -> finish());
    }
}