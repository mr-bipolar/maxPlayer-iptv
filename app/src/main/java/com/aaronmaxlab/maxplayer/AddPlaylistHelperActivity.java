package com.aaronmaxlab.maxplayer;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.aaronmaxlab.maxplayer.databinding.ActivityAddPlaylistHelperBinding;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;


public class AddPlaylistHelperActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        ActivityAddPlaylistHelperBinding binding = ActivityAddPlaylistHelperBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        //  Control navigation bar icon contrast
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightNavigationBars(false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });



        // back press
        ClickUtils.applyTouchEffect(binding.backAddPlaylistHelper, v -> finish());

        // add playlist
        ClickUtils.applyTouchEffect(binding.helperAddPlaylistBtn, v -> {
            Intent intent = new Intent(getBaseContext(), AddPlaylistActivity.class);
            startActivity(intent);
        });

        // play url
        ClickUtils.applyTouchEffect(binding.helperPlayaUrl, v -> {
            Intent intent = new Intent(getBaseContext(), PlayVideoUrlActivity.class);
            startActivity(intent);
        });
    }
}