package com.aaronmaxlab.maxplayer.filepicker.parser;

import android.content.ContentResolver;
import android.net.Uri;

import com.aaronmaxlab.maxplayer.filepicker.models.PlaylistInfo;

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

    private static final Pattern GROUP_TITLE = Pattern.compile(
            "group-title\\s*=\\s*\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE
    );

    private PlaylistParser() {}

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

                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    if (line.startsWith("#EXT-X-")) {
                        isHls = true;
                    }

                    if (line.startsWith("#EXTINF:")) {
                        entries++;

                        java.util.regex.Matcher matcher =
                                java.util.regex.Pattern.compile(
                                        "group-title\\s*=\\s*\"([^\"]*)\"",
                                        java.util.regex.Pattern.CASE_INSENSITIVE
                                ).matcher(line);

                        if (matcher.find()) {
                            String category = matcher.group(1).trim();

                            if (!category.isEmpty()) {
                                categories.add(category);
                            }
                        }
                    }
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
}