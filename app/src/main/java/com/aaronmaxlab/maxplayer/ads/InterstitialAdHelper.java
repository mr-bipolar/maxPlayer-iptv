
package com.aaronmaxlab.maxplayer.ads;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;

public final class InterstitialAdHelper {

    private static final String TAG = "InterstitialAdHelper";
    private static boolean isShowing = false;
    private static final int AD_INTERVAL = 5;
    private static final String PREFS_NAME = "ad_preferences";
    private static final String COUNT_KEY = "interstitial_count";

    private InterstitialAdHelper() {
        // Utility class
    }

    public static void recordEvent(Activity activity) {

        if (activity == null ||
                activity.isFinishing() ||
                activity.isDestroyed()) {
            return;
        }

        android.content.SharedPreferences prefs =
                activity.getSharedPreferences(
                        PREFS_NAME,
                        android.content.Context.MODE_PRIVATE
                );

        int count = prefs.getInt(COUNT_KEY, 0);

        if (count < AD_INTERVAL) {
            count++;
            prefs.edit().putInt(COUNT_KEY, count).apply();
        }

        Log.d(TAG, "Ad event count: " + count);

        if (count >= AD_INTERVAL) {
            show(activity);
        }
    }

    private static void resetCount(Activity activity) {
        activity.getSharedPreferences(
                PREFS_NAME,
                android.content.Context.MODE_PRIVATE
        ).edit().putInt(COUNT_KEY, 0).apply();

        Log.d(TAG, "Ad counter reset.");
    }

    public static boolean isAdAvailable() {
        return InterstitialAdPreloader.isAdAvailable(
                AdConfig.VIDEO_AD_UNIT_ID
        );
    }

    public static void show(Activity activity) {

        if (activity == null ||
                activity.isFinishing() ||
                activity.isDestroyed()) {
            return;
        }

        if (isShowing) {
            Log.d(TAG, "Interstitial ad already showing.");
            return;
        }

        final InterstitialAd ad =
                InterstitialAdPreloader.pollAd(
                        AdConfig.VIDEO_AD_UNIT_ID
                );

        if (ad == null) {
            Log.d(TAG, "Interstitial ad is not available.");
            return;
        }

        Log.d(TAG, "Response info: " + ad.getResponseInfo());

        ad.setAdEventCallback(new InterstitialAdEventCallback() {

            @Override
            public void onAdShowedFullScreenContent() {
                isShowing = true;
                resetCount(activity);
                Log.d(TAG, "Interstitial ad shown.");
            }

            @Override
            public void onAdImpression() {
                Log.d(TAG, "Interstitial impression recorded.");
            }

            @Override
            public void onAdClicked() {
                Log.d(TAG, "Interstitial ad clicked.");
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                isShowing = false;
                Log.d(TAG, "Interstitial ad dismissed.");
            }

            @Override
            public void onAdFailedToShowFullScreenContent(
                    @NonNull FullScreenContentError error) {

                isShowing = false;

                Log.e(TAG, "Failed to show ad: "
                        + error.getMessage());
            }
        });

        try {
            ad.show(activity);
        } catch (Exception e) {
            isShowing = false;
            Log.e(TAG, "Unable to show interstitial ad.", e);
        }
    }
}
