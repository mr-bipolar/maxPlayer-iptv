package com.aaronmaxlab.maxplayer;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.aaronmaxlab.maxplayer.navigates.SettingsFragment;

/** TV host for the shared settings fragment; phone navigation keeps using the nav graph. */
public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        findViewById(R.id.settings_back).setOnClickListener(v -> finish());
        if (state == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_fragment_container, new SettingsFragment())
                    .commit();
        }
    }
}
