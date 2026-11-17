package com.aaronmaxlab.maxplayer.player.mpv;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import com.maxplayer.mpv.AudioTrackInfo;
import com.maxplayer.mpv.BufferingInfo;
import com.maxplayer.mpv.LiveStatus;
import com.maxplayer.mpv.LoadOptions;
import com.maxplayer.mpv.MediaInfo;
import com.maxplayer.mpv.MpvError;
import com.maxplayer.mpv.MpvPlayer;
import com.maxplayer.mpv.MpvPlayerConfig;
import com.maxplayer.mpv.MpvPlayerListener;
import com.maxplayer.mpv.PlaybackState;
import com.maxplayer.mpv.PlaybackStats;
import com.maxplayer.mpv.SubtitleTrackInfo;
import com.maxplayer.mpv.VideoInfo;
import com.maxplayer.mpv.VideoScaleMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Wraps the mpv engine as a regular media3 {@link Player}.
 *
 * <p>Because it IS a media3 Player, the same {@code PlayerView} + {@code video_controls.xml} +
 * {@code PlayerActivity} code drives both engines: play/pause, seek bar, rewind/forward, speed,
 * audio / subtitle track dialogs (TrackSelectionDialogBuilder), buffering spinner, error callbacks,
 * LIVE/VIDEO indicator and picture-in-picture all work the same.
 *
 * <p>Everything runs on the main thread (mpv delivers its callbacks there).
 */
@UnstableApi
public final class MpvPlayerAdapter extends SimpleBasePlayer {

    private static final String TAG = "MpvAdapter";
    private static final long SEEK_INCREMENT_MS = 10_000;
    private static final double[] SUB_SIZES = {20, 26, 32, 40};

    private static final String GROUP_VIDEO = "v:0";
    private static final String PREFIX_AUDIO = "a:";
    private static final String PREFIX_SUB = "s:";

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable private MpvPlayer engine;
    @Nullable private String initError;

    @Nullable private MediaItem currentItem;
    @Nullable private Boolean nextLiveHint;
    @Nullable private PlaybackException error;

    private boolean loaded;
    private boolean firstFrameSeen;
    private boolean firstFramePending;
    private boolean resumeAfterSurface;
    private boolean destroyed;
    private boolean tracksDirty = true;
    @Nullable private Long pendingSeekPositionMs;
    private boolean startPaused;

    private int subtitleSizeIndex;
    @Nullable private Integer userSubtitleId;

    private Tracks cachedTracks = Tracks.EMPTY;
    private TrackSelectionParameters cachedParams = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;

    @Nullable private SurfaceHolder attachedHolder;
    @Nullable private SurfaceHolder.Callback engineSurfaceCb;

    public MpvPlayerAdapter(@NonNull Context context, int subtitleSizeIndex) {
        super(Looper.getMainLooper());
        this.appContext = context.getApplicationContext();
        this.subtitleSizeIndex = clampIndex(subtitleSizeIndex);
        createEngine();
    }

    // =====================================================================================
    // Engine setup
    // =====================================================================================

    private void createEngine() {
        String caPath = null;
        try {
            caPath = copyCaBundle();
        } catch (IOException e) {
            Log.e(TAG, "CA bundle copy failed", e);
        }
        try {
            MpvPlayerConfig.Builder cfg = MpvPlayerConfig.builder(MpvPlayerConfig.BufferingProfile.BALANCED)
                    .networkTimeoutSeconds(15)
                    .retry(3, 1000, 2.0)
                    .hardwareDecoding(MpvPlayerConfig.HardwareDecoding.AUTO_ANDROID)
                    .subtitleFontSize(SUB_SIZES[subtitleSizeIndex])
                    .tlsVerify(true);
            if (caPath != null) cfg.tlsCaFile(caPath);
            MpvPlayer p = MpvPlayer.create(appContext, cfg.build());
            p.addListener(new EngineListener());
            p.initialize();
            Log.i(TAG, p.getMpvVersion() + " | " + p.getFfmpegVersion() + " | rejected options: " + p.getOptionReport());
            engine = p;
        } catch (RuntimeException | UnsatisfiedLinkError e) {   // native libs missing, bad config...
            Log.e(TAG, "Engine start failed", e);
            engine = null;
            initError = String.valueOf(e.getMessage());
        }
    }

