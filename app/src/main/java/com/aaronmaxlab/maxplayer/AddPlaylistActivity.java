package com.aaronmaxlab.maxplayer;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.aaronmaxlab.maxplayer.databinding.ActivityAddPlaylistBinding;
import com.aaronmaxlab.maxplayer.filepicker.FileDetailsActivity;
import com.aaronmaxlab.maxplayer.filepicker.MediaBrowserActivity;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.Playlist;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import java.util.Locale;

public class AddPlaylistActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> mediaBrowserLauncher;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        ActivityAddPlaylistBinding binding = ActivityAddPlaylistBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

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

                    if (MediaBrowserActivity.TYPE_VIDEO.equals(type)) {
                        // Later: pass mediaUri to your MPV player.
                        Toast.makeText(
                                this,
                                "Video selected: " + name,
                                Toast.LENGTH_SHORT
                        ).show();

                        // Example for future MPV integration:
                        // mpvPlayer.open(mediaUri);

                    } else if (MediaBrowserActivity.TYPE_PLAYLIST.equals(type)) {
                        handleSelectedPlaylist(mediaUri);

                    }
                }
        );

        // back press
        ClickUtils.applyTouchEffect(binding.backAddPlaylist, v -> finish());

        // open folder
        ClickUtils.applyTouchEffect(binding.buttonOpenFolder, v -> {
            Intent intent = new Intent(
                    getBaseContext(),
                    MediaBrowserActivity.class
            );

            mediaBrowserLauncher.launch(intent);
        });

        // open single file
        ClickUtils.applyTouchEffect(binding.buttonOpenFile, v ->
            openPlaylistPicker()
        );

        // open playlist url
        ClickUtils.applyTouchEffect(binding.buttonOpenPlaylistUrl, v -> {
          startActivity(new Intent(getBaseContext(), AddPlaylistUrlActivity.class));
        });

        // open video url
        ClickUtils.applyTouchEffect(binding.buttonOpenVideoUrl, v -> {
            startActivity(new Intent(getBaseContext(), PlayVideoUrlActivity.class));
        });

        // open audio url
        ClickUtils.applyTouchEffect(binding.buttonOpenAudioStream, v -> {
          startActivity(new Intent(getBaseContext(), PlayAudioStreamActivity.class));
        });

    }


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


    private void handleSelectedPlaylist(Uri uri) {
        String fileName = Playlist.getFileName(this, uri);

        if (fileName == null ||
                !(fileName.toLowerCase(Locale.ROOT).endsWith(".m3u")
                        || fileName.toLowerCase(Locale.ROOT).endsWith(".m3u8"))) {

            Toast.makeText(
                    this,
                    "Please select an M3U or M3U8 playlist",
                    Toast.LENGTH_LONG
            ).show();

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


}