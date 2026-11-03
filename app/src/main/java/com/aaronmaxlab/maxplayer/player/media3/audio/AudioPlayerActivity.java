package com.aaronmaxlab.maxplayer.player.media3.audio;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import androidx.appcompat.view.ContextThemeWrapper;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.aaronmaxlab.maxplayer.PlayAudioStreamActivity;
import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.models.LocalDbHistory;
import com.aaronmaxlab.maxplayer.databinding.ActivityAudioPlayerBinding;
import com.aaronmaxlab.maxplayer.playlist.SaveChannelDialog;
import com.aaronmaxlab.maxplayer.service.PlaybackService;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.AudioUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@OptIn(markerClass = UnstableApi.class)
public class AudioPlayerActivity extends AppCompatActivity {

    private static final long ARTWORK_ROTATION_MS = 8000L;

    private ActivityAudioPlayerBinding binding;
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController player;
    private ObjectAnimator artworkRotation;
    @Nullable private Dialog infoDialog;
    @Nullable private Dialog saveDialog;

    private String streamUri;
    private String streamChannelName;

    // stream format, shown in the label and in Stream info
    private String codec;
    private int bitrateKbps = -1;

    // UI state, used to skip redundant view updates
    private boolean uiBound;
    private boolean uiPlaying;
    private boolean visible;
    private boolean favorite;

    // ---- static categories

    private static final List<String> AUDIO_CATEGORIES =
            new ArrayList<>(Arrays.asList(
                    "Podcast",  "Radio", "Mp3"
            ));

    private SqlDbHelper sqlDbHelper;

    // Lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        streamUri = getIntent().getStringExtra(PlayAudioStreamActivity.EXTRA_STREAM_URI);
        if (TextUtils.isEmpty(streamUri)) {
            AppToast.warning(this, "Stream URL not found");
            finish();
            return;
        }

        String name = getIntent().getStringExtra(PlayAudioStreamActivity.EXTRA_STREAM_NAME);
        streamChannelName = TextUtils.isEmpty(name) ? UIUtils.extractLabel(streamUri) : name;