    /** Copies assets/cacert.pem to filesDir once (mbedTLS builds need a PEM bundle for https). */
    private String copyCaBundle() throws IOException {
        File out = new File(appContext.getFilesDir(), "cacert.pem");
        if (out.exists() && out.length() > 0) return out.getAbsolutePath();
        try (InputStream in = appContext.getAssets().open("cacert.pem");
             FileOutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
        }
        return out.getAbsolutePath();
    }

    /** Non-null when the native engine could not start (the Activity then falls back to Media3). */
    @Nullable
    public String getInitError() {
        return initError;
    }

    // =====================================================================================
    // SimpleBasePlayer: state
    // =====================================================================================

    @NonNull
    @Override
    protected State getState() {
        State.Builder b = new State.Builder();
        final MpvPlayer e = engine;

        if (e == null || currentItem == null || destroyed) {
            return b.setAvailableCommands(new Player.Commands.Builder()
                            .addAll(COMMAND_SET_MEDIA_ITEM, COMMAND_PREPARE, COMMAND_RELEASE,
                                    COMMAND_SET_VIDEO_SURFACE, COMMAND_GET_CURRENT_MEDIA_ITEM,
                                    COMMAND_GET_TIMELINE, COMMAND_GET_TRACKS)
                            .build())
                    .setPlaybackState(STATE_IDLE)
                    .setPlayWhenReady(false, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    .build();
        }

        PlaybackState s = e.getState();
        boolean busy = s == PlaybackState.LOADING || s == PlaybackState.BUFFERING;
        boolean playWhenReady = busy || s == PlaybackState.PLAYING;
        boolean vod = isVod(e);

        if (tracksDirty) rebuildTracks(e);

        MediaItemData.Builder item = new MediaItemData.Builder(String.valueOf(currentItem.mediaId))
                .setMediaItem(currentItem)
                .setTracks(cachedTracks)
                .setIsSeekable(vod && e.isSeekable());
        if (vod) {
            item.setDurationUs((long) (e.getDurationSeconds() * 1_000_000d));
        } else {   // live (or unknown, non-seekable): "LIVE" indicator, no finite duration
            item.setIsDynamic(true).setLiveConfiguration(new MediaItem.LiveConfiguration.Builder().build());
        }

        boolean canSeek = vod && e.isSeekable();
        Player.Commands commands = new Player.Commands.Builder()
                .addAll(COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_SET_MEDIA_ITEM, COMMAND_RELEASE,
                        COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_METADATA,
                        COMMAND_GET_TRACKS, COMMAND_SET_SPEED_AND_PITCH, COMMAND_SET_VIDEO_SURFACE,
                        COMMAND_SET_TRACK_SELECTION_PARAMETERS, COMMAND_SEEK_TO_DEFAULT_POSITION,
                        COMMAND_SEEK_TO_MEDIA_ITEM)
                .addIf(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, canSeek)
                .addIf(COMMAND_SEEK_BACK, canSeek)
                .addIf(COMMAND_SEEK_FORWARD, canSeek)
                .build();

        int playbackState;
        switch (s) {
            case LOADING:
            case BUFFERING: playbackState = STATE_BUFFERING; break;
            case PLAYING:
            case PAUSED:    playbackState = STATE_READY; break;
            case ENDED:     playbackState = STATE_ENDED; break;
            default:        playbackState = STATE_IDLE; break;   // IDLE, ERROR, RELEASED
        }

        b.setAvailableCommands(commands)
                .setPlayWhenReady(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackState(playbackState)
                .setIsLoading(busy)
                .setPlaylist(ImmutableList.of(item.build()))
                .setCurrentMediaItemIndex(0)
                .setContentPositionMs(this::positionMs)
                .setContentBufferedPositionMs(() -> positionMs() + bufferedAheadMs())
                .setTotalBufferedDurationMs(this::bufferedAheadMs)
                .setPlaybackParameters(new PlaybackParameters((float) e.getSpeed()))
                .setTrackSelectionParameters(cachedParams)
                .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
                .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
                .setNewlyRenderedFirstFrame(firstFramePending);
        VideoInfo vi = e.getVideoInfo();
        if (vi != null && vi.getSourceWidth() > 0 && vi.getSourceHeight() > 0) {
            b.setVideoSize(new VideoSize(vi.getSourceWidth(), vi.getSourceHeight()));
        }
        if (playbackState == STATE_IDLE && error != null) b.setPlayerError(error);
        return b.build();
    }

    /** Same rule as the old mpv UI: finite duration AND (seekable OR positively classified VOD), never LIVE. */
    private boolean isVod(MpvPlayer e) {
        double d = e.getDurationSeconds();
        if (Double.isNaN(d) || Double.isInfinite(d) || d <= 0) return false;
        LiveStatus ls = e.getLiveStatus();
        return ls != LiveStatus.LIVE && (e.isSeekable() || ls == LiveStatus.VOD);
    }

    private long positionMs() {
        MpvPlayer e = engine;
        if (e == null) return 0;
        double p = e.getPositionSeconds();
        return Double.isNaN(p) || p < 0 ? 0 : (long) (p * 1000d);
    }

    private long bufferedAheadMs() {
        MpvPlayer e = engine;
        if (e == null) return 0;
        BufferingInfo bi = e.getBufferingInfo();
        double ahead = bi == null ? Double.NaN : bi.getBufferedAheadSeconds();
        return Double.isNaN(ahead) || ahead < 0 ? 0 : (long) (ahead * 1000d);
    }

    private void notifyChanged() {
        if (destroyed) return;
        invalidateState();
        firstFramePending = false;
    }

    // =====================================================================================
    // SimpleBasePlayer: commands
    // =====================================================================================

    @NonNull
    @Override
    protected ListenableFuture<?> handleSetMediaItems(
            @NonNull List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        MpvPlayer e = engine;
        if (e == null || mediaItems.isEmpty()) return Futures.immediateVoidFuture();
        MediaItem item = mediaItems.get(0);
        if (item.localConfiguration == null) return Futures.immediateVoidFuture();

        currentItem = item;
        error = null;
        loaded = false;
        firstFrameSeen = false;
        tracksDirty = true;
        cachedTracks = Tracks.EMPTY;

        if (startPositionMs > 0) {
            pendingSeekPositionMs = startPositionMs;
        }

        LoadOptions.Builder lo = LoadOptions.builder();
        if (nextLiveHint != null) lo.liveHint(nextLiveHint ? LiveStatus.LIVE : LiveStatus.VOD);
        nextLiveHint = null;
        e.load(toMpvPath(item.localConfiguration.uri), lo.build());
        return Futures.immediateVoidFuture();
    }


    @NonNull
    @Override
    protected ListenableFuture<?> handlePrepare() {
        if (engine != null && error != null) engine.reopen();     // Util.handlePlayPauseButtonAction -> prepare() after an error
        return Futures.immediateVoidFuture();
    }

    @NonNull
    @Override
    protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
        MpvPlayer e = engine;
        if (e != null) {
            if (playWhenReady) e.play();          // PAUSED, ENDED (restarts), ERROR/IDLE (reopens)
            else e.pause();
        }
        return Futures.immediateVoidFuture();
    }

    @NonNull
    @Override
    protected ListenableFuture<?> handleSeek(int mediaItemIndex, long positionMs, int seekCommand) {
        MpvPlayer e = engine;
        if (e == null) return Futures.immediateVoidFuture();
        if (positionMs == C.TIME_UNSET) {              // "default position": live edge for live, start for VOD
            if (e.isLive()) e.jumpToLive();
            else e.seekTo(0);
        } else {
            e.seekTo(positionMs / 1000d);              // the engine takes SECONDS
        }
        return Futures.immediateVoidFuture();
    }

    @NonNull
    @Override
    protected ListenableFuture<?> handleSetPlaybackParameters(@NonNull PlaybackParameters p) {
        if (engine != null) engine.setSpeed(p.speed);  // real value comes back through onSpeedChanged()
        return Futures.immediateVoidFuture();
    }

    @NonNull
    @Override
    protected ListenableFuture<?> handleSetTrackSelectionParameters(@NonNull TrackSelectionParameters p) {
        MpvPlayer e = engine;
        if (e == null) return Futures.immediateVoidFuture();

        boolean textDisabled = p.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
        if (textDisabled) {
            if (userSubtitleId != null || anySubtitleSelected(e)) {
                userSubtitleId = null;
                e.disableSubtitles();
            }
        }
        for (TrackSelectionOverride o : p.overrides.values()) {
            String gid = o.mediaTrackGroup.id;
            try {
                if (gid.startsWith(PREFIX_AUDIO)) {
                    int id = Integer.parseInt(gid.substring(PREFIX_AUDIO.length()));
                    if (!isAudioSelected(e, id)) e.selectAudioTrack(id);
                } else if (gid.startsWith(PREFIX_SUB) && !textDisabled) {
                    int id = Integer.parseInt(gid.substring(PREFIX_SUB.length()));
                    if (!isSubtitleSelected(e, id)) {
                        userSubtitleId = id;
                        e.selectSubtitleTrack(id);
                    }
                }
            } catch (NumberFormatException ignored) { /* not one of our groups */ }
        }
        tracksDirty = true;
        return Futures.immediateVoidFuture();
    }

    @NonNull
    @Override
    protected ListenableFuture<?> handleSetVideoOutput(@NonNull Object videoOutput) {
        if (videoOutput instanceof SurfaceView) attachSurface(((SurfaceView) videoOutput).getHolder());
        else if (videoOutput instanceof SurfaceHolder) attachSurface((SurfaceHolder) videoOutput);
        return Futures.immediateVoidFuture();
    }

    @NonNull
    @Override
    protected ListenableFuture<?> handleClearVideoOutput(@Nullable Object videoOutput) {
        detachSurface();
        return Futures.immediateVoidFuture();
    }

    @NonNull
    @Override
    protected ListenableFuture<?> handleRelease() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        detachSurface();
        if (engine != null) {
            engine.release();
            engine = null;
        }
        return Futures.immediateVoidFuture();
    }

