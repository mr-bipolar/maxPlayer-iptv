package com.aaronmaxlab.maxplayer;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.aaronmaxlab.maxplayer.databinding.ActivityAddPlaylistUrlBinding;
import com.aaronmaxlab.maxplayer.filepicker.models.PlaylistInfo;
import com.aaronmaxlab.maxplayer.filepicker.parser.PlaylistParser;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.Playlist;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddPlaylistUrlActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        ActivityAddPlaylistUrlBinding binding = ActivityAddPlaylistUrlBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        // bottom nav set black
        UIUtils.setLightNavigationBars(this, false);


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.errorCard.setVisibility(INVISIBLE);

        ClickUtils.applyTouchEffect(binding.backAddPlayListUrlOpen, v -> finish());

        ClickUtils.applyTouchEffect(binding.btnAddPlaylistCall, v -> {

           final String url = String.valueOf(binding.inputPlaylistUrl.getText());
           final String name = String.valueOf(binding.inputPlaylistName.getText());
            TextView errorCard = findViewById(R.id.errorCard);
            TextView statusLabel = findViewById(R.id.statesLabel);
            LinearLayout  loading = findViewById(R.id.loadingState);
            LinearLayout successCard = findViewById(R.id.successCard);

            TextView playlistCount = findViewById(R.id.playlistAddedCount);
            TextView playlistCatCount = findViewById(R.id.playlistAddedCatCount);

            loading.setVisibility(VISIBLE);
            statusLabel.setVisibility(INVISIBLE);
            errorCard.setVisibility(INVISIBLE);
            successCard.setVisibility(GONE);

            if (url.trim().isEmpty()) {
                Toast.makeText(this, "Enter a playlist URL.", Toast.LENGTH_SHORT).show();
                return;
            }

             final ExecutorService executor =
                    Executors.newSingleThreadExecutor();

             executor.execute(() -> {
                 try {
                     String fileName = Uri.parse(url).getLastPathSegment();

                     if (fileName == null || fileName.trim().isEmpty()) {
                         fileName = "playlist.m3u";
                     }

                     PlaylistInfo info = PlaylistParser.parseRemoteUrl(
                             url,
                             fileName
                     );

                     // Populate name and size into 'info'
                     Playlist.loadRemoteMetadata( url, info);

                      String channelCount = String.valueOf(info.channelCount);
                      String categoryCount = String.valueOf(info.categoryNames.size());

                     runOnUiThread(() -> {
                         loading.setVisibility(GONE);

                         if(info.isPlaylist && info.channelCount >= 1){
                             statusLabel.setVisibility(VISIBLE);
                             successCard.setVisibility(VISIBLE);
                             playlistCount.setText(channelCount);
                             playlistCatCount.setText(categoryCount);

                             Toast.makeText(this, "Playlist added.", Toast.LENGTH_SHORT).show();

                         }else{
                             Toast.makeText(this, "It's not a playlist.", Toast.LENGTH_SHORT).show();
                         }

                     });
                 } catch (Exception e) {
                     runOnUiThread(() -> {
                        final String errorMessage = e.getMessage();
                        final String message =  (errorMessage != null ? errorMessage + "\n" : "")
                                 + getString(R.string.error_parse_playlist);

                         errorCard.setText(message);
                         errorCard.setVisibility(VISIBLE);
                         loading.setVisibility(GONE);
                         statusLabel.setVisibility(VISIBLE);



                             }

                     );
                 }
             });
        });


    }
}