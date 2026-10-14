package com.aaronmaxlab.maxplayer;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.aaronmaxlab.maxplayer.databinding.ActivityAddPlaylistUrlBinding;
import com.aaronmaxlab.maxplayer.player.VideoPlayerActivity;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistInfo;
import com.aaronmaxlab.maxplayer.playlist.PlaylistParser;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.Playlist;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.aaronmaxlab.maxplayer.utils.UrlUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddPlaylistUrlActivity extends AppCompatActivity {

    private ActivityAddPlaylistUrlBinding binding;
    private String playlistUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

         binding = ActivityAddPlaylistUrlBinding.inflate(getLayoutInflater());

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

            EditText inputUrl = binding.inputPlaylistUrl;
            EditText inputName = binding.inputPlaylistName;

            TextView errorCard = binding.errorCard;
            TextView statusLabel = binding.statesLabel;
            LinearLayout  loading = binding.loadingState;
            LinearLayout successCard = binding.successCard;

            TextView playlistCount = binding.playlistAddedCount;
            TextView playlistCatCount = binding.playlistAddedCatCount;

            LinearLayout addPlaylistBtn = binding.btnAddPlaylistCall;

             playlistUrl = String.valueOf(inputUrl.getText()).trim();
             String name = String.valueOf(inputName.getText()).trim();


            if(playlistUrl.isEmpty()){
                inputUrl.setError("Enter a video stream URL");
                inputUrl.requestFocus();
                return;
            }

            if (!UrlUtils.isValidStreamUrl(playlistUrl)) {
                inputUrl.setError("Enter a valid HTTP or HTTPS URL");
                inputUrl.requestFocus();
                return;
            }

            loading.setVisibility(VISIBLE);
            statusLabel.setVisibility(INVISIBLE);
            errorCard.setVisibility(INVISIBLE);
            successCard.setVisibility(GONE);

            addPlaylistBtn.setEnabled(false);

             final ExecutorService executor =
                    Executors.newSingleThreadExecutor();

             executor.execute(() -> {
                 try {

                     String fileName = UIUtils.extractLabel(playlistUrl);

                     PlaylistInfo info = PlaylistParser.parseRemoteUrl(
                             playlistUrl,
                             fileName
                     );

                     // Populate name and size into 'info'
                     Playlist.loadRemoteMetadata( playlistUrl, info);

                      String channelCount = String.valueOf(info.channelCount);
                      String categoryCount = String.valueOf(info.categoryNames.size());

                     runOnUiThread(() -> {
                         loading.setVisibility(GONE);

                         if(info.isPlaylist && info.channelCount >= 1){
                             statusLabel.setVisibility(VISIBLE);
                             successCard.setVisibility(VISIBLE);
                             playlistCount.setText(channelCount);
                             playlistCatCount.setText(categoryCount);

                             AppToast.success(this, "Playlist added.");

                         }else{
                             AppToast.info(this, "It's not a playlist.");
                         }
                         addPlaylistBtn.setEnabled(true);

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

                         addPlaylistBtn.setEnabled(true);
                        }

                     );
                 }
             });
        });

        ClickUtils.applyTouchEffect(binding.openPlaylistStream, v -> {
            if (playlistUrl == null) {
                AppToast.warning(this, "Please load a playlist first.");
                return;
            }

            Intent intent = new Intent(this, VideoPlayerActivity.class);
            intent.putExtra("playlist_url", playlistUrl);
            startActivity(intent);
        });

    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}