package com.aaronmaxlab.maxplayer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;


import com.aaronmaxlab.maxplayer.databinding.ActivityAddPlaylistHelperBinding;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

public class AddPlaylistHelperActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        ActivityAddPlaylistHelperBinding binding = ActivityAddPlaylistHelperBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false);
        UIUtils.applySystemBarPadding(binding.getRoot());

        ClickUtils.applyTouchEffect(binding.backAddPlaylistHelper, v -> finish());
        openOnClick(binding.helperAddPlaylistBtn, AddPlaylistActivity.class);
        openOnClick(binding.helperPlayaUrl, PlayVideoUrlActivity.class);
    }

    private void openOnClick(@NonNull View button, @NonNull Class<?> target) {
        ClickUtils.applyTouchEffect(button, v -> startActivity(new Intent(this, target)));
    }
}