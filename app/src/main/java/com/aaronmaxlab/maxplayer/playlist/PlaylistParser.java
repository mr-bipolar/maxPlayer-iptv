package com.aaronmaxlab.maxplayer.playlist;

import android.content.ContentResolver;
import android.net.Uri;

import com.aaronmaxlab.maxplayer.playlist.models.PlaylistChannel;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistInfo;
import com.aaronmaxlab.maxplayer.utils.MediaTypes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaylistParser {

    private static final int MAX_PLAYLIST_BYTES = 5 * 1024 * 1024;
    private static final int MAX_CHANNELS = 10_000;

    private static final Pattern GROUP_TITLE = Pattern.compile(
            "group-title\\s*=\\s*\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE
    );

    private PlaylistParser() {}

    public enum LocalKind {
        PLAYLIST,      // .m3u / .m3u8 with channels (#EXTINF entries)
        HLS_STREAM,    // .m3u8 that is a single HLS stream (#EXT-X- tags)
        MEDIA_FILE,    // mp4, mkv, ts, mp3 ... one playable file
        UNSUPPORTED
    }

    /** Tells a local file apart: channel playlist, single stream or single media file. */
    public static LocalKind detectLocal(ContentResolver resolver, Uri uri, String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String mime = resolver.getType(uri);
        mime = mime == null ? "" : mime.toLowerCase(Locale.ROOT);

        boolean m3uMime = mime.contains("mpegurl");
        boolean m3uExt = lower.endsWith(".m3u") || lower.endsWith(".m3u8");

        // a plain media file (not a playlist)
        if (!m3uExt && !m3uMime
                && (MediaTypes.isMedia(lower) || mime.startsWith("video/") || mime.startsWith("audio/"))) {
            return LocalKind.MEDIA_FILE;
        }

        // look inside: playlist or HLS stream
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) return LocalKind.UNSUPPORTED;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {

                boolean first = true;
                String line;
                int guard = 0;
                while ((line = reader.readLine()) != null && guard++ < 5000) {
                    line = line.trim();
                    line = stripBom(line);
                    if (line.isEmpty()) continue;

                    if (first) {
                        first = false;
                        boolean header = line.regionMatches(true, 0, "#EXTM3U", 0, 7);
                        if (!m3uExt && !m3uMime && !header) return LocalKind.UNSUPPORTED;
                    }
                    if (line.startsWith("#EXT-X-")) return LocalKind.HLS_STREAM;   // HLS tags come before the first segment
                    if (line.startsWith("#EXTINF:")) return LocalKind.PLAYLIST;
                }
            }
        } catch (IOException e) {
            return LocalKind.UNSUPPORTED;
        }
        return LocalKind.UNSUPPORTED;   // m3u without #EXTINF entries
    }

    private static String stripBom(String line) {
        if (line == null) return null;

        return !line.isEmpty() && line.charAt(0) == '\uFEFF'
                ? line.substring(1).trim()
                : line;
    }


    public static PlaylistInfo parse(
            ContentResolver resolver,
            Uri uri,
            String fileName
    ) throws IOException {

        PlaylistInfo info = new PlaylistInfo();
        info.fileName = fileName;

        String lower = fileName.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".m3u8")) {
            info.format = "M3U8";
            info.isPlaylist = true;
        } else if (lower.endsWith(".m3u")) {
            info.format = "M3U";
            info.isPlaylist = true;
        } else if (lower.endsWith(".ts")) {
            info.format = "MPEG-TS";
            info.isPlaylist = false;
            return info;
        } else if (lower.endsWith(".mpd")) {
            info.format = "MPEG-DASH";
            info.isPlaylist = true;
            return info; // DASH parsing can be added separately.
        } else if (lower.endsWith(".strm")) {
            info.format = "STRM";
            info.isPlaylist = true;
            return info;
        } else if (lower.endsWith(".pls")) {
            info.format = "PLS";
            info.isPlaylist = true;
            return info;
        } else if (lower.endsWith(".xspf")) {
            info.format = "XSPF";
            info.isPlaylist = true;
            return info;
        } else if (lower.endsWith(".asx")) {
            info.format = "ASX";
            info.isPlaylist = true;
            return info;
        } else if (lower.endsWith(".wpl")) {
            info.format = "WPL";
            info.isPlaylist = true;
            return info;
        } else {
            throw new IOException("Unsupported file format");
        }

        Set<String> categories = new LinkedHashSet<>();

        boolean hasHlsTags = false;
        int extInfCount = 0;
        String pendingGroup = null;

        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Unable to open selected file");
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {

                String line;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    if (line.startsWith("#EXT-X-")) {
                        hasHlsTags = true;
                    }

                    if (line.regionMatches(
                            true, 0, "#EXTGRP:", 0, 8)) {
                        String group = line.substring(8).trim();
                        if (!group.isEmpty()) {
                            pendingGroup = group;
                        }
                    }

                    if (line.startsWith("#EXTINF:")) {
                        extInfCount++;

                        Matcher matcher = GROUP_TITLE.matcher(line);
                        if (matcher.find()) {
                            String group = matcher.group(1).trim();
                            if (!group.isEmpty()) {
                                categories.add(group);
                            }
                        } else if (pendingGroup != null) {
                            categories.add(pendingGroup);
                        }

                        pendingGroup = null;
                    }
                }
            }
        }

        info.isHlsManifest = hasHlsTags;

        if (hasHlsTags) {
            info.format = "HLS M3U8";
            info.channelCount = 0;
            info.categoryNames.clear();
        } else {
            info.channelCount = extInfCount;
            info.categoryNames.addAll(categories);
        }

        return info;
    }


    public static PlaylistInfo parseRemoteUrl(
            String urlString,
            String fileName
    ) throws IOException {

        java.net.HttpURLConnection connection = null;

        try {
            java.net.URL url = java.net.URI.create(urlString).toURL();

            String protocol = url.getProtocol();
            if (!protocol.equalsIgnoreCase("http")
                    && !protocol.equalsIgnoreCase("https")) {
                throw new IOException("Only HTTP and HTTPS URLs are supported.");
            }

            connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty(
                    "User-Agent", "Mozilla/5.0"
            );

            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                throw new IOException("Server returned HTTP " + status);
            }

            PlaylistInfo info = new PlaylistInfo();
            info.fileName = fileName;
            info.format = fileName.toLowerCase(java.util.Locale.ROOT)
                    .endsWith(".m3u8") ? "M3U8" : "M3U";
            info.isPlaylist = true;

            java.util.Set<String> categories =
                    new java.util.LinkedHashSet<>();

            boolean isHls = false;
            int entries = 0;

            try (java.io.BufferedReader reader =
                         new java.io.BufferedReader(
                                 new java.io.InputStreamReader(
                                         connection.getInputStream(),
                                         java.nio.charset.StandardCharsets.UTF_8))) {

                String line;
                String pendingName = null;
                String pendingCategory = "";
                String pendingLogo = "";
                long bytes = 0;

                Pattern logoPattern = Pattern.compile(
                        "tvg-logo\\s*=\\s*\"([^\"]*)\"",
                        Pattern.CASE_INSENSITIVE
                );

                while ((line = reader.readLine()) != null) {
                    bytes += line.length() + 1L;

                    if (bytes > MAX_PLAYLIST_BYTES) {throw new IOException("Playlist too large");}

                    line = line.trim();

                    if (line.isEmpty()) {continue;}
                    if (line.startsWith("#EXT-X-")) {isHls = true;}

                    if (line.startsWith("#EXTINF:")) {
                        entries++;

                        if (entries > MAX_CHANNELS) {
                            throw new IOException("Too many channels in playlist");
                        }

                        int comma = line.indexOf(',');
                        pendingName = comma >= 0
                                ? line.substring(comma + 1).trim()
                                : "";

                        pendingCategory = "";
                        pendingLogo = "";

                        Matcher matcher = GROUP_TITLE.matcher(line);
                        if (matcher.find()) {
                            pendingCategory = matcher.group(1).trim();

                            if (!pendingCategory.isEmpty()) {
                                categories.add(pendingCategory);
                            }
                        }

                        Matcher logoMatcher = logoPattern.matcher(line);
                        if (logoMatcher.find()) {
                            pendingLogo = logoMatcher.group(1).trim();
                        }

                        continue;
                    }

                    if (line.regionMatches(true, 0, "#EXTGRP:", 0, 8)) {
                        pendingCategory = line.substring(8).trim();

                        if (!pendingCategory.isEmpty()) {
                            categories.add(pendingCategory);
                        }

                        continue;
                    }

                    // Ignore comments and tags; the next non-comment line is the URL.
                    if (line.startsWith("#")) {
                        continue;
                    }

                    // Keep HLS manifest handling unchanged.
                    if (!isHls && pendingName != null) {
                        PlaylistChannel channel = new PlaylistChannel(
                                pendingName.isEmpty() ? line : pendingName,
                                line,
                                pendingCategory
                        );

                        channel.logoUrl = pendingLogo;
                        info.channels.add(channel);
                    }

                    pendingName = null;
                    pendingCategory = "";
                    pendingLogo = "";
                }

            }

            info.isHlsManifest = isHls;
            info.channelCount = isHls ? 0 : entries;

            if (!isHls) {
                info.categoryNames.addAll(categories);
            } else {
                info.format = "HLS M3U8";
            }

            return info;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }


    public static void parseM3uContent(
            BufferedReader reader,
            PlaylistInfo info
    ) throws IOException {

        Set<String> categories = new LinkedHashSet<>();

        Pattern groupTitle = Pattern.compile(
                "group-title\\s*=\\s*\"([^\"]*)\"",
                Pattern.CASE_INSENSITIVE
        );

        Pattern logoPattern = Pattern.compile(
                "tvg-logo\\s*=\\s*\"([^\"]*)\"",
                Pattern.CASE_INSENSITIVE
        );

        String line;
        String pendingName = null;
        String pendingCategory = "";
        String pendingLogo = "";
        boolean hasHlsTags = false;

        while ((line = reader.readLine()) != null) {
            line = line.trim();

            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("#EXT-X-")) {
                hasHlsTags = true;
            }

            if (line.regionMatches(true, 0, "#EXTGRP:", 0, 8)) {
                pendingCategory = line.substring(8).trim();
                continue;
            }

            if (line.startsWith("#EXTINF:")) {
                int comma = line.indexOf(',');

                pendingName = comma >= 0
                        ? line.substring(comma + 1).trim()
                        : "";

                Matcher groupMatcher = groupTitle.matcher(line);
                Matcher logoMatcher = logoPattern.matcher(line);

                String category = pendingCategory;

                if (groupMatcher.find()) {
                    category = groupMatcher.group(1).trim();
                }

                pendingCategory = category;

                if (logoMatcher.find()) {
                    pendingLogo = logoMatcher.group(1).trim();
                } else {
                    pendingLogo = "";
                }

                continue;
            }

            // Ignore comments and other M3U metadata.
            if (line.startsWith("#")) {
                continue;
            }

            // This is the stream URL belonging to the pending entry.
            if (pendingName != null && !line.isEmpty()) {
                PlaylistChannel channel = new PlaylistChannel(
                        pendingName,
                        line,
                        pendingCategory
                );

                channel.logoUrl = pendingLogo;

                info.channels.add(channel);

                if (!pendingCategory.isEmpty()) {
                    categories.add(pendingCategory);
                }

                pendingName = null;
                pendingCategory = "";
                pendingLogo = "";
            }
        }

        info.isHlsManifest = hasHlsTags;

        if (hasHlsTags) {
            info.format = "HLS M3U8";
            info.channelCount = 0;
            info.channels.clear();
            info.categoryNames.clear();
        } else {
            info.channelCount = info.channels.size();
            info.categoryNames.clear();
            info.categoryNames.addAll(categories);
        }
    }

    /** Local playlist file -> full PlaylistInfo WITH channels.
     *  parse() only reads metadata (format, counts); parseM3uContent() fills the channel list. */
    public static PlaylistInfo parseLocal(
            ContentResolver resolver,
            Uri uri,
            String fileName
    ) throws IOException {

        PlaylistInfo info = parse(resolver, uri, fileName);      // validates extension + format

        String format = info.format == null ? "" : info.format;
        if (!format.equals("M3U") && !format.equals("M3U8") && !format.equals("HLS M3U8")) {
            return info;                                         // .pls/.xspf/etc: no channel parsing yet
        }

        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Unable to open selected file");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                parseM3uContent(reader, info);
            }
        }
        return info;
    }

}