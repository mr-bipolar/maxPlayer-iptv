package com.aaronmaxlab.maxplayer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.aaronmaxlab.maxplayer.databinding.ActivityAddPlaylistBinding;
import com.aaronmaxlab.maxplayer.filepicker.FileDetailsActivity;
import com.aaronmaxlab.maxplayer.filepicker.MediaBrowserActivity;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.ads.AdConfig;
import com.aaronmaxlab.maxplayer.ads.AdMobManager;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.ads.BannerAdHelper;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.DeviceUtils;
import com.aaronmaxlab.maxplayer.utils.MediaTypes;
import com.aaronmaxlab.maxplayer.utils.Playlist;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import java.util.Locale;

public class AddPlaylistActivity extends AppCompatActivity {

    private static final String[] PLAYLIST_MIME_TYPES = {
            "audio/x-mpegurl",
            "application/vnd.apple.mpegurl",
            "audio/mpegurl",
            "audio/x-scpls",
            "text/plain",
            "application/octet-stream"
    };

    private static final String EXTRA_PLAYLIST_URI = "playlist_uri";
    private static final String EXTRA_PLAYLIST_FILE_NAME = "playlist_file_name";

    private boolean isTv;


    /** Launchers must be registered before the Activity is STARTED; */
    private final ActivityResultLauncher<Intent> mediaBrowserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onMediaBrowserResult);

    private final ActivityResultLauncher<String[]> playlistPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) handleSelectedPlaylist(uri, null);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        ActivityAddPlaylistBinding binding = ActivityAddPlaylistBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        isTv = DeviceUtils.isTv(this);

        UIUtils.setLightNavigationBars(this, false);
        UIUtils.applySystemBarPadding(binding.getRoot());

        // Back press
        ClickUtils.applyTouchEffect(binding.backAddPlaylist, v -> finish());
        ClickUtils.applyTouchEffect(binding.buttonOpenFile,
                v -> playlistPickerLauncher.launch(PLAYLIST_MIME_TYPES));
        // URL screens
        openOnClick(binding.buttonOpenPlaylistUrl, AddPlaylistUrlActivity.class);
        openOnClick(binding.buttonOpenVideoUrl, PlayVideoUrlActivity.class);
        // File / folder pickers
        ClickUtils.applyTouchEffect(binding.buttonOpenFolder,
                v -> mediaBrowserLauncher.launch(new Intent(this, MediaBrowserActivity.class)));

        if(!isTv){
            assert binding.buttonOpenAudioStream != null;
            openOnClick(binding.buttonOpenAudioStream, PlayAudioStreamActivity.class);
        }

        BannerAdHelper bannerAdHelper = new BannerAdHelper(binding.bannerAd, binding.adView);
        setupBannerAd(bannerAdHelper);

    }

    private void setupBannerAd( BannerAdHelper bannerAdHelper ) {
        if (isTv) return;
        AdMobManager.initialize(this, () -> {
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    bannerAdHelper.load(
                            this,
                            AdConfig.PLAYLIST_AD_UNIT_ID
                    );
                }
            });
        });
    }


    private void openOnClick(@NonNull View button, @NonNull Class<? extends Activity> target) {
        ClickUtils.applyTouchEffect(button, v -> startActivity(new Intent(this, target)));
    }


    // Results
    private void onMediaBrowserResult(@NonNull ActivityResult result) {
        Intent data = result.getData();
        if (result.getResultCode() != RESULT_OK || data == null) return;

        String uriString = data.getStringExtra(MediaBrowserActivity.EXTRA_MEDIA_URI);
        if (uriString == null) return;

        Uri uri = Uri.parse(uriString);
        String type = data.getStringExtra(MediaBrowserActivity.EXTRA_MEDIA_TYPE);

        if (MediaBrowserActivity.TYPE_PLAYLIST.equals(type)) {
            handleSelectedPlaylist(uri, data.getStringExtra(MediaBrowserActivity.EXTRA_MEDIA_NAME));
        } else if (MediaBrowserActivity.TYPE_VIDEO.equals(type)) {
            playLocalFile(uri, data.getStringExtra(MediaBrowserActivity.EXTRA_MEDIA_NAME));
        }
    }

    /** Playlist file or single media file: PlayerActivity.forLocalFile decides which. */
    private void playLocalFile(@NonNull Uri uri, @Nullable String displayName) {
        Intent player = PlayerActivity.forLocalFile(this, uri, displayName);
        if (player != null) startActivity(player);
        else AppToast.warning(this, "Unsupported file");
    }

    private void handleSelectedPlaylist(@NonNull Uri uri, @Nullable String displayName) {
        String fileName = Playlist.getFileName(this, uri);
        // The in-app TV browser returns an app-scoped file:// URI, which has no OpenableColumns
        // provider metadata. Its existing result contract also includes the display name.
        if (fileName == null && "file".equals(uri.getScheme())) {
            fileName = displayName != null ? displayName : uri.getLastPathSegment();
        }
        if (!isValidPlaylist(fileName)) {
            AppToast.warning(this, "Please select a valid playlist");
            return;
        }

        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException | UnsupportedOperationException ignored) {
            // The URI may still be readable for this session.
        }

        startActivity(new Intent(this, FileDetailsActivity.class)
                .putExtra(EXTRA_PLAYLIST_URI, uri.toString())
                .putExtra(EXTRA_PLAYLIST_FILE_NAME, fileName));
    }

    private static boolean isValidPlaylist(@Nullable String fileName) {
        if (fileName == null) return false;
        String ext = MediaTypes.extension(fileName);
        String lower = ext.toLowerCase(Locale.ROOT);
        return MediaTypes.PLAYLIST_EXT.contains(lower);
    }
}