        binding = ActivityAudioPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false);
        UIUtils.applySystemBarPadding(binding.getRoot());

        sqlDbHelper = new SqlDbHelper(this);

        binding.audioStationName.setText(streamChannelName);
        favorite = AudioFavorites.isFavorite(this,  streamUri);
        renderFavorite();

        setupArtworkRotation();
        setupControls();
        connectToPlaybackService();
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        if (uiPlaying) startArtworkRotation();
    }

    @Override
    protected void onStop() {
        visible = false;
        if (artworkRotation != null) artworkRotation.pause(); // no frames while hidden
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (infoDialog != null) {
            infoDialog.dismiss();
            infoDialog = null;
        }
        if (artworkRotation != null) {
            artworkRotation.cancel();
            artworkRotation = null;
        }
        if (player != null) {
            player.removeListener(playerListener);
            player = null;
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }

        setHistory(sqlDbHelper);

        binding = null;
        super.onDestroy();
    }

    private void setHistory(SqlDbHelper sqlDbHelper){
        try {
           final LocalDbHistory history = new LocalDbHistory(
                   streamChannelName,
                   "Audio stream",
                   streamUri,
                   null,
                   PlaylistType.AUDIO,
                   null,
                   null);

            sqlDbHelper.saveHistory(history);

        } catch (Exception e) {
            Log.e("HISTORY", Objects.requireNonNull(e.getMessage()));
        }finally {
            sqlDbHelper.close();
        }
    }


    // Controls and header menu
    private void setupControls() {
        ClickUtils.applyTouchEffect(binding.backAudioStreamBtn, v -> finish());
        ClickUtils.applyTouchEffect(binding.audioMoreBtn, this::showMenu);
        ClickUtils.applyTouchEffect(binding.audioCatSaveBtn, v -> showAddChannelDialog());

        ClickUtils.applyTouchEffect(binding.audioFavoriteBtn, v -> {
            favorite = AudioFavorites.toggle(this, streamChannelName, streamUri);
            renderFavorite();
        });

        // The icon is updated by playerListener.onIsPlayingChanged.
        ClickUtils.applyTouchEffect(binding.playPauseContainer, v -> {
            if (player == null) return;

            if (player.isPlaying()) {
                player.pause();
                return;
            }

            AudioUtils.setAudioStopped(this, false);
            if (player.getPlaybackState() == Player.STATE_IDLE) {
                player.prepare();
            }
            player.play();
        });

        ClickUtils.applyTouchEffect(binding.audioStopBtn, v -> stopPlayback());
    }

    private void showAddChannelDialog(){
        if (saveDialog != null) saveDialog.dismiss();
        saveDialog = SaveChannelDialog.show(
                this,
                streamUri,
                AUDIO_CATEGORIES, //  categories
                null,
                category -> {
                    Toast.makeText(this, category, Toast.LENGTH_SHORT).show();
                    // save streamUri + streamChannelName + category here
                });
    }

    private void showMenu(@NonNull View anchor) {
        PopupMenu menu = new PopupMenu(
                new ContextThemeWrapper(this, R.style.PlayerPopupMenuTheme), anchor);
        menu.inflate(R.menu.menu_audio_player);
        menu.setOnMenuItemClickListener(this::onMenuItemSelected);
        menu.show();
    }

    private boolean onMenuItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_share) {
            shareStream();
        } else if (id == R.id.action_copy_url) {
            copyStreamUrl();
        } else if (id == R.id.action_stream_info) {
            showStreamInfo();
        } else if (id == R.id.action_stop_close) {
            stopPlayback();
            finish();
        } else {
            return false;
        }
        return true;
    }

    private void shareStream() {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, streamChannelName)
                .putExtra(Intent.EXTRA_TEXT, streamUri);
        startActivity(Intent.createChooser(send, getString(R.string.menu_share_stream)));
    }

    private void copyStreamUrl() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;

        clipboard.setPrimaryClip(ClipData.newPlainText(streamChannelName, streamUri));
        // Android 13+ shows its own confirmation
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            AppToast.info(this, getString(R.string.toast_link_copied));
        }
    }

    private void showStreamInfo() {
        String format = formatLabel();
        String message = format == null ? streamUri : streamUri + "\n\n" + format;

        if (infoDialog != null) infoDialog.dismiss();
        infoDialog = new AlertDialog.Builder(this, R.style.PlayerDialogTheme)
                .setTitle(streamChannelName)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void stopPlayback() {
        if (player != null) {
            player.pause();
            player.seekTo(0);
        }
        AudioUtils.setAudioStopped(this, true);

        if (artworkRotation != null) {
            artworkRotation.cancel();
            binding.audioArtwork.setRotation(0f);
        }
    }

    private void renderFavorite() {
        binding.audioFavoriteBtn.setImageResource(
                favorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        ImageViewCompat.setImageTintList(binding.audioFavoriteBtn,
                ColorStateList.valueOf(ContextCompat.getColor(
                        this, favorite ? R.color.accent : R.color.accent_dim)));
    }


    // Artwork rotation
    private void setupArtworkRotation() {
        artworkRotation = ObjectAnimator.ofFloat(binding.audioArtwork, View.ROTATION, 0f, 360f);
        artworkRotation.setDuration(ARTWORK_ROTATION_MS);
        artworkRotation.setInterpolator(new LinearInterpolator());
        artworkRotation.setRepeatCount(ValueAnimator.INFINITE);
    }

    private void startArtworkRotation() {
        if (!visible || artworkRotation == null) return;

        if (artworkRotation.isPaused()) {
            artworkRotation.resume();
        } else if (!artworkRotation.isStarted()) {
            artworkRotation.start();
        }
    }

    private void pauseArtworkRotation() {
        if (artworkRotation != null) artworkRotation.pause(); // keeps the angle, no snap
    }

    // Player service
    private void connectToPlaybackService() {
        SessionToken token = new SessionToken(
                this, new ComponentName(this, PlaybackService.class));

        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(this::onControllerReady,
                ContextCompat.getMainExecutor(this));
    }

    private void onControllerReady() {
        if (isFinishing() || isDestroyed() || controllerFuture == null) return;

        try {
            player = controllerFuture.get();
        } catch (Exception e) {
            AppToast.error(this, "Unable to connect to audio player");
            finish();
            return;
        }

        initializePlayer();
    }

    private void initializePlayer() {
        player.addListener(playerListener);

        /** The service player outlives the activity. Only (re)start when this
         * stream isn't already playing cleanly (e.g. rotation keeps playing).
         */
        if (isStreamPlaying()) {
            // No events fire until the next change, so read the current state now.
            updateTitle(player.getMediaMetadata());
            updateStreamFormat(player.getCurrentTracks());
        } else {
            AudioUtils.setAudioStopped(this, false);

            player.setMediaItem(new MediaItem.Builder()
                    .setMediaId(streamUri) // survives IPC, used for the check above
                    .setUri(streamUri)
                    .setMediaMetadata(new MediaMetadata.Builder()
                            /** Don't set title: MediaItem metadata overrides the
                             * stream's live (ICY) title. Use artist/station instead.
                             */
                            .setArtist(streamChannelName)
                            .setStation(streamChannelName)
                            .build())
                    .build());
            player.prepare();
            player.play();
        }

        applyPlayingState(player.isPlaying());
    }

    private boolean isStreamPlaying() {
        MediaItem current = player.getCurrentMediaItem();
        return current != null
                && streamUri.equals(current.mediaId)
                && player.getPlayerError() == null
                && player.isPlaying();
    }


    // UI state
    private void applyPlayingState(boolean isPlaying) {
        if (binding == null || (uiBound && uiPlaying == isPlaying)) return;
        uiBound = true;
        uiPlaying = isPlaying;

        if (isPlaying) {
            startArtworkRotation();
        } else {
            pauseArtworkRotation();
        }

        binding.playPauseBtn.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        binding.playPauseContainer.setContentDescription(
                getString(isPlaying ? R.string.cd_pause : R.string.cd_play));
        binding.statusIndicator.setBackgroundResource(
                isPlaying ? R.drawable.bg_circle_ok : R.drawable.bg_circle_live);
        binding.fmStreamLabel.setText(
                isPlaying ? R.string.audio_status_playing : R.string.audio_status_stop);
        binding.fmStreamLabel.setTextColor(
                ContextCompat.getColor(this, isPlaying ? R.color.ok : R.color.live));
    }

    private void updateTitle(@NonNull MediaMetadata metadata) {
        if (binding == null) return;

        CharSequence title = firstNonBlank(metadata.title, metadata.displayTitle);
        binding.audioStationName.setText(title != null ? title : streamChannelName);
    }

    /** First non-blank value, or null. Also used by {@link AudioMiniPlayer}. */
    @Nullable
    static CharSequence firstNonBlank(CharSequence... values) {
        for (CharSequence value : values) {
            if (value != null && !value.toString().trim().isEmpty()) return value;
        }
        return null;
    }


    // Stream format (codec and bitrate)
    private void updateStreamFormat(@NonNull Tracks tracks) {
        codec = null;
        bitrateKbps = -1;

        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;

            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSelected(i)) continue;

                Format format = group.getTrackFormat(i);
                codec = codecLabel(format.sampleMimeType);
                bitrateKbps = format.bitrate > 0 ? format.bitrate / 1000 : -1;
                renderFormatLabel();
                return;
            }
        }
        renderFormatLabel();
    }

    private void renderFormatLabel() {
        if (binding == null) return;

        String label = formatLabel();
        if (label == null) {
            binding.bitrateLabel.setVisibility(View.GONE);
        } else {
            binding.bitrateLabel.setText(label);
            binding.bitrateLabel.setVisibility(View.VISIBLE);
        }
    }

    @Nullable
    private String formatLabel() {
        if (codec != null && bitrateKbps > 0) return codec + " \u2022 " + bitrateKbps + " kbps";
        if (bitrateKbps > 0) return bitrateKbps + " kbps";
        return codec;
    }

    @Nullable
    private static String codecLabel(@Nullable String mime) {
        if (mime == null) return null;

        switch (mime) {
            case MimeTypes.AUDIO_MPEG:   return "MP3";
            case MimeTypes.AUDIO_AAC:    return "AAC";
            case MimeTypes.AUDIO_OPUS:   return "Opus";
            case MimeTypes.AUDIO_VORBIS: return "Vorbis";
            case MimeTypes.AUDIO_FLAC:   return "FLAC";
            default:
                int slash = mime.indexOf('/');
                return (slash >= 0 ? mime.substring(slash + 1) : mime).toUpperCase(Locale.ROOT);
        }
    }


    // Player listener
    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            applyPlayingState(isPlaying);
        }

        @Override
        public void onMediaMetadataChanged(@NonNull MediaMetadata metadata) {
            updateTitle(metadata);
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            updateStreamFormat(tracks);
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            AppToast.error(AudioPlayerActivity.this,
                    error.getMessage() != null ? error.getMessage() : "Playback failed");
        }
    };
}