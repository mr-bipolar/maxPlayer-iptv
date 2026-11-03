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
import com.aaronmaxlab.maxplayer.utils.UIUtils;


public class AddPlaylistHelperActivity extends AppCompatActivity {

    private ActivityAddPlaylistHelperBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

         binding = ActivityAddPlaylistHelperBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        // bottom nav set black
        UIUtils.setLightNavigationBars(this, false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });



        // back press
        ClickUtils.applyTouchEffect(binding.backAddPlaylistHelper, v -> finish());

        // add playlist
        ClickUtils.applyTouchEffect(binding.helperAddPlaylistBtn, v -> {
            startActivity(new Intent(getBaseContext(), AddPlaylistActivity.class));
        });

        // play url
        ClickUtils.applyTouchEffect(binding.helperPlayaUrl, v -> {
            startActivity(new Intent(getBaseContext(), PlayVideoUrlActivity.class));
        });
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}