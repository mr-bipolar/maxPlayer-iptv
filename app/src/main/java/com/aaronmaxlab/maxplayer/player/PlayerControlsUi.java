package com.aaronmaxlab.maxplayer.player;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.ActivityPlayerBinding;
import com.aaronmaxlab.maxplayer.utils.DeviceUtils;

import java.util.Locale;

/**
 * The glass controller of {@code activity_player.xml} (the design from the old mpv screen), driven by
 * ANY media3 {@link Player}: the Media3 MediaController or the mpv adapter. It only renders state and
 * handles the seek bar / tap gestures / auto-hide; button actions stay in {@link PlayerActivity}.
 */
final class PlayerControlsUi {

    private static final long AUTO_HIDE_MS = 5000;
    private static final long TICK_MS = 500;
    private static final long DOUBLE_TAP_SEEK_MS = 10_000;
    private static final float DISABLED_ALPHA = 0.35f;

    private final Activity activity;
    private final ActivityPlayerBinding b;
    private final boolean tv;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable private Player player;
    @Nullable private Player.Listener listener;

    private boolean controlsVisible = true, userSeeking, pip, autoHideBlocked;
    private int lastFocusedControlId = View.NO_ID;
    private CharSequence subtitleBase = "";

    PlayerControlsUi(@NonNull Activity activity, @NonNull ActivityPlayerBinding binding) {
        this.activity = activity;
        this.b = binding;
        this.tv = DeviceUtils.isTv(activity);
        setupSeekBar();
        setupGestures();
        setControlsVisible(true);
    }

