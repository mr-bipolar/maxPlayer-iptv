package com.aaronmaxlab.maxplayer.utils;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * ONE place that knows what the app can open: everything FFmpeg/mpv can read.
 * Media3 plays a smaller subset, so {@link #needsMpv} tells PlayerActivity when to use mpv
 * even if the user picked Media3 in the settings.
 */
public final class MediaTypes {

    private MediaTypes() {}

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    // ---- everything mpv/FFmpeg can read
    public static final Set<String> PLAYLIST_EXT = setOf("cue", "m3u", "m3u8", "pls", "vlc");

    public static final Set<String> AUDIO_EXT = setOf(
            "3ga", "3ga2", "a52", "aac", "ac3", "ac4", "adt", "adts", "aif", "aifc", "aiff", "alac",
            "amr", "ape", "au", "awb", "dsf", "dts", "dts-hd", "dtshd", "eac3", "f4a", "flac",
            "lc3", "lpcm", "m1a", "m2a", "m4a", "mka", "mlp", "mp+", "mp1", "mp2", "mp3",
            "mpa", "mpc", "mpga", "mpp", "oga", "ogg", "opus", "pcm", "qoa", "ra", "ram", "rax",
            "shn", "snd", "spx", "tak", "thd", "thd+ac3", "true-hd", "truehd", "tta", "wav", "weba",
            "wma", "wv", "wvp");

    public static final Set<String> VIDEO_EXT = setOf(
            "264", "265", "266", "3g2", "3gp", "3gp2", "3gpp", "3gpp2", "asf",
            "asx", "av1", "avc", "avf", "avi", "bdm", "bdmv", "clpi", "cpi", "divx", "dv", "evo",
            "evob", "f4v", "flc", "fli", "flic", "flv", "gxf", "h264", "h265", "h266", "hdmov",
            "hdv", "hevc", "lrv", "m1u", "m1v", "m2t", "m2ts", "m2v", "m4u", "m4v", "mk3d", "mkv",
            "mj2", "mov", "mp2v", "mp4", "mp4v", "mpe", "mpeg", "mpeg2", "mpeg4", "mpg",
            "mpg4", "mpl", "mpv", "mpv2", "mts", "mtv", "mxf", "mxu", "nsv", "nut", "ogm",
            "ogv", "ogx", "qt", "qtvr", "rm", "rmj", "rmm", "rms", "rmvb", "rmx", "rv", "rvx",
            "sdp", "tod", "trp", "ts", "tsa", "tsv", "tts", "vc1", "vfw", "vob", "vro", "vvc",
            "webm", "wm", "wmv", "wmx", "x264", "x265", "xvid", "y4m", "yuv");

    public static final Set<String> PROTOCOLS = setOf(
            "file", "content", "http", "https", "data", "ftp",
            "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh", "tcp", "udp", "lavf");

    // ---- what Media3 plays out of the box (conservative;
    // add to it if your Media3 build has more modules)
    private static final Set<String> MEDIA3_EXT = setOf(
            "mp4", "m4v", "m4a", "mov", "3gp", "3g2", "mkv", "mka", "webm", "ts", "mts", "m2ts",
            "mp3", "aac", "ac3", "eac3", "ac4", "flac", "ogg", "oga", "opus", "wav", "amr", "flv",
            "mpg", "mpeg", "ps");

    private static final Set<String> MEDIA3_PROTOCOLS = setOf("http", "https", "file", "content", "data", "udp");


    // File names
    /** "Movies/a.MKV?x=1" -> "mkv"; "" when there is none. Works for file names and URLs. */
    public static String extension(@Nullable String nameOrUrl) {
        if (nameOrUrl == null) return "";
        String s = nameOrUrl;
        int cut = s.indexOf('?');
        if (cut >= 0) s = s.substring(0, cut);
        cut = s.indexOf('#');
        if (cut >= 0) s = s.substring(0, cut);
        int slash = Math.max(s.lastIndexOf('/'), Math.max(s.lastIndexOf('\\'), s.lastIndexOf(':')));
        s = s.substring(slash + 1);
        int dot = s.lastIndexOf('.');
        return dot < 0 || dot == s.length() - 1 ? "" : s.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** .m3u / .m3u8: parsed by the app into a channel list. */
    public static boolean isM3u(@Nullable String nameOrUrl) {
        String e = extension(nameOrUrl);
        return e.equals("m3u") || e.equals("m3u8");
    }

    /** Anything played as ONE stream: audio, video and the playlist formats mpv opens itself (pls, cue, vlc). */
    public static boolean isMedia(@Nullable String nameOrUrl) {
        String e = extension(nameOrUrl);
        if (e.isEmpty()) return false;
        if (e.equals("m3u") || e.equals("m3u8")) return false;
        return AUDIO_EXT.contains(e) || VIDEO_EXT.contains(e) || PLAYLIST_EXT.contains(e);
    }


    // URLs
    @Nullable
    public static String scheme(@Nullable String url) {
        if (url == null) return null;
        int i = url.indexOf(':');
        if (i <= 0) return null;
        String s = url.substring(0, i).toLowerCase(Locale.ROOT);
        return s.matches("[a-z][a-z0-9+.-]*") ? s : null;
    }

    /** http(s) with a host: the only kind of URL a PLAYLIST can be downloaded from. */
    public static boolean isHttpUrl(@Nullable String url) {
        if (url == null) return false;
        String u = url.trim();
        String s = scheme(u);
        if (!"http".equals(s) && !"https".equals(s)) return false;
        String host = Uri.parse(u).getHost();
        return host != null && !host.isEmpty();
    }

    /** Any protocol mpv can open (http, https, rtmp, rtsp, udp, ftp, ...). */
    public static boolean isSupportedUrl(@Nullable String url) {
        if (url == null) return false;
        String u = url.trim();
        String s = scheme(u);
        if (s == null || !PROTOCOLS.contains(s)) return false;
        if (s.equals("http") || s.equals("https")) return isHttpUrl(u);
        return u.length() > s.length() + 1;          // something after "scheme:"
    }


    // Engine routing
    /**
     * True when Media3 cannot play this source (protocol like rtmp/ftp/rtsp, or a format like avi, wmv, ape...)
     * so PlayerActivity must use mpv. {@code name} is optional: content:// Uris have no extension,
     * the display name does.
     */
    public static boolean needsMpv(@Nullable String url, @Nullable String name) {
        String scheme = scheme(url);
        if (scheme != null && !MEDIA3_PROTOCOLS.contains(scheme)) return true;
        return extensionNeedsMpv(extension(name)) || extensionNeedsMpv(extension(url));
    }

    private static boolean extensionNeedsMpv(String ext) {
        if (ext.isEmpty() || MEDIA3_EXT.contains(ext) || ext.equals("m3u") || ext.equals("m3u8")) return false;
        return AUDIO_EXT.contains(ext) || VIDEO_EXT.contains(ext) || PLAYLIST_EXT.contains(ext);
    }
}