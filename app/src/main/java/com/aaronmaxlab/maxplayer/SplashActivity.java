
package com.aaronmaxlab.maxplayer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.aaronmaxlab.maxplayer.databinding.ActivitySplashBinding;
import com.aaronmaxlab.maxplayer.utils.DeviceUtils;

public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    SharedPreferences sharedPreferences;
    private static final long SPLASH_DELAY = 3000L;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_AD_SHOWN = "ad_shown";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable splashRunnable = () -> {
        boolean isTV = DeviceUtils.isTv(this);

        if(!isTV){
            try {
                sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                boolean isAddShow = sharedPreferences.getBoolean(KEY_AD_SHOWN, false);
                if (isAddShow) {
                    sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean(KEY_AD_SHOWN, false);
                    editor.apply();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        finish();
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable edge-to-edge layout
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Inflate splash layout
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Hide system bars
        hideSystemBars();

        handler.postDelayed(splashRunnable, SPLASH_DELAY);
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(
                        getWindow(),
                        getWindow().getDecorView()
                );

        controller.hide(WindowInsetsCompat.Type.systemBars());

        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(splashRunnable);
        super.onDestroy();
        binding = null;
    }
}
