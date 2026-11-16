
package com.aaronmaxlab.maxplayer.ads;

import android.content.Context;
import android.util.Log;

import com.google.android.libraries.ads.mobile.sdk.MobileAds;
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig;

public final class AdMobManager {

    private static final String TAG = "AdMobManager";

    private static boolean initialized = false;
    private static boolean initializing = false;

    private AdMobManager() {}

    public interface InitializationListener {
        void onInitialized();
    }

    public static synchronized void initialize(
            Context context,
            InitializationListener listener
    ) {
        if (initialized) {
            listener.onInitialized();
            return;
        }

        if (initializing) {
            return;
        }

        initializing = true;

        new Thread(() -> {
            InitializationConfig config =
                    new InitializationConfig.Builder(AdConfig.AD_ROOT_ID).build();

            MobileAds.initialize(
                    context.getApplicationContext(),
                    config,
                    status -> {
                        synchronized (AdMobManager.class) {
                            initialized = true;
                            initializing = false;
                        }

                        listener.onInitialized();
                        Log.d(TAG, "MobileAds initialized");
                    }
            );
        }).start();
    }
}
