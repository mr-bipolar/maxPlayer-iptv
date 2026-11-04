package com.aaronmaxlab.maxplayer;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
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

import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.adapter.LocalDbChannelAdapter;
import com.aaronmaxlab.maxplayer.database.models.LocalDbChannel;
import com.aaronmaxlab.maxplayer.databinding.ActivityMainBinding;
import com.aaronmaxlab.maxplayer.keyboard.RemoteKeyboardActivity;
import com.aaronmaxlab.maxplayer.keyboard.RemotePairingActivity;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.DeviceUtils;
import com.aaronmaxlab.maxplayer.utils.StreamLauncher;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private SqlDbHelper sqlDbHelper;
    private LocalDbChannelAdapter channelAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);                       // before setContentView

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false);
        UIUtils.applySystemBarPadding(binding.getRoot(), false);


        sqlDbHelper = new SqlDbHelper(this);

        final boolean isTv =DeviceUtils.isTv(this);


        if(isTv){

            assert binding.tvAddPlaylist != null;
            ClickUtils.applyTouchEffect(binding.tvAddPlaylist, v -> {
                startActivity(new Intent(this, AddPlaylistActivity.class));
            });

            assert binding.remotePair != null;
            ClickUtils.applyTouchEffect(binding.remotePair, v -> {
                startActivity(new Intent(this, RemotePairingActivity.class));
            });

        }else {

            assert binding.bottomNavigation != null;
            UIUtils.adjustBottomNavGap(binding.bottomNavigation);

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


    private void showPlaybackCredits() {

        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_playback_credits);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent
            );

            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
            dialog.getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT
            );
        }

        TextView linkMpvAndroid = dialog.findViewById(R.id.linkMpvAndroid);
        TextView linkMpvEngine = dialog.findViewById(R.id.linkMpvEngine);
        Button btnClose = dialog.findViewById(R.id.btnCloseCredits);

        linkMpvAndroid.setOnClickListener(v ->
                openCreditUrl(
                        "https://github.com/mpv-android/mpv-android/releases/tag/2026-09-17"
                )
        );

        linkMpvEngine.setOnClickListener(v ->
                openCreditUrl(
                        "https://github.com/mr-bipolar/mpv-android-engine"
                )
        );

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);

            dialog.getWindow().setLayout(
                    width, WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void openCreditUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            AppToast.warning(this, "Unable to open link");
        }
    }

}