    /** mpv/ffmpeg cannot open content:// itself: hand it the file descriptor ("fd://N", mpv closes it). */
    private String toMpvPath(Uri uri) {
        String scheme = uri.getScheme();
        if ("content".equals(scheme)) {
            try {
                ParcelFileDescriptor pfd = appContext.getContentResolver().openFileDescriptor(uri, "r");
                if (pfd != null) return "fd://" + pfd.detachFd();
            } catch (Exception ignored) { /* fall through to the plain uri */ }
        } else if ("file".equals(scheme) && uri.getPath() != null) {
            return uri.getPath();
        }
        return uri.toString();
    }

    // =====================================================================================
    // Tracks
    // =====================================================================================

    private void rebuildTracks(MpvPlayer e) {
        tracksDirty = false;
        if (!loaded) {
            cachedTracks = Tracks.EMPTY;
            cachedParams = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
            return;
        }
        List<Tracks.Group> groups = new ArrayList<>();
        TrackSelectionParameters.Builder pb = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT.buildUpon();

        // One selected video group: PlayerView needs it to hide its shutter / artwork logic correctly.
        TrackGroup video = new TrackGroup(GROUP_VIDEO,
                new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_UNKNOWN).build());
        groups.add(new Tracks.Group(video, false, new int[]{C.FORMAT_HANDLED}, new boolean[]{true}));

