package com.aaronmaxlab.maxplayer;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.aaronmaxlab.maxplayer.databinding.ActivitySettingsBinding;
import com.aaronmaxlab.maxplayer.navigates.SettingsFragment;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

/** TV host for the shared settings fragment; phone navigation keeps using the nav graph. */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false);
        UIUtils.applySystemBarPadding(binding.getRoot(), false);


        if (state == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(
                            R.id.settings_fragment_container,
                            new SettingsFragment()
                    )
                    .commit();
        }
    }
}
