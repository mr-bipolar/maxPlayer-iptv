package com.aaronmaxlab.maxplayer.filepicker;

import static android.view.View.INVISIBLE;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.ActivityFileDetailsBinding;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistInfo;
import com.aaronmaxlab.maxplayer.playlist.PlaylistParser;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.Playlist;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.google.android.flexbox.FlexboxLayout;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileDetailsActivity extends AppCompatActivity {

   private ActivityFileDetailsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityFileDetailsBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());


        // bottom nav set black
        UIUtils.setLightNavigationBars(this, false);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

       final String playlistUri = getIntent().getStringExtra("playlist_uri");
       final String fileName = getIntent().getStringExtra("playlist_file_name");


        ClickUtils.applyTouchEffect(binding.backPlaylistFound, view -> finish());

        if (fileName != null) {
            binding.txtPlaylistFileName.setText(fileName);
            binding.editPlaylistName.setText(
                    fileName.replaceFirst("(?i)\\.m3u8?$", "")
            );
        }

        if (playlistUri != null) {
          //  binding.txtPlaylistFileMeta.setText("Playlist file selected");
        }

        if (playlistUri != null && fileName != null) {
            inspectPlaylist(Uri.parse(playlistUri), fileName);
        }


    }

    private void showCategories(List<String> categories) {
        FlexboxLayout container = findViewById(R.id.categoryContainer);
        container.removeAllViews();

        for (String category : categories) {
            if (category == null || category.trim().isEmpty()) {
                continue;
            }

            TextView chip = new TextView(this);
            chip.setText(category.trim());
            chip.setTextSize(13);
            chip.setTextColor(Color.parseColor("#6B7280"));
            chip.setTypeface(
                    android.graphics.Typeface.create(
                            "sans-serif-medium",
                            android.graphics.Typeface.NORMAL
                    )
            );

            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setBackgroundResource(R.drawable.bg_chip);

            FlexboxLayout.LayoutParams params =
                    new FlexboxLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            params.setMargins(0, 0, dp(8), dp(8));

            container.addView(chip, params);
        }
    }

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }



    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private void inspectPlaylist(Uri uri, String fileName) {
        executor.execute(() -> {
            try {

                PlaylistInfo info = PlaylistParser.parse(
                        getContentResolver(),
                        uri,
                        fileName
                );

                // Populate name and size into 'info'
                Playlist.loadFileMetadata(this, uri, info);

                runOnUiThread(() -> {
                    // Replace these IDs with the ones in your XML.
                    TextView nameView =
                            findViewById(R.id.txtPlaylistFileName);
                    TextView metaView =
                            findViewById(R.id.txtPlaylistFileMeta);

                    TextView channelCount = findViewById(R.id.channelCount);
                    TextView categorySCount = findViewById(R.id.categoryCount);
                    TextView fileFormat = findViewById(R.id.fileFormat);
                    LinearLayout categoryLabel = findViewById(R.id.categoryLabel);

                    if(info.categoryNames.isEmpty()){
                        categoryLabel.setVisibility(INVISIBLE);
                    }

                    String size = Playlist.formatFileSize(info.fileSize);
                    String channelSize = String.valueOf(info.channelCount);
                    String categorySize = String.valueOf(info.categoryNames.size());

                    nameView.setText(info.fileName);
                    metaView.setText(info.format + " • " + size);

                    channelCount.setText(channelSize);
                    categorySCount.setText(categorySize);
                    fileFormat.setText(info.format);
                    showCategories(info.categoryNames);

                });

            } catch (Exception e) {
                runOnUiThread(() -> AppToast.error(this, "Unable to read playlist: " + e.getMessage())
                );
            }
        });
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}