        for (AudioTrackInfo t : e.getAudioTracks()) {
            Format.Builder f = new Format.Builder()
                    .setId(String.valueOf(t.getId()))
                    .setSampleMimeType(MimeTypes.AUDIO_UNKNOWN)
                    .setLabel(trackName(t.getTitle(), t.getLanguage(), t.getId()));
            if (t.getChannels() > 0) f.setChannelCount(t.getChannels());
            TrackGroup g = new TrackGroup(PREFIX_AUDIO + t.getId(), f.build());
            groups.add(new Tracks.Group(g, false, new int[]{C.FORMAT_HANDLED}, new boolean[]{t.isSelected()}));
            if (t.isSelected()) pb.addOverride(new TrackSelectionOverride(g, 0));
        }

        boolean subSelected = false;
        for (SubtitleTrackInfo t : e.getSubtitleTracks()) {
            Format f = new Format.Builder()
                    .setId(String.valueOf(t.getId()))
                    .setSampleMimeType(MimeTypes.TEXT_UNKNOWN)
                    .setLabel(trackName(t.getTitle(), t.getLanguage(), t.getId()))
                    .build();
            TrackGroup g = new TrackGroup(PREFIX_SUB + t.getId(), f);
            groups.add(new Tracks.Group(g, false, new int[]{C.FORMAT_HANDLED}, new boolean[]{t.isSelected()}));
            if (t.isSelected()) {
                subSelected = true;
                pb.addOverride(new TrackSelectionOverride(g, 0));
            }
        }
        pb.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subSelected);

        cachedTracks = new Tracks(groups);
        cachedParams = pb.build();
    }

    private static boolean isAudioSelected(MpvPlayer e, int id) {
        for (AudioTrackInfo t : e.getAudioTracks()) if (t.getId() == id) return t.isSelected();
        return false;
    }

    private static boolean isSubtitleSelected(MpvPlayer e, int id) {
        for (SubtitleTrackInfo t : e.getSubtitleTracks()) if (t.getId() == id) return t.isSelected();
        return false;
    }

    private static boolean anySubtitleSelected(MpvPlayer e) {
        for (SubtitleTrackInfo t : e.getSubtitleTracks()) if (t.isSelected()) return true;
        return false;
    }

    private String trackName(String title, String lang, int id) {
        if (notEmpty(title) && notEmpty(lang)) return title + " (" + lang + ")";
        if (notEmpty(title)) return title;
        if (notEmpty(lang)) return lang;
        return "Track " + id;
    }

    // =====================================================================================
    // Surface (mpv renders straight into PlayerView's SurfaceView)
    // =====================================================================================

    private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override public void surfaceCreated(@NonNull SurfaceHolder holder) {
            if (engineSurfaceCb != null) engineSurfaceCb.surfaceCreated(holder);
            onSurfaceReady();
        }
        @Override public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int w, int h) {
            if (engineSurfaceCb != null) engineSurfaceCb.surfaceChanged(holder, format, w, h);
        }
        @Override public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            if (engineSurfaceCb != null) engineSurfaceCb.surfaceDestroyed(holder);
        }
    };

    private void attachSurface(SurfaceHolder holder) {
        detachSurface();
        if (engine == null) return;
        attachedHolder = holder;
        engineSurfaceCb = engine.getSurfaceCallback();        // keep ONE reference: add and remove the same object
        holder.addCallback(surfaceCallback);
        if (holder.getSurface() != null && holder.getSurface().isValid()) {   // surface may already exist
            Rect f = holder.getSurfaceFrame();
            if(engineSurfaceCb != null) {
                engineSurfaceCb.surfaceCreated(holder);
                engineSurfaceCb.surfaceChanged(holder, PixelFormat.RGBA_8888, f.width(), f.height());
            }
            onSurfaceReady();
        }
    }

    private void detachSurface() {
        SurfaceHolder h = attachedHolder;
        if (h == null) return;
        h.removeCallback(surfaceCallback);
        if (engineSurfaceCb != null && h.getSurface() != null && h.getSurface().isValid()) {
            engineSurfaceCb.surfaceDestroyed(h);
        }
        attachedHolder = null;
        engineSurfaceCb = null;
    }

    private void onSurfaceReady() {
        scheduleSubtitleReapply();
        MpvPlayer e = engine;
        if (e == null || !resumeAfterSurface) return;
        resumeAfterSurface = false;
        if (e.isLive()) e.jumpToLive();      // do not continue a stale live buffer
        else e.play();
    }

    // =====================================================================================
    // Host (Activity) lifecycle
    // =====================================================================================

    public void onHostStart() {
        if (engine == null || !resumeAfterSurface) return;
        SurfaceHolder h = attachedHolder;
        if (h != null && h.getSurface() != null && h.getSurface().isValid()) onSurfaceReady();   // else surfaceCreated() does it
    }

    public void onHostStop() {
        MpvPlayer e = engine;
        if (e == null) return;
        PlaybackState s = e.getState();
        if (s == PlaybackState.PLAYING || s == PlaybackState.BUFFERING || s == PlaybackState.LOADING) {
            resumeAfterSurface = true;
            e.pause();
        }
    }

    /** Call from Activity.onConfigurationChanged (video output was re-created). */
    public void onConfigurationChanged() {
        scheduleSubtitleReapply();
    }

    // =====================================================================================
    // mpv-only extras used by PlayerActivity
    // =====================================================================================

    /** Tell the engine whether the NEXT item is known live (true) / VOD (false) / unknown (null). */
    public void setNextLiveHint(@Nullable Boolean live) {
        nextLiveHint = live;
    }

    public boolean isLiveNow() {
        return engine != null && engine.isLive();
    }

    public void addExternalSubtitle(@NonNull Uri uri) {
        MpvPlayer e = engine;
        if (e == null) return;
        e.loadExternalSubtitle(uri.toString());
        handler.postDelayed(() -> {      // the engine selects the new track; remember it for re-apply after rotation
            if (destroyed || engine == null) return;
            for (SubtitleTrackInfo t : engine.getSubtitleTracks()) if (t.isSelected()) userSubtitleId = t.getId();
            tracksDirty = true;
            notifyChanged();
        }, 600);
    }

    public void setSubtitleSizeIndex(int index) {
        subtitleSizeIndex = clampIndex(index);
        if (engine != null) engine.setSubtitleSize(SUB_SIZES[subtitleSizeIndex]);
    }

    /** 0 FIT, 1 16:9, 2 4:3, 3 FILL, 4 STRETCH. */
    public void setAspectMode(int mode) {
        MpvPlayer e = engine;
        if (e == null) return;
        switch (mode) {
            case 1: e.setVideoScaleMode(VideoScaleMode.FIT); e.setAspectRatio("16:9"); break;
            case 2: e.setVideoScaleMode(VideoScaleMode.FIT); e.setAspectRatio("4:3"); break;
            case 3: e.setVideoScaleMode(VideoScaleMode.FILL); e.resetAspectRatio(); break;
            case 4: e.setVideoScaleMode(VideoScaleMode.STRETCH); e.resetAspectRatio(); break;
            default: e.setVideoScaleMode(VideoScaleMode.FIT); e.resetAspectRatio(); break;
        }
    }

    public void takeScreenshot(@NonNull File file) {
        if (engine != null) engine.takeScreenshot(file.getAbsolutePath(), false);
    }

    /** After the video output was re-created (rotation / fullscreen / back to app): restore subtitle state. */
    private void scheduleSubtitleReapply() {
        handler.postDelayed(() -> {
            MpvPlayer e = engine;
            if (destroyed || e == null) return;
            Integer id = null;
            for (SubtitleTrackInfo t : e.getSubtitleTracks()) if (t.isSelected()) id = t.getId();
            if (id == null) id = userSubtitleId;
            if (id != null) e.selectSubtitleTrack(id);          // also sets sub-visibility=yes
            e.setSubtitleSize(SUB_SIZES[subtitleSizeIndex]);
        }, 400);
    }

    @NonNull
    public String buildInfoText() {
        MpvPlayer p = engine;
        if (p == null) return "mpv not running";
        StringBuilder s = new StringBuilder();
        s.append("mpv: ").append(p.getMpvVersion()).append('\n').append("FFmpeg: ").append(p.getFfmpegVersion()).append("\n\n");
        s.append("State: ").append(p.getState()).append('\n');
        s.append("Live: ").append(p.getLiveStatus()).append(" (").append(p.getLiveStatusReason()).append(")\n");
        s.append("Duration: ").append(p.getDurationSeconds()).append("   Speed: ").append(p.getSpeed()).append('\n');
        s.append("Seekable: ").append(p.isSeekable()).append("   Network: ").append(p.isNetworkStream()).append('\n');
        MediaInfo mi = p.getMediaInfo();
        if (mi != null) s.append("Format: ").append(mi.getFileFormat()).append("   Video tracks: ").append(mi.getVideoTracks().size())
                .append("   Audio: ").append(mi.getAudioTracks().size()).append("   Subs: ").append(mi.getSubtitleTracks().size()).append('\n');
        VideoInfo vi = p.getVideoInfo();
        if (vi != null) s.append("Video: ").append(vi.getSourceWidth()).append('×').append(vi.getSourceHeight())
                .append("   aspect ").append(String.format(Locale.ROOT, "%.3f", vi.getSourceAspect())).append('\n');
        PlaybackStats st = p.getStats();
        if (st != null) s.append("Decoder: ").append(st.hardwareDecoder).append("   fps ").append(String.format(Locale.ROOT, "%.1f", st.outputFps))
                .append("   dropped ").append(st.voDroppedFrames).append('/').append(st.decoderDroppedFrames).append('\n');
        s.append("Buffer: ").append(p.getBufferingInfo()).append('\n');
        if (!p.getOptionReport().isEmpty()) s.append("Rejected options: ").append(p.getOptionReport()).append('\n');
        return s.toString();
    }

    // =====================================================================================
    // Engine events -> media3 state
    // =====================================================================================

    private final class EngineListener implements MpvPlayerListener {
        @Override public void onStateChanged(PlaybackState newState, PlaybackState oldState) {
            Log.d(TAG, oldState + " -> " + newState);
            if (newState == PlaybackState.ERROR) {
                MpvError me = engine == null ? null : engine.getLastError();
                String msg = me != null && me.getMessage() != null ? me.getMessage() : "Playback failed";
                error = new PlaybackException(msg, null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
            } else if (newState != PlaybackState.IDLE) {
                error = null;
            }
            if (newState == PlaybackState.PLAYING && !firstFrameSeen) {   // opens PlayerView's shutter
                firstFrameSeen = true;
                firstFramePending = true;
            }
            tracksDirty = true;
            notifyChanged();
        }
        @Override public void onMediaLoaded(MediaInfo info) {
            Log.i(TAG, "Loaded: format=" + info.getFileFormat() + " duration=" + info.getDurationSeconds()
                    + " seekable=" + info.isSeekable() + " live=" + info.getLiveStatus() + " (" + info.getLiveStatusReason() + ")");
            loaded = true;
            tracksDirty = true;

            if (startPaused) {
                assert engine != null;
                engine.pause();
            }
            applyPendingSeekIfReady();
            notifyChanged();
        }
        @Override public void onLiveStatusChanged(LiveStatus status, String reason) { notifyChanged(); }
        @Override public void onDurationChanged(double durationSeconds) { notifyChanged(); }
        @Override public void onSpeedChanged(double speed) { notifyChanged(); }
        @Override public void onTracksChanged(List<AudioTrackInfo> audio, List<SubtitleTrackInfo> subs) {
            for (SubtitleTrackInfo sub : subs) {
                Log.i(TAG, "Subtitle track:"
                        + " id=" + sub.getId()
                        + ", title=" + sub.getTitle()
                        + ", language=" + sub.getLanguage()
                        + ", selected=" + sub.isSelected());
            }

            tracksDirty = true;
            notifyChanged();
        }
        @Override public void onVideoInfoChanged(VideoInfo info) { notifyChanged(); }
        @Override public void onPlaylistPositionChanged(int position, int count) { /* playlists are handled by the app */ }
        @Override public void onError(MpvError e) {
            Log.e(TAG, "Playback error: " + e.getMessage() + ", willRetry=" + e.willRetry() + ", log=" + e.getLogLines());
        }
        @Override public void onRetryScheduled(int attempt, int max, long delayMs) {
            Log.w(TAG, "Retry " + attempt + "/" + max + " in " + delayMs + " ms");
        }
        @Override public void onOptionRejected(String name, String value, int code, String reason) {
            Log.w(TAG, "Rejected option " + name + "=" + value + " (" + code + "): " + reason);
        }
    }

    // =====================================================================================
    // History seeks
    // =====================================================================================
    public void setStartPaused(boolean paused) {
        startPaused = paused;
    }

    public void seekWhenLoaded(long positionMs) {if (positionMs <= 0) return; pendingSeekPositionMs = positionMs; applyPendingSeekIfReady();
    }

    private void applyPendingSeekIfReady() {
        MpvPlayer e = engine;
        if (e == null || !loaded || pendingSeekPositionMs == null) {return;}

        long positionMs = pendingSeekPositionMs;
        pendingSeekPositionMs = null;
        if (!e.isSeekable()) {return;}

        e.pause();
        e.seekTo(positionMs / 1000d);
    }

    public boolean isMediaLoaded() {
        return loaded;
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private static int clampIndex(int i) {
        return Math.max(0, Math.min(SUB_SIZES.length - 1, i));
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }


}