
package com.aaronmaxlab.maxplayer.ads;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.libraries.ads.mobile.sdk.banner.AdSize;
import com.google.android.libraries.ads.mobile.sdk.banner.AdView;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;


public final class BannerAdHelper {

    private static final String TAG = "BannerAdHelper";

    private final View adViewContainer;
    private final AdView adView;

    private boolean bannerLoadStarted = false;

    public BannerAdHelper(View adViewContainer, AdView adView) {
        this.adViewContainer = adViewContainer;
        this.adView = adView;
    }

    public void load(Activity activity, String adUnitId) {

        if (bannerLoadStarted) return;

        if (activity == null || activity.isFinishing()
                || activity.isDestroyed()) {
            return;
        }

        if (adViewContainer == null || adView == null) {
            Log.e(TAG, "Banner views are missing.");
            return;
        }

        bannerLoadStarted = true;
        adViewContainer.setVisibility(View.GONE);

        AdSize adSize =
                AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, 360);

        BannerAdRequest adRequest =
                new BannerAdRequest.Builder(adUnitId, adSize).build();

        adView.loadAd(adRequest, new AdLoadCallback<BannerAd>() {

            @Override
            public void onAdLoaded(@NonNull BannerAd bannerAd) {
                adViewContainer.post(() -> {
                    adViewContainer.setVisibility(View.VISIBLE);
                });

                Log.d(TAG, "Banner ad loaded.");
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                adViewContainer.post(() -> {
                    adViewContainer.setVisibility(View.GONE);
                });

                Log.e(TAG, "Banner ad failed: " + adError);
            }
        });
    }
}
