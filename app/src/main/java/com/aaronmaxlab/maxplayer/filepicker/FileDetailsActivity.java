package com.aaronmaxlab.maxplayer.filepicker;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.ActivityFileDetailsBinding;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.playlist.PlaylistParser;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistInfo;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.Playlist;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.google.android.flexbox.FlexboxLayout;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class FileDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_PLAYLIST_URI = "playlist_uri";
    public static final String EXTRA_PLAYLIST_FILE_NAME = "playlist_file_name";

    private static final Pattern PLAYLIST_EXT = Pattern.compile("\\.m3u8?$", Pattern.CASE_INSENSITIVE);
    private static final int CHIP_TEXT_COLOR = Color.parseColor("#6B7280");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityFileDetailsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityFileDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false); // black bottom nav
        UIUtils.applySystemBarPadding(binding.getRoot());

        String uriString = getIntent().getStringExtra(EXTRA_PLAYLIST_URI);
        String fileName = getIntent().getStringExtra(EXTRA_PLAYLIST_FILE_NAME);
        Uri uri = uriString != null ? Uri.parse(uriString) : null;

        ClickUtils.applyTouchEffect(binding.backPlaylistFound, v -> finish());
        ClickUtils.applyTouchEffect(binding.savePlaylist, v -> openPlayer(uri, fileName));

        if (fileName != null) {
            binding.txtPlaylistFileName.setText(fileName);
            binding.editPlaylistName.setText(PLAYLIST_EXT.matcher(fileName).replaceFirst(""));
        }

        if (uri != null && fileName != null) {
            inspectPlaylist(uri, fileName);
        }
    }

    private void openPlayer(@Nullable Uri uri, @Nullable String fileName) {
        if (uri == null) return;

        Intent intent = PlayerActivity.forLocalFile(this, uri, fileName);
        if (intent != null) startActivity(intent);
        else AppToast.warning(this, "Unsupported file");
    }

    private void inspectPlaylist(@NonNull Uri uri, @NonNull String fileName) {
        executor.execute(() -> {
            try {
                PlaylistInfo info = PlaylistParser.parse(getContentResolver(), uri, fileName);
                Playlist.loadFileMetadata(this, uri, info); // fills name and size

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    showPlaylistInfo(info);
                });
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    AppToast.error(this, "Unable to read playlist: " + message);
                });
            }
        });
    }

    private void showPlaylistInfo(@NonNull PlaylistInfo info) {
        List<String> categories = info.categoryNames;

        binding.txtPlaylistFileName.setText(info.fileName);
        binding.txtPlaylistFileMeta.setText(
                info.format + " • " + Playlist.formatFileSize(info.fileSize));

        binding.channelCount.setText(String.valueOf(info.channelCount));
        binding.categoryCount.setText(String.valueOf(categories.size()));
        binding.fileFormat.setText(info.format);

        binding.categoryLabel.setVisibility(categories.isEmpty() ? View.GONE : View.VISIBLE);
        showCategories(categories);
    }

    private void showCategories(@NonNull List<String> categories) {
        FlexboxLayout container = binding.categoryContainer;
        container.removeAllViews();

        // computed once, not per chip
        final int padH = dp(14);
        final int padV = dp(8);
        final int marginEnd = dp(8);
        final Typeface typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);

        for (String category : categories) {
            if (category == null) continue;
            String label = category.trim();
            if (label.isEmpty()) continue;

            TextView chip = new TextView(this);
            chip.setText(label);
            chip.setTextSize(13);
            chip.setTextColor(CHIP_TEXT_COLOR);
            chip.setTypeface(typeface);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(padH, padV, padH, padV);
            chip.setBackgroundResource(R.drawable.bg_chip);

            FlexboxLayout.LayoutParams lp = new FlexboxLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, marginEnd, marginEnd);

            container.addView(chip, lp);
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        binding = null;
        super.onDestroy();
    }
}