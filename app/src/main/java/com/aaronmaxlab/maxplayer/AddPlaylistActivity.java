package com.aaronmaxlab.maxplayer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.aaronmaxlab.maxplayer.databinding.ActivityAddPlaylistBinding;
import com.aaronmaxlab.maxplayer.filepicker.FileDetailsActivity;
import com.aaronmaxlab.maxplayer.filepicker.MediaBrowserActivity;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.Playlist;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import java.util.Locale;

public class AddPlaylistActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> mediaBrowserLauncher;
    private  ActivityAddPlaylistBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityAddPlaylistBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        // Navbar setup
        UIUtils.setLightNavigationBars(this, false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // File pickers
        mediaBrowserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK
                            || result.getData() == null) {
                        return;
                    }

                    Intent data = result.getData();

                    String uriString = data.getStringExtra(
                            MediaBrowserActivity.EXTRA_MEDIA_URI
                    );

                    String name = data.getStringExtra(
                            MediaBrowserActivity.EXTRA_MEDIA_NAME
                    );

                    String type = data.getStringExtra(
                            MediaBrowserActivity.EXTRA_MEDIA_TYPE
                    );

                    if (uriString == null) {
                        return;
                    }

                    Uri mediaUri = Uri.parse(uriString);

                   if (MediaBrowserActivity.TYPE_PLAYLIST.equals(type)) {
                        handleSelectedPlaylist(mediaUri);
                    }
                }
        );

        // Back button press
        ClickUtils.applyTouchEffect(binding.backAddPlaylist, v -> finish());

        // Open folder dir
        ClickUtils.applyTouchEffect(binding.buttonOpenFolder, v -> {
            Intent intent = new Intent(
                    getBaseContext(),
                    MediaBrowserActivity.class
            );

            mediaBrowserLauncher.launch(intent);
        });

        // Open single media file
        ClickUtils.applyTouchEffect(binding.buttonOpenFile, v ->
            openPlaylistPicker()
        );

        // Open playlist url
        ClickUtils.applyTouchEffect(binding.buttonOpenPlaylistUrl, v -> {
          startActivity(new Intent(getBaseContext(), AddPlaylistUrlActivity.class));
        });

        // open video url
        ClickUtils.applyTouchEffect(binding.buttonOpenVideoUrl, v -> {
          startActivity(new Intent(getBaseContext(), PlayVideoUrlActivity.class));

        });

        // Open audio url
        ClickUtils.applyTouchEffect(binding.buttonOpenAudioStream, v -> {
          startActivity(new Intent(getBaseContext(), PlayAudioStreamActivity.class));
        });

        // Back  nav button press
        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                       finish();
                    }
                });

    }


    // File picker launcher
    private final ActivityResultLauncher<String[]> pickPlaylistLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) {
                            handleSelectedPlaylist(uri);
                        }
                    }
            );

    private void openPlaylistPicker() {
        pickPlaylistLauncher.launch(new String[]{
                "audio/x-mpegurl",
                "application/vnd.apple.mpegurl",
                "text/plain",
                "video/mp2t",
                "application/octet-stream"
        });
    }

    // handle selected playlist
    private void handleSelectedPlaylist(Uri uri) {
        String fileName = Playlist.getFileName(this, uri);

        if (fileName == null ||
                !(fileName.toLowerCase(Locale.ROOT).endsWith(".m3u")
                        || fileName.toLowerCase(Locale.ROOT).endsWith(".m3u8"))) {

            AppToast.warning(this, "Please select an M3U or M3U8 playlist");
            return;
        }

        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException | UnsupportedOperationException ignored) {
            // The URI may still be readable for this session.
        }

        Intent intent = new Intent(this, FileDetailsActivity.class);
        intent.putExtra("playlist_uri", uri.toString());
        intent.putExtra("playlist_file_name", fileName);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}