    // Attach / detach
    void attach(@NonNull Player p) {
        detach();
        player = p;
        listener = new Player.Listener() {
            @Override public void onEvents(@NonNull Player pl, @NonNull Player.Events events) {
                render();
                if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYER_ERROR)) {
                    if (pl.isPlaying()) restartAutoHide();
                    else if (!tv && !controlsVisible) setControlsVisible(true);   // preserve mobile behavior
                }
            }
        };
        p.addListener(listener);
        render();
        setControlsVisible(true);
    }

    void detach() {
        if (player != null && listener != null) player.removeListener(listener);
        player = null;
        listener = null;
    }

    void release() {
        detach();
        handler.removeCallbacksAndMessages(null);
        b.playerControllers.animate().cancel();
    }

    // Setters used by the Activity

    void setHeader(CharSequence title, CharSequence subtitle) {
        b.tvTitle.setText(title);
        subtitleBase = subtitle == null ? "" : subtitle;
        updateSubtitle();
    }

    void setFavorite(boolean fav) {
        b.btnFavorite.setImageResource(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        if (tv) b.btnFavorite.setSelected(fav);
        androidx.core.widget.ImageViewCompat.setImageTintList(b.btnFavorite,
                android.content.res.ColorStateList.valueOf(ContextCompat.getColor(activity,
                        fav ? (tv ? R.color.tv_player_accent : R.color.accent) : R.color.white)));
    }

    void setNav(boolean hasList, boolean hasPrev, boolean hasNext) {
        b.btnPrev.setAlpha(hasPrev ? 1f : DISABLED_ALPHA);
        b.btnNext.setAlpha(hasNext ? 1f : DISABLED_ALPHA);
        if (!tv) b.btnChannels.setAlpha(hasList ? 1f : DISABLED_ALPHA);
    }

    void setAspectLabel(@StringRes int res) {
        b.tvAspectLabel.setText(res);
    }

    void setFullscreenIcon(boolean landscape) {
        b.btnFullscreenIcon.setImageResource(landscape ? R.drawable.ic_close_fullscreen : R.drawable.ic_expand);
        b.addPlayListPlusBtn.setVisibility(landscape ? View.GONE : View.VISIBLE );
        b.btnAddPlaylistAction.setVisibility(landscape ? View.VISIBLE : View.GONE);
    }

    /** While a dialog / menu is open the controls must not fade away. */
    void setAutoHideBlocked(boolean blocked) {
        autoHideBlocked = blocked;
        if (blocked) handler.removeCallbacks(hideRunnable);
        else restartAutoHide();
    }

    /** Picture-in-picture: the small window shows only the video; the system draws its own buttons. */
    void setPipMode(boolean inPip) {
        pip = inPip;
        handler.removeCallbacks(hideRunnable);
        handler.removeCallbacks(ticker);
        b.playerControllers.animate().cancel();
        if (inPip) {
            lastFocusedControlId = View.NO_ID;
            b.playerControllers.setVisibility(View.GONE);
            controlsVisible = false;
        } else {
            b.playerControllers.setAlpha(1f);
            setControlsVisible(true);
        }
    }

    // Rendering
    private void render() {
        Player p = player;
        if (p == null) return;
        int st = p.getPlaybackState();
        PlaybackException err = p.getPlayerError();
        boolean busy = st == Player.STATE_BUFFERING && err == null;
        boolean playingIntent = err == null && p.getPlayWhenReady()
                && (st == Player.STATE_BUFFERING || st == Player.STATE_READY);

        b.bufferingIndicator.setVisibility(busy ? View.VISIBLE : View.GONE);
        b.playPauseBtn.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);   // the spinner takes the icon's place
        b.playPauseBtn.setImageResource(playingIntent ? R.drawable.ic_pause : R.drawable.ic_play);
        b.playPause.setContentDescription(activity.getString(playingIntent ? R.string.cd_pause : R.string.cd_play));
        b.getRoot().setKeepScreenOn(playingIntent);

        if (err != null) {
            b.tvErrorMessage.setText(err.getMessage() != null ? err.getMessage() : activity.getString(R.string.error_desc));
            b.errorPanel.setVisibility(View.VISIBLE);
        } else {
            b.errorPanel.setVisibility(View.GONE);
        }

        float speed = p.getPlaybackParameters().speed;
        b.tvSpeedLabel.setText(Math.abs(speed - 1f) < 0.001f
                ? activity.getString(R.string.player_ctl_speed) : formatSpeed(speed));

        updateSubtitle();
        updateLiveUi();
    }

    private void updateSubtitle() {
        StringBuilder sb = new StringBuilder(subtitleBase);
        Player p = player;
        if (p != null) {
            VideoSize vs = p.getVideoSize();
            if (vs.width > 0 && vs.height > 0) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(vs.width).append('×').append(vs.height);
            }
        }
        b.tvSubtitle.setText(sb);
    }

    private boolean isLive(Player p) {
        return p.isCurrentMediaItemLive()
                || (p.getPlaybackState() == Player.STATE_READY && p.getDuration() == C.TIME_UNSET);
    }

    private boolean isVod(Player p) {
        long d = p.getDuration();
        return !p.isCurrentMediaItemLive() && d != C.TIME_UNSET && d > 0;
    }

    /** LIVE badge + live row for live/unknown streams, timeline for VOD. */
    private void updateLiveUi() {
        Player p = player;
        if (p == null) return;
        boolean live = isLive(p);
        boolean vod = isVod(p);
        b.tvLiveBadge.setVisibility(live ? View.VISIBLE : View.GONE);
        b.vodRow.setVisibility(vod ? View.VISIBLE : View.GONE);
        b.liveRow.setVisibility(vod ? View.GONE : View.VISIBLE);
        b.liveDot.setVisibility(live ? View.VISIBLE : View.GONE);
        b.tvLiveLabel.setText(live ? R.string.badge_live : R.string.mp_stream_label);
        b.tvLiveLabel.setTextColor(ContextCompat.getColor(activity, live ? R.color.white_alpha_75 : R.color.live));
        b.btnSpeed.setAlpha(live ? DISABLED_ALPHA : 1f);
        boolean canSeek = p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
        b.btnRewind.setAlpha(canSeek ? 1f : DISABLED_ALPHA);
        b.btnForward.setAlpha(canSeek ? 1f : DISABLED_ALPHA);
        b.videoProgress.setEnabled(canSeek);
        b.videoProgress.setAlpha(canSeek ? 1f : 0.5f);
        b.addPlaylistWindow.setPadding(0, 0, live ? 12 : 0, 0);
        updateProgress();
    }

    private void updateProgress() {
        Player p = player;
        if (p == null || !controlsVisible) return;
        if (isVod(p)) {
            long dur = p.getDuration();
            b.tvDuration.setText(formatTime(dur));
            if (!userSeeking) {
                long pos = p.getCurrentPosition();
                b.tvPosition.setText(formatTime(pos));
                b.videoProgress.setProgress((int) Math.max(0, Math.min(1000, pos * 1000L / dur)));
            }
        } else {
            long ahead = p.getTotalBufferedDuration();
            b.tvBufferInfo.setText(ahead > 0 ? activity.getString(R.string.mp_buffer_fmt, Math.round(ahead / 1000d)) : "");
        }
    }


    // Seek bar + gestures

    private void setupSeekBar() {
        b.videoProgress.setMax(1000);
        b.videoProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                Player p = player;
                if (fromUser && p != null && p.getDuration() > 0) b.tvPosition.setText(formatTime(p.getDuration() * progress / s.getMax()));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {
                userSeeking = true;
                handler.removeCallbacks(hideRunnable);
            }
            @Override public void onStopTrackingTouch(SeekBar s) {
                userSeeking = false;
                Player p = player;
                if (p != null && p.getDuration() > 0) {
                    if (p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                        p.seekTo(p.getDuration() * s.getProgress() / s.getMax());
                    } else {
                        Toast.makeText(activity, R.string.mp_not_seekable, Toast.LENGTH_SHORT).show();
                    }
                }
                restartAutoHide();
            }
        });
    }

    /** Restart the auto-hide timer after a button press. */
    void keepAlive() {
        restartAutoHide();
    }

    /** Remote input reveals hidden TV controls or restarts their inactivity timer. */
    void onUserActivity() {
        if (pip) return;
        if (controlsVisible) restartAutoHide();
        else setControlsVisible(true);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupGestures() {
        final GestureDetector detector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (!pip) setControlsVisible(!controlsVisible);
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                Player p = player;
                if (pip || p == null || !isVod(p) || !p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return false;
                boolean left = e.getX() < b.playerView.getWidth() / 2f;
                long target = p.getCurrentPosition() + (left ? -DOUBLE_TAP_SEEK_MS : DOUBLE_TAP_SEEK_MS);
                p.seekTo(Math.max(0, Math.min(p.getDuration(), target)));
                setControlsVisible(true);
                return true;
            }
        });
        b.playerView.setOnTouchListener((v, e) -> { detector.onTouchEvent(e); return true; });
    }


    // Visibility / auto-hide

    private final Runnable hideRunnable = () -> { if (shouldAutoHide()) setControlsVisible(false); };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!controlsVisible || pip) return;
            updateLiveUi();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private boolean shouldAutoHide() {
        return player != null && (tv || player.isPlaying())
                && !userSeeking && !autoHideBlocked && !pip;
    }

    private void restartAutoHide() {
        handler.removeCallbacks(hideRunnable);
        if (controlsVisible && shouldAutoHide()) handler.postDelayed(hideRunnable, AUTO_HIDE_MS);
    }

    private void setControlsVisible(boolean visible) {
        if (pip) return;
        handler.removeCallbacks(hideRunnable);
        handler.removeCallbacks(ticker);
        View c = b.playerControllers;
        c.animate().cancel();
        boolean wasVisible = controlsVisible;
        controlsVisible = visible;
        if (visible) {
            c.setVisibility(View.VISIBLE);
            c.animate().alpha(1f).setDuration(150).start();
            if (tv && !wasVisible && lastFocusedControlId != View.NO_ID) {
                View target = activity.findViewById(lastFocusedControlId);
                if (target == null || target.getVisibility() != View.VISIBLE) target = b.playPause;
                target.requestFocus();
                lastFocusedControlId = View.NO_ID;
            }
            updateProgress();
            handler.postDelayed(ticker, TICK_MS);
            restartAutoHide();
        } else {
            View focused = tv ? c.findFocus() : null;
            lastFocusedControlId = focused == null ? View.NO_ID : focused.getId();
            c.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                if (!controlsVisible && !pip) c.setVisibility(View.GONE);
            }).start();
        }
    }


    // Formatting

    static String formatSpeed(double s) {
        return (s == Math.rint(s) ? String.valueOf((int) s) : String.valueOf(s)) + "x";
    }

    private static String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long t = ms / 1000;
        long h = t / 3600, m = (t % 3600) / 60, sec = t % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }
}
