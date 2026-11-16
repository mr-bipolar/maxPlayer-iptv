package com.aaronmaxlab.maxplayer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.categories.TvCategoriesActivity;
import com.aaronmaxlab.maxplayer.channels.AllChannelsActivity;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.adapter.LocalDbChannelAdapter;
import com.aaronmaxlab.maxplayer.database.models.LocalDbChannel;
import com.aaronmaxlab.maxplayer.databinding.ActivityMainBinding;
import com.aaronmaxlab.maxplayer.favorites.TvFavoritesActivity;
import com.aaronmaxlab.maxplayer.keyboard.RemotePairingActivity;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioPlayerActivity;
import com.aaronmaxlab.maxplayer.playlists.TvPlaylistsActivity;
import com.aaronmaxlab.maxplayer.search.TvSearchActivity;
import com.aaronmaxlab.maxplayer.ads.AdConfig;
import com.aaronmaxlab.maxplayer.ads.AdMobManager;
import com.aaronmaxlab.maxplayer.ads.BannerAdHelper;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.DeviceUtils;
import com.aaronmaxlab.maxplayer.utils.StreamLauncher;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd;
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdPreloader;
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;


import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private SqlDbHelper sqlDbHelper;
    private LocalDbChannelAdapter channelAdapter;
    private ActivityMainBinding binding;

    public static final String EXTRA_STREAM_URI = "stream_uri";
    public static final String EXTRA_STREAM_NAME = "stream_name";
    public static final String TAG = "Mobile-SDK";

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_AD_SHOWN = "ad_shown";
    private AppOpenAd appOpenAd;
    private BannerAdHelper bannerAdHelper;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        UIUtils.setLightNavigationBars(this, false);
        UIUtils.applySystemBarPadding(binding.getRoot(), false);
        sqlDbHelper = new SqlDbHelper(this);
        boolean isTv = DeviceUtils.isTv(this);


        if(isTv){
            ImageButton settingsBtn = binding.tvSettingsButton;
            ImageButton favoritesBtn = binding.tvFavoritesButton;
            ImageButton remoteBtn  = binding.tvRemoteButton;
            ImageButton searchBtn  = binding.tvSearchCard;

            LinearLayout importPlaylist = binding.tvImportButton;
            LinearLayout liveChannels = binding.tvLiveCard;
            LinearLayout playlists = binding.tvPlaylistsCard;
            LinearLayout exploreItems = binding.tvExploreCard;
            LinearLayout categories = binding.tvCategoriesCard;

            buttonCall(settingsBtn, SettingsActivity.class);
            buttonCall(favoritesBtn, TvFavoritesActivity.class);
            buttonCall(remoteBtn, RemotePairingActivity.class);
            buttonCall(searchBtn, TvSearchActivity.class);
            buttonCall(importPlaylist, AddPlaylistActivity.class);
            buttonCall(liveChannels, AllChannelsActivity.class);
            buttonCall(playlists, TvPlaylistsActivity.class);
            buttonCall(exploreItems, ExploreActivity.class);
            buttonCall(categories, TvCategoriesActivity.class);
        }else {
            View bottomBar = findViewById(R.id.customBottomBar);
            ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                lp.bottomMargin = bars.bottom;
                v.setLayoutParams(lp);
                return insets;
            });
            bannerAdHelper = new BannerAdHelper(binding.bannerAd, binding.adView);

            AdMobManager.initialize(this, () -> {
                runOnUiThread(() -> {
                    bannerAdHelper.load(this, AdConfig.BANNER_AD_UNIT_ID);

                    AdRequest adRequest = new AdRequest.Builder(AdConfig.APP_OPEN_AD_UNIT_ID).build();
                    PreloadConfiguration preloadConfig = new PreloadConfiguration(adRequest);
                    AppOpenAdPreloader.start(AdConfig.APP_OPEN_AD_UNIT_ID, preloadConfig);

                    // Interstitial preloader
                    AdRequest videoAdRequest =
                            new AdRequest.Builder(AdConfig.VIDEO_AD_UNIT_ID).build();

                    PreloadConfiguration videoPreloadConfig =
                            new PreloadConfiguration(videoAdRequest);

                    InterstitialAdPreloader.start(AdConfig.VIDEO_AD_UNIT_ID, videoPreloadConfig);

                    try {
                        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        boolean isAdShows = sharedPreferences.getBoolean(KEY_AD_SHOWN, false);
                        if (!isAdShows) {

                            AppOpenAd.load(adRequest, new AdLoadCallback<>() {
                                @Override
                                public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                                    AdLoadCallback.super.onAdFailedToLoad(adError);
                                }

                                @Override
                                public void onAdLoaded(AppOpenAd ad) {
                                    AdLoadCallback.super.onAdLoaded(ad);
                                    appOpenAd = ad;
                                    pollAndShowAd();
                                }
                            });

                        }
                    } catch (Exception e) {
                        Log.d("Error", Objects.requireNonNull(e.getMessage()));
                    }

                });
            });

            NavHostFragment navHost =
                    (NavHostFragment) getSupportFragmentManager()
                            .findFragmentById(R.id.nav_host_fragment);
            if (navHost == null) return;
            NavController navController = navHost.getNavController();
            ImageView searchBtn = binding.searchChannel;
            ImageView settingsBtn = binding.settings;
            buttonCall(searchBtn, SearchActivity.class);
            buttonCall(settingsBtn, SettingsActivity.class);
            setupNavItem(bottomBar, R.id.navHome, navController, R.id.navHome);
            setupNavItem(bottomBar, R.id.navPlaylists, navController, R.id.navPlaylists);
            setupNavItem(bottomBar, R.id.navFavorites, navController, R.id.navFavorites);
            setupNavItem(bottomBar, R.id.navHistory, navController, R.id.navHistory);

            if(bottomBar.findViewById(R.id.btnChannels) != null){
                ClickUtils.applyTouchEffect(bottomBar.findViewById(R.id.btnChannels), v -> showChannelsBottomSheet());
            }

            navController.addOnDestinationChangedListener(
                    (controller, destination, arguments) -> {

                        int destinationId = destination.getId();

                        bottomBar.findViewById(R.id.navHome).setSelected(
                                destinationId == R.id.navHome);

                        bottomBar.findViewById(R.id.navPlaylists).setSelected(
                                destinationId == R.id.navPlaylists);

                        bottomBar.findViewById(R.id.navFavorites).setSelected(
                                destinationId == R.id.navFavorites);

                        bottomBar.findViewById(R.id.navHistory).setSelected(
                                destinationId == R.id.navHistory);
                    });


        }


    }

    // Helper function
    private void buttonCall(View view, Class<?> callback) {
        if (view != null) {
            ClickUtils.applyTouchEffect(view,
                    v -> startActivity(new Intent(this, callback)));
        }
    }

    private void setupNavItem(View bottomBar, int viewId,
                              NavController navController, int destinationId) {
        View view = bottomBar.findViewById(viewId);
        if (view != null) {
            ClickUtils.applyTouchEffect(
                    view,
                    v -> navController.navigate(destinationId)
            );
        }
    }


    @Override protected void onResume() {
        super.onResume();
    }

    @Override protected void onDestroy() {
        if (sqlDbHelper != null) {
            sqlDbHelper.close();
            sqlDbHelper = null;
        }
        binding = null;
        super.onDestroy();
    }


    private void  showChannelsBottomSheet(){
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(R.layout.search_drawer);
        RecyclerView rvChannels = sheet.findViewById(R.id.channelsRecyclerView);
        if (rvChannels == null) return;

        setupChannelSearch(Objects.requireNonNull(sheet.findViewById(R.id.ChannelSearch)));

        List<LocalDbChannel> channels = sqlDbHelper.getChannels(null);

        channelAdapter = new LocalDbChannelAdapter(
                this,
                false,
                channels,
                new LocalDbChannelAdapter.OnChannelClickListener() {
                    @Override
                    public void onChannelClick(LocalDbChannel channel, boolean isRadio) {
                        if(isRadio){
                            startActivity(new Intent(MainActivity.this, AudioPlayerActivity.class)
                                    .putExtra(EXTRA_STREAM_URI, channel.getChannelUrl())
                                    .putExtra(EXTRA_STREAM_NAME, channel.getChannelName()));
                        }else {
                            StreamLauncher.launch(MainActivity.this, channel.getChannelUrl(),
                                    channel.getChannelName(), true);
                        }
                    }

                    @Override
                    public void onMenuClick(LocalDbChannel channel, View anchor) {

                    }

                }
        );


        GridLayoutManager layoutManager =
                new GridLayoutManager(this, 3);

        rvChannels.setLayoutManager(layoutManager);
        rvChannels.setHasFixedSize(true);

        channelAdapter.setGridMode(true);
        rvChannels.setAdapter(channelAdapter);
        sheet.setOnShowListener(d -> styleSheetNavigationBar(sheet));
        sheet.show();

    }

    private void styleSheetNavigationBar(BottomSheetDialog sheet) {
        Window window = sheet.getWindow();
        if (window != null) {
            window.setNavigationBarColor(ContextCompat.getColor(this, R.color.black));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(false);
                window.setNavigationBarDividerColor(Color.TRANSPARENT);
            }
            WindowCompat.getInsetsController(window, window.getDecorView()).setAppearanceLightNavigationBars(false);
        }
        View s = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (s != null) s.setBackgroundColor(Color.TRANSPARENT);
    }

    private void setupChannelSearch(TextInputEditText view) {

        view.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {

                if (channelAdapter != null) {
                    channelAdapter.search(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }



    // show ads
    private void pollAndShowAd() {
        if (appOpenAd != null && !sharedPreferences.getBoolean(KEY_AD_SHOWN, false)) {

            appOpenAd.setAdEventCallback(new AppOpenAdEventCallback() {
                @Override
                public void onAdClicked() {
                    AppOpenAdEventCallback.super.onAdClicked();
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    markAdAsShown();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError fullScreenContentError) {
                    markAdAsShown();
                }


                @Override
                public void onAdShowedFullScreenContent() {
                    markAdAsShown();
                }
            });

            // Show the ad if it's loaded
            appOpenAd.show(this);
        }
    }

    private void markAdAsShown() {
        try {
            sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(KEY_AD_SHOWN, true);
            editor.apply();
        } catch (Exception e) {
            Log.d(TAG, Objects.requireNonNull(e.getMessage()));
        }
    }


}
