package com.aaronmaxlab.maxplayer.player;

import android.app.Dialog;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Rational;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.media3.ui.TrackSelectionDialogBuilder;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.MaxPlayerApplication;
import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.animations.LoaderAnimator;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.models.LocalDbChannel;
import com.aaronmaxlab.maxplayer.database.models.LocalDbFavorites;
import com.aaronmaxlab.maxplayer.database.models.LocalDbHistory;
import com.aaronmaxlab.maxplayer.databinding.ActivityPlayerBinding;
import com.aaronmaxlab.maxplayer.player.mpv.MpvPlayerAdapter;
import com.aaronmaxlab.maxplayer.playlist.PlaylistParser;
import com.aaronmaxlab.maxplayer.playlist.SaveChannelDialog;
import com.aaronmaxlab.maxplayer.playlist.adapter.PlaylistAdapter;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistChannel;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistInfo;
import com.aaronmaxlab.maxplayer.remote.RemoteKeyController;
import com.aaronmaxlab.maxplayer.service.VideoPlaybackService;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.AppUtils;
import com.aaronmaxlab.maxplayer.utils.AspectRatioUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.DeviceUtils;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * THE video player: one Activity, one layout ({@code activity_player.xml}), one controller design
 * ({@link PlayerControlsUi}), two engines:
 * <ul>
 *   <li>{@link PlayerEngineType#MEDIA3} - MediaController connected to {@link VideoPlaybackService}</li>
 *   <li>{@link PlayerEngineType#MPV} - {@link MpvPlayerAdapter} (mpv exposed as a media3 {@link Player})</li>
 * </ul>
 * Both are plain {@link Player}s, so every control is written once.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    // ---- intent extras
    public static final String EXTRA_PLAYLIST_URL = "playlist_url";        // M3U to download and parse
    public static final String EXTRA_STREAM_URL = "playlist_uri";          // one direct stream
    public static final String EXTRA_TITLE = "playlist_file_name";         // title for a single stream
    public static final String EXTRA_LIVE = "extra_live";                  // boolean hint (optional)
    public static final String EXTRA_START_INDEX = "extra_start_index";    // first channel of a playlist
    public static final String EXTRA_POSITION = "extra_position";          // video resume position
    public static final String EXTRA_ENGINE = "extra_engine";              // "media3" | "mpv" (overrides the setting)
    private static final long SEEK_STEP_MS = 10_000;

    private static final String STATE_CHANNEL_POSITION = "channel_position";
    private static final String CATEGORY_ALL = "All";
    private static final double[] SPEEDS = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0};
    private static final int[] SUB_SIZE_LABELS = {R.string.mp_sub_small, R.string.mp_sub_medium,
            R.string.mp_sub_large, R.string.mp_sub_xl};
    private static final float[] MEDIA3_SUB_SCALE = {0.75f, 1f, 1.3f, 1.7f};
    private static final int[] ASPECT_LABELS = {R.string.mp_aspect_fit, R.string.mp_aspect_16_9,
            R.string.mp_aspect_4_3, R.string.mp_aspect_fill, R.string.mp_aspect_stretch};
    private static final String PREFS = "player_prefs";
    private static final String KEY_FAVORITES = "favorites";

    // ---- picture-in-picture remote actions
    private static final String ACTION_PIP = "com.aaronmaxlab.maxplayer.PIP_CONTROL";
    private static final String EXTRA_PIP_CMD = "pip_cmd";
    private static final int PIP_PLAY_PAUSE = 1, PIP_PREV = 2, PIP_NEXT = 3;

    public static Intent forPlaylist(Context c, String m3uUrl) {
        return new Intent(c, PlayerActivity.class).putExtra(EXTRA_PLAYLIST_URL, m3uUrl);
    }

    public static Intent forStream(Context c, String url, @Nullable String title, @Nullable Boolean live,  @Nullable Long position) {
        Intent i = new Intent(c, PlayerActivity.class).putExtra(EXTRA_STREAM_URL, url).putExtra(EXTRA_TITLE, title);
        if (live != null) i.putExtra(EXTRA_LIVE, live.booleanValue());
        if (position != null) {
            i.putExtra(EXTRA_POSITION, position);
        }
        return i;
    }

    public static Intent forStream(Context context, String url,
            @Nullable String title,
            @Nullable Boolean live) {
        return forStream(context, url, title, live, null);
    }

    /** Playlist (URL or file) that starts at channel startIndex; the full list stays available. */
    public static Intent forPlaylist(Context c, String source, int startIndex) {
        return forPlaylist(c, source).putExtra(EXTRA_START_INDEX, startIndex);
    }

    /** A playlist FILE on the device, e.g. content://com.android.externalstorage.documents/document/primary%3ADownload%2FTelegram%2Fadult.m3u */
    public static Intent forPlaylistFile(Context c, Uri file, int startIndex) {
        return forPlaylist(c, file.toString(), startIndex)
                .setData(file)                                     // carries the read permission to this Activity
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }


    @Nullable
    public static Intent forLocalFile(Context c, Uri file, @Nullable String fileName) {
        PlaylistParser.LocalKind kind = PlaylistParser.detectLocal(c.getContentResolver(), file, fileName);
        switch (kind) {
            case PLAYLIST:
                return forPlaylistFile(c, file, 0);
            case MEDIA_FILE:
                return forStream(c, file.toString(), fileName, null)
                        .setData(file)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            default:   // HLS_STREAM / UNSUPPORTED
                return null;
        }
    }

    /** The one live player screen. A newer instance takes over from an older one (see releaseForTakeover). */
    @Nullable private static PlayerActivity active;

    // ---- views / engine
    private ActivityPlayerBinding binding;
    private PlayerView playerView;
    private PlayerControlsUi ui;
    private boolean isTv;
    @Nullable private View tvPlaylistPanel;
    @Nullable private RecyclerView tvPlaylistRecycler;
    @Nullable private PlaylistAdapter tvPlaylistAdapter;
    private boolean tvPlaylistInitialFocusRequested;
    private int tvPlaylistLastFocusedPosition = RecyclerView.NO_POSITION;

    private PlayerEngineType engineType;
    @Nullable private Player player;                    // media3 controller OR mpv adapter
    @Nullable private MpvPlayerAdapter mpv;             // non-null only when running mpv
    @Nullable private ListenableFuture<MediaController> controllerFuture;
    @Nullable private Player.Listener playerListener;
    private boolean switchingEngine;

    // ---- source
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private Boolean liveHint;
    @Nullable private PlaylistInfo playlistModel;
    private int currentChannelPosition = 0;
    private String streamUrl;
    private String currentUrl;
    private String currentTitle;
    @Nullable private Long pendingHistoryPosition;
    private boolean resumePromptPending;

    // ---- ui state
    private int aspectMode = 0;
    private boolean wasInPipMode = false;
    private boolean pipReceiverRegistered = false;
    private RemoteKeyController remoteKeys;
    @Nullable private AlertDialog dialog;
    @Nullable private Dialog saveDialog;


    // ---- channels bottom sheet state
    @Nullable private PlaylistAdapter adapter;
    private boolean showSearchBox = false;
    private String selectedCategory = CATEGORY_ALL;
    private String searchQuery = "";

    // ---- static categories
    private static final List<String> IPTV_CATEGORIES = AppUtils.showCategories();

    // ---- local db
    private SqlDbHelper sqlDbHelper;


    private final ActivityResultLauncher<String[]> subtitlePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onSubtitlePicked);

    /** Buttons of the picture-in-picture window come back here. */
    private final BroadcastReceiver pipReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_PIP.equals(intent.getAction())) return;
            switch (intent.getIntExtra(EXTRA_PIP_CMD, 0)) {
                case PIP_PLAY_PAUSE: togglePlayPause(); break;
                case PIP_PREV: changeChannel(-1); break;
                case PIP_NEXT: changeChannel(+1); break;
                default: break;
            }
            updatePipParams();
        }
    };

    // =====================================================================================
    // Lifecycle
    // =====================================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        isTv = DeviceUtils.isTv(this);
        if (isTv) setupTvPlaylist();
        sqlDbHelper = new SqlDbHelper(this);
        remoteKeys = ((MaxPlayerApplication) getApplication()).getRemoteKeyController();
        // Never two players at once: mpv is a single native engine ("already initialized") and the Media3
        // service player is shared. Free the old screen's player/surface BEFORE this one creates its own.
        PlayerActivity previous = active;
        if (previous != null && previous != this) previous.releaseForTakeover();
        active = this;

        UIUtils.setLightNavigationBars(this, false);
        setupSystemUi();
        // The video fills the whole screen; only the controls are padded away from bars / cutouts.
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int insetTypes = WindowInsetsCompat.Type.displayCutout();
            if (!isTv) insetTypes |= WindowInsetsCompat.Type.systemBars();
            Insets i = insets.getInsets(insetTypes);
            binding.playerControllers.setPadding(i.left, i.top, i.right, i.bottom);
            return insets;
        });

        engineType = resolveEngine();

        playerView = binding.playerView;
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setKeepContentOnPlayerReset(false);

        ui = new PlayerControlsUi(this, binding);
        ui.setAspectLabel(ASPECT_LABELS[0]);
        ui.setFullscreenIcon(isLandscape());
        setupButtons();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBackPress(); }
        });

        if (savedInstanceState != null) {
            currentChannelPosition = savedInstanceState.getInt(STATE_CHANNEL_POSITION, 0);
        } else {
            currentChannelPosition = Math.max(0, getIntent().getIntExtra(EXTRA_START_INDEX, 0));
        }
        resolveSourceAndStart();

    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CHANNEL_POSITION, currentChannelPosition);
    }

    /**
     * The manifest uses launchMode="singleTask": opening a video while this screen is alive (full screen,
     * background, or in the PiP window) comes here instead of creating a second Activity.
     */
    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (dialog != null) { dialog.dismiss(); dialog = null; }

        playlistModel = null;
        liveHint = null;
        clearTvPlaylist();
        currentChannelPosition = Math.max(0, intent.getIntExtra(EXTRA_START_INDEX, 0));

        if (resolveEngine() != engineType) {          // the caller (or the setting) wants the other engine
            switchingEngine = true;
            recreate();                               // onCreate re-reads the new intent
            return;
        }
        resolveSourceAndStart();                      // same engine: just load the new stream into the live player
    }

    /** Loads the resolved stream: into the existing player if there is one, otherwise connects the engine first. */
    private void startOrConnect() {
        if (player != null) {
            if (resumePromptPending) {
                prepareHistoryResume();
            } else {
                playStream(streamUrl);
                updatePipParams();
            }
        } else if (controllerFuture == null) {        // not already connecting (then onPlayerReady uses the new streamUrl)
            connectPlayer();
        }
    }

    /** Called by a newer PlayerActivity: give up the engine/surface right now, then close. */
    private void releaseForTakeover() {
        unregisterPipReceiver();
        handler.removeCallbacksAndMessages(null);
        if (dialog != null) { dialog.setOnDismissListener(null); dialog.dismiss(); dialog = null; }
        if (ui != null) ui.detach();
        if (player != null && playerListener != null) player.removeListener(playerListener);
        playerListener = null;
        if (playerView != null) playerView.setPlayer(null);
        if (mpv != null) {
            mpv.release();                            // frees the single native mpv instance for the new screen
            mpv = null;
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);   // detach only: the new screen owns the playback
            controllerFuture = null;
        }
        player = null;
        active = null;
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mpv != null) mpv.onHostStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        wasInPipMode = false;                 // back to full screen (PiP expanded or a new intent arrived)
        UIUtils.setLightNavigationBars(this, false);
        if (isTv) setupSystemUi();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (isTv && ui != null) ui.onUserActivity();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !isInPip()) setupSystemUi();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (canEnterPip()) enterPipMode();
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveHistoryPosition(sqlDbHelper);
        // PiP window was closed by the user: stop playback and leave.
        if (wasInPipMode && !isChangingConfigurations()) {
            if (player != null) player.pause();
            if (mpv != null) mpv.onHostStop();
            finish();
            return;
        }
        // mpv owns a surface, so it pauses while the screen is hidden (Media3 keeps playing in its service).
        if (mpv != null) mpv.onHostStop();
    }



    /** Only called when the manifest declares android:configChanges (otherwise the Activity is recreated). */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ViewCompat.requestApplyInsets(binding.getRoot());
        ui.setFullscreenIcon(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
        if (mpv != null) mpv.onConfigurationChanged();
    }


    @Override
    protected void onDestroy() {
        AppToast.info(this, "destroy called");
        final boolean owner = active == this;
        if (owner) active = null;
        unregisterPipReceiver();
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        if (dialog != null) { dialog.setOnDismissListener(null); dialog.dismiss(); dialog = null; }

        // Save playback position and duration BEFORE stopping or releasing the player.

        if (ui != null) ui.release();
        if (player != null && playerListener != null) player.removeListener(playerListener);

        if (mpv != null) {
            playerView.setPlayer(null);
            mpv.release();
            mpv = null;
        } else {
            // The Media3 service player outlives this Activity: wipe it when the user really leaves
            // (not on rotation), and when switching to the other engine so it does not keep playing.
            boolean leaving = owner && ((isFinishing() && !isChangingConfigurations()) || switchingEngine);
            if (player != null && leaving) {
                player.stop();
                player.clearMediaItems();
            }
            playerView.setPlayer(null);
            if (controllerFuture != null) {
                MediaController.releaseFuture(controllerFuture);
                controllerFuture = null;
            }
        }

        player = null;
        adapter = null;
        binding = null;
        // in onDestroy, next to infoDialog
        if (saveDialog != null) { saveDialog.dismiss(); saveDialog = null; }
        UIUtils.setLightNavigationBars(this, false);
        super.onDestroy();
    }

    private void setupSystemUi() {
        Window w = getWindow();
        WindowInsetsControllerCompat c = new WindowInsetsControllerCompat(w, w.getDecorView());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        c.hide(WindowInsetsCompat.Type.systemBars());
    }

    private void saveHistory(PlaylistInfo playlistModel, SqlDbHelper dbHelper, PlaylistType playlistType){
        if (playlistModel == null
                || playlistModel.channels == null
                || currentChannelPosition < 0
                || currentChannelPosition >= playlistModel.channels.size()) {
            return;
        }

        try {
            PlaylistChannel channel =
                    playlistModel.channels.get(currentChannelPosition);

            if (channel == null) return;

            LocalDbHistory dbHistory = new LocalDbHistory(
                    channel.name,
                    channel.category,
                    channel.url,
                    channel.logoUrl,
                    playlistType,
                    null,
                    null
            );
            dbHelper.saveHistory(dbHistory);
        } catch (Exception e) {
            Log.e("HISTORY", Objects.requireNonNull(e.getMessage()));
        }
    }


    private void saveBeforeLeaving() {
        saveHistoryPosition(sqlDbHelper);
    }


    private void saveHistoryPosition(SqlDbHelper sqlDbHelper) {
        try {
            if (currentUrl == null || currentUrl.isEmpty()
                    || sqlDbHelper == null || player == null) {
                return;
            }

            final boolean isVideo = isVod();
            final PlaylistType expectedType =
                    isVideo ? PlaylistType.VOD : PlaylistType.LIVE;

            final boolean isExists = sqlDbHelper.isHistoryExists(currentUrl);

            // Insert new history.
            if (!isExists) {
                saveHistory(playlistModel, sqlDbHelper, expectedType);
                return;
            }

            // Correct history type if needed.
            if (!sqlDbHelper.isHistoryTypeMatch(currentUrl, expectedType)) {
                boolean updated = sqlDbHelper.updateHistoryType(currentUrl, expectedType);
                Log.d("HISTORY", "History type updated: " + updated);
            }

            // Update playback position only for videos.
            if (isVideo) {
                long positionMs = Math.max(0L, player.getCurrentPosition());
                long durationMs = Math.max(0L, player.getDuration());

                sqlDbHelper.updateHistoryPosition(
                        currentUrl,
                        positionMs,
                        durationMs
                );
            }

        } catch (Exception e) {
            Log.e("PLAYER_DB", "Failed to save history position", e);
        }
    }



    private boolean isVod() {
        return player != null
                && !player.isCurrentMediaItemLive()
                && player.getDuration() > 0;
    }

    // =====================================================================================
    // Source (playlist OR single stream)
    // =====================================================================================

    private PlayerEngineType resolveEngine() {
        String forced = getIntent().getStringExtra(EXTRA_ENGINE);
        PlayerEngineType saved = PlayerSettings.getEngine(this);
        return forced == null ? saved : PlayerEngineType.fromKey(forced, saved);
    }

    private void resolveSourceAndStart() {
        Intent i = getIntent();
        if (i.hasExtra(EXTRA_LIVE)) liveHint = i.getBooleanExtra(EXTRA_LIVE, true);

        String playlistUrl = i.getStringExtra(EXTRA_PLAYLIST_URL);
        if (!TextUtils.isEmpty(playlistUrl) && !playlistUrl.trim().isEmpty()) {
            loadPlaylistAsync(playlistUrl);
            return;
        }

        streamUrl = i.getStringExtra(EXTRA_STREAM_URL);
        if (TextUtils.isEmpty(streamUrl) || streamUrl.trim().isEmpty()) {
            failAndFinish("Stream URL not found");
            return;
        }

        pendingHistoryPosition = null;
        resumePromptPending = false;

        if (i.hasExtra(EXTRA_POSITION)) {
            long position = i.getLongExtra(EXTRA_POSITION, 0L);

            if (position > 0) {
                pendingHistoryPosition = position;
                resumePromptPending = true;
            }
        }

        String title = i.getStringExtra(EXTRA_TITLE);
        if (title == null || title.trim().isEmpty()) title = getString(R.string.video_channel);
        ui.setHeader(title, hostOf(streamUrl));
        currentTitle = title;
        updateNav();
        startOrConnect();
    }

    private void loadPlaylistAsync(String source) {
        showParsingStatus("Loading playlist...");
        executor.execute(() -> {
            PlaylistInfo model;
            try {
                String fileName = isLocalSource(source) ? queryDisplayName(Uri.parse(source)) : UIUtils.extractLabel(source);
                model = parsePlaylist(source, fileName == null ? "playlist.m3u" : fileName);
            } catch (Exception e) {
                model = null;
            }
            PlaylistInfo result = model;
            runOnUiThread(() -> onPlaylistLoaded(result));
        });
    }

    private static boolean isLocalSource(String source) {
        return source.startsWith("content://") || source.startsWith("file://");
    }

    /** URL -> download + parse; content:// or file:// -> read the file and parse it. */
    private PlaylistInfo parsePlaylist(String source, String fileName) throws Exception {
        if (isLocalSource(source)) {
            return PlaylistParser.parseLocal(getContentResolver(), Uri.parse(source), fileName);
        }
        return PlaylistParser.parseRemoteUrl(source, fileName);
    }

    private void onPlaylistLoaded(@Nullable PlaylistInfo model) {
        if (isFinishing() || isDestroyed()) return;
        showParsingStatus("Processing channels...");
        if (model == null) { failAndFinish("Failed to load playlist"); return; }
        if (model.channels == null || model.channels.isEmpty()) { failAndFinish("No channels found in playlist"); return; }

        playlistModel = model;
        if (currentChannelPosition < 0 || currentChannelPosition >= model.channels.size()) currentChannelPosition = 0;
        displayTvPlaylist(model);

        PlaylistChannel channel = model.channels.get(currentChannelPosition);
        streamUrl = channel.url;
        ui.setHeader(channel.name, channel.category);
        updateNav();
        startOrConnect();
        hideParsingStatus();
    }

    private void showParsingStatus(String status) {
        binding.layoutParsing.setVisibility(View.VISIBLE);
        LoaderAnimator.start(binding.loadingView);
        binding.tvParsingStatus.setText(status);
    }

    private void hideParsingStatus() {
        if (binding != null) {
            binding.layoutParsing.setVisibility(View.GONE);
            LoaderAnimator.stop(binding.loadingView);
        }
    }

    private void failAndFinish(String message) {
        hideParsingStatus();
        AppToast.warning(this, message);
        finish();
    }

    private static String hostOf(String url) {
        try {
            String h = Uri.parse(url).getHost();
            return h == null ? "" : h;
        } catch (Exception e) {
            return "";
        }
    }

    private void prepareHistoryResume() {
        if (player == null || TextUtils.isEmpty(streamUrl)) {
            return;
        }

        currentUrl = streamUrl;

        if (mpv != null) {
            mpv.setNextLiveHint(liveHint);
        }

        // Prepare the media without starting playback.
        player.pause();
        player.setMediaItem(
                new MediaItem.Builder()
                        .setMediaId(streamUrl)
                        .setUri(streamUrl)
                        .build()
        );
        player.prepare();

        // The dialog is shown when the media reaches STATE_READY.
    }

    private void showResumeDialogIfReady() {
        if (!resumePromptPending
                || pendingHistoryPosition == null
                || player == null
                || player.getPlaybackState() != Player.STATE_READY
                || isFinishing()
                || isDestroyed()) {
            return;
        }

        if (mpv != null && !mpv.isMediaLoaded()) {
            return;
        }

        long position = pendingHistoryPosition;

        resumePromptPending = false;
        pendingHistoryPosition = null;

        long totalDuration = player.getDuration();

        String message = "Resume from "
                + android.text.format.DateUtils.formatElapsedTime(position / 1000);

        if (totalDuration > 0) {
            message += " of "
                    + android.text.format.DateUtils.formatElapsedTime(
                    totalDuration / 1000);
        }

        new android.app.AlertDialog.Builder(this, R.style.PlayerDialogTheme)
                .setTitle("Resume playback?")
                .setMessage(message)
                .setPositiveButton("Resume", (dialog, which) -> {
                    if (mpv != null) {
                        mpv.setStartPaused(false);
                        mpv.seekWhenLoaded(position);
                    } else {
                        player.seekTo(position);
                        player.play();
                    }
                })
                .setNegativeButton("Start Over", (dialog, which) -> {
                    if (mpv != null) {
                        mpv.setStartPaused(false);
                    }

                    player.seekTo(0);
                    player.play();
                })
                .setCancelable(false)
                .show();
    }

    // =====================================================================================
    // Engine connection
    // =====================================================================================

    private void connectPlayer() {
        if (engineType == PlayerEngineType.MPV) {
            MpvPlayerAdapter a = new MpvPlayerAdapter(this, PlayerSettings.getSubtitleSizeIndex(this));
            if (a.getInitError() == null) {
                mpv = a;
                onPlayerReady(a);
                return;
            }
            a.release();                                   // native libs missing etc.: use Media3 for this session
            AppToast.warning(this, getString(R.string.player_mpv_unavailable));
            engineType = PlayerEngineType.MEDIA3;
        }
        connectMedia3();
    }

    private void connectMedia3() {
        SessionToken token = new SessionToken(this, new ComponentName(this, VideoPlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            if (isFinishing() || isDestroyed() || controllerFuture == null) return;
            try {
                onPlayerReady(controllerFuture.get());
            } catch (Exception e) {
                AppToast.error(this, "Unable to connect to video player");
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /** Common to both engines from here on. */
    private void onPlayerReady(@NonNull Player p) {
        player = p;
        playerView.setResizeMode(mpv != null
                ? AspectRatioFrameLayout.RESIZE_MODE_FILL     // mpv does its own fit/fill inside the surface
                : AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setPlayer(p);                              // video only (use_controller=false)
        ui.attach(p);
        applySubtitleSize(PlayerSettings.getSubtitleSizeIndex(this), false);

        playerListener = new Player.Listener() {
            @Override public void onEvents(@NonNull Player pl, @NonNull Player.Events events) {
                if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_VIDEO_SIZE_CHANGED)) {
                    updatePipParams();                        // keeps the PiP play/pause icon correct
                }

                if (resumePromptPending) {
                    showResumeDialogIfReady();
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    showResumeDialogIfReady();
                }
            }
        };
        p.addListener(playerListener);

        if (resumePromptPending) {
            if (mpv != null) {
                mpv.setStartPaused(true);
            }
            prepareHistoryResume();
        } else {
            ensureStreamPlaying(streamUrl);
        }

    }

    private void playStream(String url) {
        if (player == null || TextUtils.isEmpty(url)) return;
        currentUrl = url;
        if (mpv != null) {
            mpv.setNextLiveHint(liveHint);
        } else if (player.getPlaybackState() == Player.STATE_IDLE || player.getCurrentMediaItem() == null) {
            playerView.setPlayer(null);           // the surface may have been detached while the player was stopped
            playerView.setPlayer(player);         // -> audio without picture otherwise
        }
        player.setMediaItem(new MediaItem.Builder().setMediaId(url).setUri(url).build());
        player.prepare();
        player.play();
        ui.setFavorite(isFavorite(currentUrl));

    }

    /** The Media3 service player outlives the Activity: keep a healthy current item (rotation), replace a stale/failed one. */
    private void ensureStreamPlaying(String url) {
        if (player == null || TextUtils.isEmpty(url)) return;
        currentUrl = url;
        MediaItem current = player.getCurrentMediaItem();
        boolean healthy = current != null && url.equals(current.mediaId) && player.getPlayerError() == null
                && player.getPlaybackState() != Player.STATE_IDLE;
        if (!healthy) playStream(url);
        ui.setFavorite(isFavorite(currentUrl));
    }

    /**
     * ONE play/pause logic for the on-screen button AND the PiP window button. A failed, idle or cleared
     * player (e.g. after the PiP window outlived the stream) is reloaded instead of just "played".
     */
    private void togglePlayPause() {
        Player p = player;
        if (p == null) return;
        int st = p.getPlaybackState();
        if (p.getPlayerError() != null || st == Player.STATE_IDLE || p.getCurrentMediaItem() == null) {
            playStream(currentUrl != null ? currentUrl : streamUrl);
        } else if (st == Player.STATE_ENDED) {
            p.seekToDefaultPosition();
            p.play();
        } else if (p.getPlayWhenReady()) {
            p.pause();
        } else {
            p.play();
        }
    }

    /** +/- 10 s for VOD; live streams are not seekable, so the buttons are dimmed there. */
    private void seekBy(long deltaMs) {
        Player p = player;
        if (p == null) return;
        if (!p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            AppToast.warning(this, getString(R.string.mp_not_seekable));
            return;
        }
        long target = p.getCurrentPosition() + deltaMs;
        long duration = p.getDuration();
        if (duration != C.TIME_UNSET) target = Math.min(target, duration);
        p.seekTo(Math.max(0, target));
        ui.keepAlive();
    }

    // =====================================================================================
    // Buttons (written once, work for both engines)
    // =====================================================================================

    private void setupButtons() {
        setPlayerClick(binding.btnBack, v -> handleBackPress());
        setPlayerClick(binding.btnFavorite, v -> toggleFavorite(playlistModel));
        setPlayerClick(binding.btnMore, v -> showMoreMenu());
        setPlayerClick(binding.playPause, v -> togglePlayPause());
        setPlayerClick(binding.btnPrev, v -> changeChannel(-1));
        setPlayerClick(binding.btnNext, v -> changeChannel(+1));
        setPlayerClick(binding.btnRewind, v -> seekBy(-SEEK_STEP_MS));
        setPlayerClick(binding.btnForward, v -> seekBy(SEEK_STEP_MS));
        setPlayerClick(binding.btnAudio, v -> showAudioDialog());
        setPlayerClick(binding.btnSubtitles, v -> showSubtitleMenu());
        setPlayerClick(binding.btnSpeed, v -> showSpeedDialog());
        setPlayerClick(binding.btnAspect, v -> showAspectMenu());
        setPlayerClick(binding.btnChannels, v -> {
            if (isTv) toggleTvPlaylist();
            else showChannelsBottomSheet();
        });
        if (isTv) {
            View reloadAction = findViewById(R.id.btnReloadAction);
            View infoAction = findViewById(R.id.btnInfoAction);
            if (reloadAction != null) setPlayerClick(reloadAction,
                    v -> playStream(currentUrl != null ? currentUrl : streamUrl));
            if (infoAction != null) setPlayerClick(infoAction, v -> showStreamInfo());
        }
        setPlayerClick(binding.btnFullscreen, v -> toggleOrientation());
        setPlayerClick(binding.addPlayListPlusBtn, v -> showAddChannelDialog());
        setPlayerClick(binding.btnAddPlaylistAction, v -> showAddChannelDialog());
        setPlayerClick(binding.liveRow, v -> { if (isLiveNow()) jumpToLive(); });
        setPlayerClick(binding.btnRetry, v -> playStream(currentUrl != null ? currentUrl : streamUrl));
        binding.errorPanel.setOnLongClickListener(v -> {      // copy the technical details of a failed stream
            copyText(buildErrorInfo());
            return true;
        });
    }

    private void setPlayerClick(@NonNull View view, @NonNull View.OnClickListener listener) {
        if (isTv) view.setOnClickListener(listener);
        else ClickUtils.applyTouchEffect(view, listener);
    }

    private void setupTvPlaylist() {
        tvPlaylistPanel = findViewById(R.id.tvPlaylistPanel);
        tvPlaylistRecycler = findViewById(R.id.tvPlaylistRecycler);
        if (tvPlaylistPanel == null || tvPlaylistRecycler == null) return;

        tvPlaylistPanel.setVisibility(View.GONE);
        tvPlaylistRecycler.setLayoutManager(new LinearLayoutManager(this));
        final int itemGap = getResources().getDimensionPixelSize(R.dimen.space_6);
        tvPlaylistRecycler.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                       @NonNull RecyclerView parent,
                                       @NonNull RecyclerView.State state) {
                outRect.bottom = itemGap;
            }
        });
        tvPlaylistRecycler.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(@NonNull View view) {
                view.setOnFocusChangeListener((focused, hasFocus) -> {
                    if (!hasFocus || tvPlaylistRecycler == null) return;
                    int position = tvPlaylistRecycler.getChildAdapterPosition(focused);
                    if (position != RecyclerView.NO_POSITION) tvPlaylistLastFocusedPosition = position;
                });
            }

            @Override public void onChildViewDetachedFromWindow(@NonNull View view) { }
        });
        tvPlaylistAdapter = new PlaylistAdapter();
        tvPlaylistRecycler.setAdapter(tvPlaylistAdapter);
        tvPlaylistAdapter.setOnChannelClickListener((channel, position) -> playChannelAt(position));
        setTvPlaylistVisible(false);
    }

    private void displayTvPlaylist(@NonNull PlaylistInfo model) {
        if (!isTv || tvPlaylistPanel == null || tvPlaylistRecycler == null || tvPlaylistAdapter == null) return;

        tvPlaylistAdapter.setChannels(model.channels);
        tvPlaylistAdapter.setPlayingPosition(currentChannelPosition);
        setTvPlaylistVisible(true);

        if (!tvPlaylistInitialFocusRequested) {
            tvPlaylistInitialFocusRequested = true;
            tvPlaylistLastFocusedPosition = currentChannelPosition;
            focusTvPlaylistItem(true);
        }
    }

    private void clearTvPlaylist() {
        boolean playlistHadFocus = tvPlaylistPanel != null && tvPlaylistPanel.hasFocus();
        tvPlaylistInitialFocusRequested = false;
        if (tvPlaylistAdapter != null) {
            tvPlaylistAdapter.setChannels(java.util.Collections.emptyList());
        }
        if (isTv) {
            setTvPlaylistVisible(false);
            if (playlistHadFocus) {
                if (ui != null) ui.onUserActivity();
                binding.playPause.requestFocus();
            }
        }
    }

    private void toggleTvPlaylist() {
        if (!isTv || tvPlaylistPanel == null || tvPlaylistAdapter == null
                || tvPlaylistAdapter.getItemCount() == 0) return;

        boolean show = tvPlaylistPanel.getVisibility() != View.VISIBLE;
        boolean playlistHadFocus = tvPlaylistPanel.hasFocus();
        if (!show) rememberTvPlaylistFocus();
        setTvPlaylistVisible(show);
        if (show) {
            focusTvPlaylistItem(true);
        } else if (playlistHadFocus) {
            if (ui != null) ui.onUserActivity();
            binding.playPause.requestFocus();
        }
    }

    private void rememberTvPlaylistFocus() {
        if (tvPlaylistRecycler == null) return;
        View focused = tvPlaylistRecycler.findFocus();
        View item = focused == null ? null : tvPlaylistRecycler.findContainingItemView(focused);
        if (item == null) return;
        int position = tvPlaylistRecycler.getChildAdapterPosition(item);
        if (position != RecyclerView.NO_POSITION) tvPlaylistLastFocusedPosition = position;
    }

    private void focusTvPlaylistItem(boolean scrollIfNeeded) {
        if (tvPlaylistRecycler == null || tvPlaylistAdapter == null) return;
        tvPlaylistRecycler.post(() -> {
            if (tvPlaylistRecycler == null || tvPlaylistPanel == null
                    || tvPlaylistPanel.getVisibility() != View.VISIBLE
                    || tvPlaylistAdapter == null || tvPlaylistAdapter.getItemCount() == 0) return;
            int position = tvPlaylistLastFocusedPosition;
            if (position < 0 || position >= tvPlaylistAdapter.getItemCount()) {
                position = Math.max(0, Math.min(currentChannelPosition, tvPlaylistAdapter.getItemCount() - 1));
                tvPlaylistLastFocusedPosition = position;
            }
            RecyclerView.ViewHolder holder = tvPlaylistRecycler.findViewHolderForAdapterPosition(position);
            if (holder != null) {
                holder.itemView.requestFocus();
            } else if (scrollIfNeeded) {
                tvPlaylistRecycler.scrollToPosition(position);
                final int target = position;
                tvPlaylistRecycler.post(() -> {
                    RecyclerView.ViewHolder visible = tvPlaylistRecycler == null
                            ? null : tvPlaylistRecycler.findViewHolderForAdapterPosition(target);
                    if (visible != null) visible.itemView.requestFocus();
                });
            }
        });
    }

    private void setTvPlaylistVisible(boolean visible) {
        if (!isTv || tvPlaylistPanel == null) return;

        ConstraintLayout.LayoutParams params =
                (ConstraintLayout.LayoutParams) binding.videoContainer.getLayoutParams();
        if (visible) {
            params.endToStart = tvPlaylistPanel.getId();
            params.endToEnd = ConstraintSet.UNSET;
        } else {
            params.endToStart = ConstraintSet.UNSET;
            params.endToEnd = ConstraintSet.PARENT_ID;
        }
        binding.videoContainer.setLayoutParams(params);
        tvPlaylistPanel.setVisibility(visible ? View.VISIBLE : View.GONE);

        boolean hasChannels = tvPlaylistAdapter != null && tvPlaylistAdapter.getItemCount() > 0;
        binding.btnChannels.setEnabled(hasChannels);
        binding.btnChannels.setAlpha(hasChannels ? 1f : 0.35f);
        binding.btnChannels.setSelected(visible);
        binding.btnChannels.setContentDescription(getString(
                visible ? R.string.tv_hide_playlist : R.string.tv_show_playlist));
        binding.playPause.setNextFocusRightId(visible ? R.id.tvPlaylistRecycler : View.NO_ID);
    }

    private void showAddChannelDialog() {
        String channelUrl = currentUrl != null ? currentUrl : streamUrl;

        if (TextUtils.isEmpty(channelUrl)) {
            AppToast.error(this, "Unable to save this channel");
            return;
        }

        if (saveDialog != null) saveDialog.dismiss();

        saveDialog = SaveChannelDialog.show(
                this,
                channelUrl,
                IPTV_CATEGORIES,
                null,
                category -> {
                    int index = IPTV_CATEGORIES.indexOf(category);

                    if (index == -1) return;

                    try {
                        LocalDbChannel dbChannel = getDbChannel(channelUrl, index);

                        boolean result = sqlDbHelper.addChannel(dbChannel);

                        if (result) {
                            AppToast.success(this, "Channel Added.");
                        } else {
                            AppToast.error(this, "Channel already exists or insert failed.");
                        }

                    } catch (Exception e) {
                        Log.e("PLAYER_DB", "Add channel failed", e);
                        AppToast.error(this, "Unable to add channel.");
                    }
                }
        );
    }


    @NonNull
    private LocalDbChannel getDbChannel(String channelUrl, int index) {
        LocalDbChannel dbChannel;

        if (playlistModel != null
                && currentChannelPosition >= 0
                && currentChannelPosition < playlistModel.channels.size()) {

            PlaylistChannel channel =
                    playlistModel.channels.get(currentChannelPosition);

            dbChannel = new LocalDbChannel(
                    channel.name,
                    channel.logoUrl,
                    channelUrl,
                    index
            );

        } else {
            dbChannel = new LocalDbChannel(
                    currentTitle,
                    null,
                    channelUrl,
                    index
            );
        }
        return dbChannel;
    }


    private String buildErrorInfo() {
        StringBuilder s = new StringBuilder("Engine: ").append(engineType.key);
        if (player != null && player.getPlayerError() != null) {
            s.append("\nError: ").append(player.getPlayerError().getMessage())
                    .append("\nCode: ").append(player.getPlayerError().getErrorCodeName());
        }
        return s.append("\nStream URL: ").append(currentUrl).toString();
    }

    private boolean isLiveNow() {

        if (player == null) return false;

        return player.isCurrentMediaItemLive()
                || (player.getPlaybackState() == Player.STATE_READY
                && player.getDuration() == C.TIME_UNSET);
    }


    private void jumpToLive() {
        if (player == null) return;
        player.seekToDefaultPosition();     // mpv adapter maps this to jumpToLive(); Media3 jumps to the live edge
        player.play();
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void toggleOrientation() {
        setRequestedOrientation(isLandscape()
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    // ---- channel navigation (prev / next buttons, PiP buttons, channel list)
    private void changeChannel(int delta) {
        if (playlistModel == null) return;
        int idx = currentChannelPosition + delta;
        if (idx < 0 || idx >= playlistModel.channels.size()) return;
        playChannelAt(idx);
    }

    private boolean playChannelAt(int index) {
        if (playlistModel == null || player == null) { AppToast.error(this, "Player is not ready"); return false; }
        PlaylistChannel channel = playlistModel.channels.get(index);
        if (channel.url == null || channel.url.trim().isEmpty()) { AppToast.error(this, "Invalid channel URL"); return false; }
        currentChannelPosition = index;
        streamUrl = channel.url;
        playStream(channel.url);
        ui.setHeader(channel.name, channel.category);
        updateNav();
        updatePipParams();
        return true;
    }

    private void updateNav() {
        int n = playlistModel == null ? 0 : playlistModel.channels.size();
        ui.setNav(n > 1, n > 1 && currentChannelPosition > 0, n > 1 && currentChannelPosition < n - 1);
        if (tvPlaylistAdapter != null) tvPlaylistAdapter.setPlayingPosition(currentChannelPosition);
        if (isTv && tvPlaylistAdapter != null) {
            boolean hasChannels = tvPlaylistAdapter.getItemCount() > 0;
            binding.btnChannels.setEnabled(hasChannels);
            binding.btnChannels.setAlpha(hasChannels ? 1f : 0.35f);
        }
    }

    // ---- aspect
    private void showAspectMenu() {
        if (mpv == null) {   // Media3: existing helper
            AspectRatioUtils.showMenu(this, playerView, aspectMode, index -> aspectMode = index);
            return;
        }
        CharSequence[] labels = new CharSequence[ASPECT_LABELS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = getString(ASPECT_LABELS[i]);
        showDialog(dialogBuilder().setTitle(R.string.player_ctl_aspect)
                .setSingleChoiceItems(labels, aspectMode, (d, which) -> {
                    aspectMode = which;
                    if (mpv != null) mpv.setAspectMode(which);
                    ui.setAspectLabel(ASPECT_LABELS[which]);
                    d.dismiss();
                }).setNegativeButton(R.string.mp_cancel, null));
    }

    // ---- audio / speed
    private void showAudioDialog() {
        if (player == null) return;
        if (!player.getCurrentTracks().containsType(C.TRACK_TYPE_AUDIO)) { AppToast.warning(this, getString(R.string.mp_no_audio)); return; }
        showTrackDialog(getString(R.string.mp_dlg_audio), C.TRACK_TYPE_AUDIO);
    }

    private void showTrackDialog(String title, int trackType) {
        Dialog d = new TrackSelectionDialogBuilder(this, title, player, trackType)
                .setTheme(R.style.PlayerDialogTheme)
                .setShowDisableOption(trackType == C.TRACK_TYPE_TEXT)
                .build();
        ui.setAutoHideBlocked(true);
        d.setOnDismissListener(x -> { if (ui != null) ui.setAutoHideBlocked(false); });
        d.show();
        accentTitle(d);
    }

    private void showSpeedDialog() {
        if (player == null) return;
        if (isLiveNow()) { AppToast.warning(this, getString(R.string.mp_speed_live)); return; }
        CharSequence[] labels = new CharSequence[SPEEDS.length];
        int checked = 2;
        float current = player.getPlaybackParameters().speed;
        for (int i = 0; i < SPEEDS.length; i++) {
            labels[i] = PlayerControlsUi.formatSpeed(SPEEDS[i]);
            if (Math.abs(SPEEDS[i] - current) < 0.01) checked = i;
        }
        showDialog(dialogBuilder().setTitle(R.string.mp_dlg_speed)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    if (player != null) player.setPlaybackParameters(new PlaybackParameters((float) SPEEDS[which]));
                    d.dismiss();
                }).setNegativeButton(R.string.mp_cancel, null));
    }

    // ---- subtitles
    private void showSubtitleMenu() {
        if (player == null) return;
        CharSequence[] items = {getString(R.string.mp_dlg_subtitles), getString(R.string.mp_add_subtitle_file),
                getString(R.string.mp_sub_size_item)};
        showDialog(dialogBuilder().setTitle(R.string.mp_dlg_subtitles)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        if (player == null || !player.getCurrentTracks().containsType(C.TRACK_TYPE_TEXT)) {
                            AppToast.warning(this, "No subtitle tracks in this stream");
                            return;
                        }
                        showTrackDialog(getString(R.string.mp_dlg_subtitles), C.TRACK_TYPE_TEXT);
                    } else if (which == 1) {
                        subtitlePicker.launch(new String[]{"*/*"});
                    } else {
                        handler.post(this::showSubtitleSizeDialog);
                    }
                }).setNegativeButton(R.string.mp_cancel, null));
    }

    private void showSubtitleSizeDialog() {
        CharSequence[] labels = new CharSequence[SUB_SIZE_LABELS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = getString(SUB_SIZE_LABELS[i]);
        showDialog(dialogBuilder().setTitle(R.string.mp_dlg_sub_size)
                .setSingleChoiceItems(labels, PlayerSettings.getSubtitleSizeIndex(this), (d, which) -> {
                    applySubtitleSize(which, true);
                    d.dismiss();
                }).setNegativeButton(R.string.mp_cancel, null));
    }

    private void applySubtitleSize(int index, boolean persist) {
        index = Math.max(0, Math.min(SUB_SIZE_LABELS.length - 1, index));
        if (persist) PlayerSettings.setSubtitleSizeIndex(this, index);
        if (mpv != null) {
            mpv.setSubtitleSizeIndex(index);
        } else {
            SubtitleView sv = playerView.getSubtitleView();
            if (sv != null) sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * MEDIA3_SUB_SCALE[index]);
        }
    }

    private void onSubtitlePicked(@Nullable Uri uri) {
        if (uri == null || player == null || currentUrl == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { /* not every provider offers a persistable grant */ }

        if (mpv != null) {                              // mpv can attach a subtitle live, no reload
            mpv.addExternalSubtitle(uri);
            AppToast.success(this, "Subtitle added");
            return;
        }
        String name = queryDisplayName(uri);
        MediaItem.SubtitleConfiguration subtitle = new MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(subtitleMimeType(name))
                .setLabel(name)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build();
        long position = player.isCurrentMediaItemLive() ? C.TIME_UNSET : player.getCurrentPosition();
        boolean playWhenReady = player.getPlayWhenReady();
        player.setMediaItem(new MediaItem.Builder().setMediaId(currentUrl).setUri(currentUrl)
                .setSubtitleConfigurations(ImmutableList.of(subtitle)).build(), position);
        player.prepare();
        player.setPlayWhenReady(playWhenReady);
        AppToast.success(this, "Subtitle added");
    }

    @Nullable
    private String queryDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return uri.getLastPathSegment();
    }

    private static String subtitleMimeType(@Nullable String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".vtt")) return MimeTypes.TEXT_VTT;
        if (n.endsWith(".ass") || n.endsWith(".ssa")) return MimeTypes.TEXT_SSA;
        if (n.endsWith(".ttml") || n.endsWith(".xml") || n.endsWith(".dfxp")) return MimeTypes.APPLICATION_TTML;
        return MimeTypes.APPLICATION_SUBRIP;
    }

    // ---- favorites (local, keyed by stream URL)
    private boolean isFavorite(String url) {
        return url != null && sqlDbHelper.isFavorite(url);
    }

    private void toggleFavorite(PlaylistInfo playlistModel) {
        try {
            if (TextUtils.isEmpty(currentUrl)) return;
            LocalDbFavorites favorite = getDbFavorites(playlistModel);
            boolean status = sqlDbHelper.setFavorite(favorite);
            if (!status) {
                AppToast.error(this, "Could not update saved channel");
                return;
            }
            boolean isSaved = isFavorite(currentUrl);
            ui.setFavorite(isSaved);
            if (isTv) {
                AppToast.success(
                        this,
                        isSaved ? "Channel saved" : "Channel removed from saved"
                );
            }
        } catch (Exception e) {
            Log.e("PLAYER_DB", "Favorite update failed", e);
        }
    }

    @NonNull
    private LocalDbFavorites getDbFavorites(PlaylistInfo playlistModel) {
        String name = currentTitle;
        String category = "Other";
        String channelIcon = null;

        if (playlistModel != null
                && playlistModel.channels != null
                && currentChannelPosition >= 0
                && currentChannelPosition < playlistModel.channels.size()) {
            PlaylistChannel channel = playlistModel.channels.get(currentChannelPosition);
            if (channel.name != null) {name = channel.name;}
            if (channel.category != null) {category = channel.category;}
            if(channel.logoUrl != null) {channelIcon = channel.logoUrl;}
        }
        return new LocalDbFavorites(name, category, currentUrl, channelIcon,  isVod() ? PlaylistType.VOD : PlaylistType.LIVE);
    }

    // ---- "more" menu: info, reload, jump to live, copy url, screenshot (mpv), switch engine
    private void showMoreMenu() {
        if (player == null) return;
        PopupMenu menu = new PopupMenu(new ContextThemeWrapper(this, R.style.PlayerPopupMenuTheme), binding.btnMore);
        menu.getMenu().add(0, 1, 0, R.string.mp_menu_info);
        menu.getMenu().add(0, 2, 1, R.string.mp_menu_reload);
        if (isLiveNow()) menu.getMenu().add(0, 3, 2, R.string.mp_menu_jump_live);
        menu.getMenu().add(0, 4, 3, R.string.mp_menu_copy_url);
        if (mpv != null) menu.getMenu().add(0, 5, 4, R.string.mp_menu_screenshot);
        menu.getMenu().add(0, 6, 5, R.string.player_menu_engine);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: showStreamInfo(); return true;
                case 2: playStream(currentUrl); return true;
                case 3: jumpToLive(); return true;
                case 4: copyText(currentUrl); return true;
                case 5: takeScreenshot(); return true;
                case 6: PlayerSettings.showEnginePicker(this, t -> switchEngine()); return true;
                default: return false;
            }
        });
        ui.setAutoHideBlocked(true);
        menu.setOnDismissListener(m -> { if (ui != null) ui.setAutoHideBlocked(false); });
        menu.show();
    }

    /** Re-creates the Activity: onCreate() reads the new engine from the settings and resumes the same channel. */
    private void switchEngine() {
        switchingEngine = true;
        getIntent().removeExtra(EXTRA_ENGINE);
        recreate();
    }

    private void showStreamInfo() {
        if (player == null) return;
        StringBuilder s = new StringBuilder();
        s.append("Engine: ").append(engineType.key).append("\n\n");
        s.append("State: ").append(player.getPlaybackState()).append('\n');
        s.append("Live: ").append(isLiveNow()).append("   Duration: ").append(player.getDuration()).append('\n');
        s.append("Speed: ").append(player.getPlaybackParameters().speed).append('\n');
        if (mpv != null) s.append('\n').append(mpv.buildInfoText());
        if (currentUrl != null) s.append("\nURL: ").append(currentUrl);
        showDialog(dialogBuilder().setTitle(R.string.mp_menu_info).setMessage(s).setPositiveButton(R.string.mp_close, null));
    }

    private void takeScreenshot() {
        if (mpv == null) return;
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir == null) dir = getCacheDir();
        final File file = new File(dir, "mpv_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg");
        mpv.takeScreenshot(file);
        handler.postDelayed(() -> {   // mpv reports no completion: check the file shortly afterwards
            if (isDestroyed()) return;
            if (file.exists() && file.length() > 0) AppToast.success(this, getString(R.string.mp_screenshot_saved, file.getName()));
            else AppToast.error(this, getString(R.string.mp_screenshot_failed));
        }, 800);
    }

    private void copyText(@Nullable String text) {
        if (text == null) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("stream", text));
            AppToast.success(this, getString(R.string.mp_url_copied));
        }
    }

    // ---- dialogs (black window, accent title/buttons); controls never fade while one is open
    private AlertDialog.Builder dialogBuilder() {
        return new AlertDialog.Builder(this, R.style.PlayerDialogTheme);
    }

    private void showDialog(AlertDialog.Builder builder) {
        ui.setAutoHideBlocked(true);
        builder.setOnDismissListener(d -> { dialog = null; if (ui != null) ui.setAutoHideBlocked(false); });
        dialog = builder.show();

        if(dialog != null) {
            remoteKeys.registerDialog(dialog);
            dialog.setOnDismissListener(d -> remoteKeys.unregisterDialog(dialog));
            styleDialog(dialog);
        }
    }

    private void styleDialog(AlertDialog d) {
        int accent = ContextCompat.getColor(this, R.color.primary_highlight);
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_black);
        accentTitle(d);
        for (int w : new int[]{DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_NEUTRAL}) {
            Button btn = d.getButton(w);
            if (btn != null) btn.setTextColor(accent);
        }
        TextView message = d.findViewById(android.R.id.message);
        if (message != null) message.setTextColor(ContextCompat.getColor(this, R.color.white));
    }

    private void accentTitle(Dialog d) {
        TextView title = d.findViewById(androidx.appcompat.R.id.alertTitle);
        if (title != null) {
            title.setTextColor(ContextCompat.getColor(this, R.color.primary_highlight));
            title.setTypeface(com.aaronmaxlab.maxplayer.utils.ThemeTypography.bold(this));
        }
    }

    // =====================================================================================
    // Picture-in-picture
    // =====================================================================================

    private boolean isInPip() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode();
    }

    private boolean canEnterPip() {
        return !isTv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && player != null && player.isPlaying() && !isInPip();
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            enterPictureInPictureMode(buildPipParams());
        }  catch (IllegalStateException e) {
        finish();
    }
    }

    /**
     * Our OWN actions are what stops Android from showing the media-session controls of the Media3
     * service in the PiP window (those are the "wrong controls", and they fail once the service player
     * was stopped/cleared). Each button comes back to {@link #pipReceiver}.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private PictureInPictureParams buildPipParams() {
        Rational ratio = new Rational(16, 9);
        if (player != null) {
            VideoSize vs = player.getVideoSize();
            if (vs.width > 0 && vs.height > 0) {
                double r = (double) vs.width / vs.height;
                if (r > 2.39) ratio = new Rational(239, 100);
                else if (r < 1 / 2.39) ratio = new Rational(100, 239);
                else ratio = new Rational(vs.width, vs.height);
            }
        }
        List<RemoteAction> actions = new ArrayList<>();
        boolean hasList = playlistModel != null && playlistModel.channels.size() > 1;
        if (hasList && currentChannelPosition > 0) {
            actions.add(pipAction(R.drawable.ic_prev, R.string.player_pip_prev, PIP_PREV));
        }
        boolean playing = player != null && player.getPlayWhenReady()
                && player.getPlaybackState() != Player.STATE_ENDED && player.getPlayerError() == null;
        actions.add(pipAction(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                playing ? R.string.cd_pause : R.string.cd_play, PIP_PLAY_PAUSE));
        if (hasList && currentChannelPosition < playlistModel.channels.size() - 1) {
            actions.add(pipAction(R.drawable.ic_next, R.string.player_pip_next, PIP_NEXT));
        }
        return new PictureInPictureParams.Builder().setAspectRatio(ratio).setActions(actions).build();
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private RemoteAction pipAction(@DrawableRes int iconRes, int titleRes, int cmd) {
        Intent intent = new Intent(ACTION_PIP).setPackage(getPackageName()).putExtra(EXTRA_PIP_CMD, cmd);
        PendingIntent pi = PendingIntent.getBroadcast(this, cmd, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = getString(titleRes);
        return new RemoteAction(whiteIcon(iconRes), title, title, pi);
    }


    private Icon whiteIcon(@DrawableRes int res) {
        return Icon.createWithResource(this, res);
    }


    private void updatePipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isDestroyed() || binding == null) return;
        if (!isInPip()) return;
        try {
            setPictureInPictureParams(buildPipParams());
        } catch (IllegalStateException ignored) { /* window not ready */ }
    }

    private void registerPipReceiver() {
        if (pipReceiverRegistered) return;
        ContextCompat.registerReceiver(this, pipReceiver, new IntentFilter(ACTION_PIP), ContextCompat.RECEIVER_NOT_EXPORTED);
        pipReceiverRegistered = true;
    }

    private void unregisterPipReceiver() {
        if (!pipReceiverRegistered) return;
        try {
            unregisterReceiver(pipReceiver);
        } catch (IllegalArgumentException ignored) { }
        pipReceiverRegistered = false;
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPip, newConfig);
        if (ui != null) ui.setPipMode(inPip);                 // hide the glass overlay in the small window
        if (inPip) {
            wasInPipMode = true;
            registerPipReceiver();
            updatePipParams();
        } else {
            unregisterPipReceiver();
        }
    }

    private void handleBackPress() {
        saveBeforeLeaving();
        if (canEnterPip()) enterPipMode();
        else finish();
    }

    // =====================================================================================
    // Channels bottom sheet (playlist mode)
    // =====================================================================================

    private void showChannelsBottomSheet() {
        if (playlistModel == null || playlistModel.channels == null) {
            AppToast.warning(this, getString(R.string.mp_no_channels));
            return;
        }

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(R.layout.playlist_drawer);
        RecyclerView rvChannels = sheet.findViewById(R.id.rvChannels);
        if (rvChannels == null) return;

        selectedCategory = CATEGORY_ALL;
        searchQuery = "";
        showSearchBox = false;

        adapter = new PlaylistAdapter();
        rvChannels.setLayoutManager(new LinearLayoutManager(this));
        rvChannels.setAdapter(adapter);
        adapter.setChannels(playlistModel.channels);
        adapter.setPlayingPosition(currentChannelPosition);

        TextView topLabel = sheet.findViewById(R.id.topLabelView);
        if (topLabel != null) {
            topLabel.setText(getString(R.string.drawer_header,
                    UIUtils.extractLabel(playlistModel.fileName).toUpperCase(), playlistModel.channelCount));
        }
        setupCategoryList(sheet.findViewById(R.id.categoryList));
        setupSearch(sheet);

        adapter.setOnChannelClickListener((channel, position) -> onChannelSelected(channel, sheet));
        ui.setAutoHideBlocked(true);
        sheet.setOnShowListener(d -> styleSheetNavigationBar(sheet));
        sheet.setOnDismissListener(d -> { adapter = null; if (ui != null) ui.setAutoHideBlocked(false); });
        sheet.show();
    }

    private void onChannelSelected(PlaylistChannel channel, BottomSheetDialog sheet) {
        int originalPosition = playlistModel == null ? -1 : playlistModel.channels.indexOf(channel);   // adapter position may be filtered
        if (originalPosition == -1) { AppToast.error(this, "Channel not found in playlist"); return; }
        if (playChannelAt(originalPosition)) sheet.dismiss();
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

    private void setupCategoryList(@Nullable LinearLayout categoryList) {
        if (categoryList == null) return;
        categoryList.removeViews(1, Math.max(0, categoryList.getChildCount() - 1));   // keep the "All" chip (child 0)

        for (String category : getCategories()) {
            LinearLayout chip = createCategoryChip(category);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(8));
            categoryList.addView(chip, params);
            chip.setOnClickListener(v -> {
                selectedCategory = category;
                selectCategoryChip(chip, categoryList);
                applyChannelFilters();
            });
        }
        View allChip = categoryList.findViewById(R.id.categoryContainer);
        if (allChip != null) {
            allChip.setOnClickListener(v -> {
                selectedCategory = CATEGORY_ALL;
                selectCategoryChip(allChip, categoryList);
                applyChannelFilters();
            });
        }
    }

    private LinearLayout createCategoryChip(String category) {
        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setBackgroundResource(R.drawable.bg_chip);

        TextView label = new TextView(this);
        label.setText(category);
        label.setTextColor(getColor(R.color.ink_dim));
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.text_body_sm));
        label.setTypeface(com.aaronmaxlab.maxplayer.utils.ThemeTypography.medium(this));
        chip.addView(label);
        return chip;
    }

    private void selectCategoryChip(View selectedChip, LinearLayout categoryList) {
        for (int i = 0; i < categoryList.getChildCount(); i++) {
            View child = categoryList.getChildAt(i);
            boolean selected = child == selectedChip;
            child.setBackgroundResource(selected ? R.drawable.bg_chip_active : R.drawable.bg_chip);
            if (child instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) child;
                for (int j = 0; j < group.getChildCount(); j++) {
                    View inner = group.getChildAt(j);
                    if (inner instanceof TextView) {
                        ((TextView) inner).setTextColor(getColor(selected ? R.color.primary_highlight : R.color.ink_dim));
                    }
                }
            }
        }
    }

    private List<String> getCategories() {
        Set<String> unique = new LinkedHashSet<>();
        if (playlistModel != null && playlistModel.channels != null) {
            for (PlaylistChannel channel : playlistModel.channels) {
                String category = normalizedCategory(channel);
                if (!category.isEmpty()) unique.add(category);
            }
        }
        return new ArrayList<>(unique);
    }

    private static String normalizedCategory(PlaylistChannel channel) {
        return channel.category == null ? "" : channel.category.trim();
    }

    private void setupSearch(BottomSheetDialog sheet) {
        ImageView searchBtn = sheet.findViewById(R.id.searchButton);
        TextInputLayout searchBox = sheet.findViewById(R.id.channelSearchLayout);
        TextInputEditText searchEditText = sheet.findViewById(R.id.etChannelSearch);
        if (searchBtn == null || searchBox == null || searchEditText == null) return;

        ClickUtils.applyTouchEffect(searchBtn, v -> {
            showSearchBox = !showSearchBox;
            if (showSearchBox) {
                searchBox.setVisibility(View.VISIBLE);
                searchBtn.setImageResource(R.drawable.ic_close);
                searchEditText.requestFocus();
            } else {
                searchEditText.setText("");     // also clears the filter via TextWatcher
                searchBox.setVisibility(View.GONE);
                searchBtn.setImageResource(R.drawable.ic_search);
            }
        });
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim();
                applyChannelFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    /** Single place that combines category + search filters. */
    private void applyChannelFilters() {
        if (adapter == null || playlistModel == null) return;
        boolean allCategories = CATEGORY_ALL.equals(selectedCategory);
        String query = searchQuery.toLowerCase(Locale.ROOT);

        List<PlaylistChannel> filtered = new ArrayList<>();
        for (PlaylistChannel channel : playlistModel.channels) {
            boolean matchesCategory = allCategories || selectedCategory.equalsIgnoreCase(normalizedCategory(channel));
            boolean matchesSearch = query.isEmpty()
                    || (channel.name != null && channel.name.toLowerCase(Locale.ROOT).contains(query));
            if (matchesCategory && matchesSearch) filtered.add(channel);
        }
        adapter.setChannels(filtered);

        int playingIndex = filtered.indexOf(playlistModel.channels.get(currentChannelPosition));
        if (playingIndex >= 0) adapter.setPlayingPosition(playingIndex);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
