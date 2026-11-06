package com.aaronmaxlab.maxplayer;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aaronmaxlab.maxplayer.channels.AllChannelsActivity;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.TvSectionDataSource;
import com.aaronmaxlab.maxplayer.database.adapter.LocalDbChannelAdapter;
import com.aaronmaxlab.maxplayer.database.adapter.TvMainCardAdapter;
import com.aaronmaxlab.maxplayer.database.models.LocalDbCategory;
import com.aaronmaxlab.maxplayer.database.models.LocalDbChannel;
import com.aaronmaxlab.maxplayer.database.models.LocalDbHistory;
import com.aaronmaxlab.maxplayer.database.models.LocalDbPlaylist;
import com.aaronmaxlab.maxplayer.databinding.ActivityMainBinding;
import com.aaronmaxlab.maxplayer.PlayAudioStreamActivity;
import com.aaronmaxlab.maxplayer.favorites.TvFavoritesActivity;
import com.aaronmaxlab.maxplayer.keyboard.RemoteKeyboardActivity;
import com.aaronmaxlab.maxplayer.keyboard.RemotePairingActivity;
import com.aaronmaxlab.maxplayer.channels.ChannelActivity;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioPlayerActivity;
import com.aaronmaxlab.maxplayer.playlists.TvPlaylistsActivity;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.DeviceUtils;
import com.aaronmaxlab.maxplayer.utils.StreamLauncher;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private SqlDbHelper sqlDbHelper;
    private LocalDbChannelAdapter channelAdapter;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);                       // before setContentView

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false);
        UIUtils.applySystemBarPadding(binding.getRoot(), false);


        sqlDbHelper = new SqlDbHelper(this);

        boolean isTv = DeviceUtils.isTv(this);


        if(isTv){

            if(binding.tvSettingsButton != null) {
                ClickUtils.applyTouchEffect(binding.tvSettingsButton,
                        v -> startActivity(new Intent(this, SettingsActivity.class)));
            }
            if(binding.tvRemoteButton != null) {
                ClickUtils.applyTouchEffect(binding.tvRemoteButton,
                        v -> startActivity(new Intent(this, RemotePairingActivity.class)));
            }
            if(binding.tvFavoritesButton != null) {
                ClickUtils.applyTouchEffect(binding.tvFavoritesButton,
                        v -> startActivity(new Intent(this, TvFavoritesActivity.class)));
            }
            if(binding.tvImportButton != null) {
                ClickUtils.applyTouchEffect(binding.tvImportButton,
                        v -> startActivity(new Intent(this, AddPlaylistActivity.class)));
            }
            if(binding.tvLiveCard != null) {
                ClickUtils.applyTouchEffect(binding.tvLiveCard,
                        v -> startActivity(new Intent(this, AllChannelsActivity.class)));
            }
            if(binding.tvPlaylistsCard != null) {
                ClickUtils.applyTouchEffect(binding.tvPlaylistsCard,
                        v -> startActivity(new Intent(this, TvPlaylistsActivity.class)));
            }

            if(binding.tvExploreCard != null){
                ClickUtils.applyTouchEffect(binding.tvExploreCard,
                        v -> startActivity(new Intent(this, ExploreActivity.class)));
            }


        }else {

            assert binding.bottomNavigation != null;
            UIUtils.adjustBottomNavGap(binding.bottomNavigation);
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation, (v, insets) -> {
                androidx.core.graphics.Insets bars = insets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars());
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                int defaultMargin = v.getResources().getDimensionPixelSize(R.dimen.space_12);
                lp.bottomMargin = defaultMargin + bars.bottom;
                v.setLayoutParams(lp);
                return insets;
            });

            NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);
            if (navHost != null) {
                NavigationUI.setupWithNavController(binding.bottomNavigation, navHost.getNavController());
            }

            assert binding.searchChannel != null;
            ClickUtils.applyTouchEffect(binding.searchChannel, view -> showChannelsBottomSheet());

            assert binding.playerInfo != null;
            ClickUtils.applyTouchEffect(binding.playerInfo, view -> startActivity(new Intent(this, RemoteKeyboardActivity.class)));
        }

    }





    @Override protected void onResume() {
        super.onResume();
    }

    @Override protected void onDestroy() {
        if (sqlDbHelper != null) sqlDbHelper.close();
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
                    public void onChannelClick(LocalDbChannel channel) {
                        StreamLauncher.launch(MainActivity.this, channel.getChannelUrl(),
                                channel.getChannelName(), true);
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




}
