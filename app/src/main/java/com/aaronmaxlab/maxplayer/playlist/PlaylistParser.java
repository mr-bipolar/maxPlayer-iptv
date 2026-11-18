package com.aaronmaxlab.maxplayer.playlist;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.aaronmaxlab.maxplayer.playlist.models.PlaylistChannel;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistInfo;
import com.aaronmaxlab.maxplayer.utils.MediaTypes;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

public final class PlaylistParser {

    private static final int MAX_PLAYLIST_BYTES = 16 * 1024 * 1024;
    private static final int MAX_LINE_BYTES = 64 * 1024;
    private static final int MAX_CHANNELS = 80_000;
    private static final int MAX_REDIRECTS = 20;
    private static final int MAX_LOCATION_BYTES = 8 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    private static final Pattern GROUP_TITLE = Pattern.compile(
            "group-title\\s*=\\s*\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ENCODED_CONTROL = Pattern.compile(
            "(?i)%(0[0-9a-f]|1[0-9a-f]|7f)"
    );

    private static final Pattern ALL_DIGITS_AND_DOTS = Pattern.compile("[0-9.]+");

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

        ValidatedUri current = parseValidatedHttpUri(urlString);
        int redirects = 0;

        while (true) {
            OkHttpClient client = buildClient(current);
            Response response = null;

            try {
                Request request = new Request.Builder()
                        .url(current.canonicalUrl)
                        .header("Accept", "*/*")
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Accept-Encoding", "identity")
                        .build();

                response = client.newCall(request).execute();
                int status = response.code();

                if (isRedirectStatus(status)) {
                    if (++redirects > MAX_REDIRECTS) {
                        throw new IOException("Too many redirects");
                    }

                    ValidatedUri next = resolveRedirect(current, response);

                    // Close this hop completely before creating the next connection.
                    response.close();
                    response = null;

                    current = next;
                    continue;
                }

                if (status < 200 || status >= 300) {
                    throw new IOException("Server returned HTTP " + status);
                }

                validateResponseHeaders(response);
                return parseRemoteResponse(response, fileName);

            } finally {
                if (response != null) {
                    response.close();
                }
                client.connectionPool().evictAll();
                client.dispatcher().executorService().shutdown();
            }
        }
    }

    static PlaylistInfo parseRemoteResponse(Response response, String fileName) throws IOException {
        validateContentEncoding(response);

        if (response.body() == null) {
            throw new IOException("Empty playlist response");
        }

        try (InputStream raw = response.body().byteStream();
             LimitedInputStream limited = new LimitedInputStream(raw, MAX_PLAYLIST_BYTES);
             ByteLineReader reader = new ByteLineReader(limited)) {

            return parseRemoteLines(reader, fileName);
        }
    }

    static PlaylistInfo parseRemoteLines(ByteLineReader reader, String fileName) throws IOException {
        PlaylistInfo info = new PlaylistInfo();
        info.fileName = fileName;
        info.format = fileName.toLowerCase(Locale.ROOT)
                .endsWith(".m3u8") ? "M3U8" : "M3U";
        info.isPlaylist = true;

        Set<String> categories = new LinkedHashSet<>();

        boolean isHls = false;
        int entries = 0;

        String line;
        String pendingName = null;
        String pendingCategory = "";
        String pendingLogo = "";

        Pattern logoPattern = Pattern.compile(
                "tvg-logo\\s*=\\s*\"([^\"]*)\"",
                Pattern.CASE_INSENSITIVE
        );

        while ((line = reader.readLine()) != null) {
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

        info.isHlsManifest = isHls;
        info.channelCount = isHls ? 0 : entries;

        if (!isHls) {
            info.categoryNames.addAll(categories);
        } else {
            info.format = "HLS M3U8";
        }

        return info;
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

    // ---------------------------------------------------------------------
    // Remote URL validation and safe connection helpers
    // ---------------------------------------------------------------------

    static final class ValidatedUri {
        final String scheme;
        final String canonicalHost;      // unbracketed; ASCII DNS/IDN or canonical IP text
        final String hostForUrl;         // bracketed for IPv6
        final int port;
        final String rawPath;
        final String rawQuery;
        final String canonicalUrl;
        final boolean ipLiteral;
        final InetAddress literalAddress;

        ValidatedUri(
                String scheme,
                String canonicalHost,
                String hostForUrl,
                int port,
                String rawPath,
                String rawQuery,
                String canonicalUrl,
                boolean ipLiteral,
                InetAddress literalAddress
        ) {
            this.scheme = scheme;
            this.canonicalHost = canonicalHost;
            this.hostForUrl = hostForUrl;
            this.port = port;
            this.rawPath = rawPath;
            this.rawQuery = rawQuery;
            this.canonicalUrl = canonicalUrl;
            this.ipLiteral = ipLiteral;
            this.literalAddress = literalAddress;
        }
    }

    static ValidatedUri parseValidatedHttpUri(String input) throws IOException {
        if (input == null) {
            throw new IOException("Missing URL");
        }

        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            throw new IOException("Empty URL");
        }

        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isISOControl(trimmed.charAt(i))) {
                throw new IOException("Control character in URL");
            }
        }

        final URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IOException("Malformed URL", e);
        }

        if (!uri.isAbsolute() || uri.isOpaque() || uri.getScheme() == null) {
            throw new IOException("URL must be absolute and hierarchical");
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IOException("Only HTTP and HTTPS URLs are supported.");
        }

        String authority = uri.getRawAuthority();
        if (authority == null || authority.isEmpty()) {
            throw new IOException("Missing URL authority");
        }

        if (authority.indexOf('@') >= 0
                || uri.getRawUserInfo() != null
                || uri.getUserInfo() != null) {
            throw new IOException("URL user information is not allowed");
        }

        if (authority.indexOf('%') >= 0) {
            throw new IOException("Encoded URL host is not allowed");
        }

        String rawPath = uri.getRawPath();
        if (rawPath == null || rawPath.isEmpty()) {
            rawPath = "/";
        }
        if (rawPath.charAt(0) != '/') {
            throw new IOException("Invalid URL path");
        }
        validateRawComponent("path", rawPath);

        String rawQuery = uri.getRawQuery();
        if (rawQuery != null) {
            validateRawComponent("query", rawQuery);
        }

        int defaultPort = scheme.equals("https") ? 443 : 80;

        final String canonicalHost;
        final String hostForUrl;
        final int port;
        final boolean ipLiteral;
        final InetAddress literalAddress;

        if (authority.charAt(0) == '[') {
            int close = authority.indexOf(']');
            if (close <= 1
                    || authority.indexOf('[', 1) >= 0
                    || authority.indexOf(']', close + 1) >= 0) {
                throw new IOException("Malformed IPv6 host");
            }

            String inside = authority.substring(1, close);
            String suffix = authority.substring(close + 1);

            String portText = null;
            if (!suffix.isEmpty()) {
                if (suffix.charAt(0) != ':') {
                    throw new IOException("Malformed IPv6 authority");
                }
                portText = suffix.substring(1);
                if (portText.isEmpty()) {
                    throw new IOException("Empty port");
                }
            }

            canonicalHost = parseIpv6Literal(inside);
            hostForUrl = "[" + canonicalHost + "]";
            port = portText == null ? defaultPort : parsePort(portText);
            ipLiteral = true;
            literalAddress = parseLiteralAddress(canonicalHost);

            String hostPortion = authority.substring(0, close + 1);
            if (uri.getHost() != null && !uri.getHost().equalsIgnoreCase(hostPortion)) {
                throw new IOException("Host parsing mismatch");
            }
            checkUriPort(uri, portText == null, port);

        } else {
            String hostPart;
            String portText = null;

            int colon = authority.indexOf(':');
            if (colon >= 0) {
                if (authority.indexOf(':', colon + 1) >= 0) {
                    throw new IOException("Malformed URL authority");
                }
                hostPart = authority.substring(0, colon);
                portText = authority.substring(colon + 1);
                if (portText.isEmpty()) {
                    throw new IOException("Empty port");
                }
            } else {
                hostPart = authority;
            }

            if (hostPart.isEmpty()
                    || hostPart.indexOf('[') >= 0
                    || hostPart.indexOf(']') >= 0
                    || hostPart.indexOf('%') >= 0) {
                throw new IOException("Malformed URL host");
            }

            boolean asciiHost = isAscii(hostPart);
            String asciiCanonical;

            if (asciiHost) {
                if (uri.getHost() == null
                        || !uri.getHost().equalsIgnoreCase(hostPart)) {
                    throw new IOException("Malformed ASCII URL host");
                }
                asciiCanonical = hostPart.toLowerCase(Locale.ROOT);
            } else {
                if (uri.getHost() != null) {
                    throw new IOException("Malformed IDN authority");
                }
                try {
                    asciiCanonical = IDN.toASCII(hostPart, IDN.USE_STD3_ASCII_RULES);
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid IDN", e);
                }
                if (asciiCanonical == null || asciiCanonical.isEmpty() || !isAscii(asciiCanonical)) {
                    throw new IOException("Invalid IDN conversion");
                }
                asciiCanonical = asciiCanonical.toLowerCase(Locale.ROOT);
            }

            validateAsciiHost(asciiCanonical);

            InetAddress parsedIpv4 = tryParseCanonicalIpv4(asciiCanonical);
            if (parsedIpv4 != null) {
                if (isProhibitedAddress(parsedIpv4)) {
                    throw new IOException("Prohibited destination address");
                }
                canonicalHost = parsedIpv4.getHostAddress();
                hostForUrl = canonicalHost;
                ipLiteral = true;
                literalAddress = parsedIpv4;
            } else {
                canonicalHost = asciiCanonical;
                hostForUrl = asciiCanonical;
                ipLiteral = false;
                literalAddress = null;
            }

            port = portText == null ? defaultPort : parsePort(portText);
            checkUriPort(uri, portText == null, port);
        }

        String provisionalUrl = buildCanonicalUrl(scheme, hostForUrl, port, rawPath, rawQuery);

        HttpUrl httpUrl;
        try {
            httpUrl = HttpUrl.get(provisionalUrl);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid canonical URL", e);
        }

        // HttpUrl performs RFC 3986 path canonicalization (including dot-segment
        // removal from redirect targets). Use that canonical form for the request
        // and assert that the authority/scheme were not changed.
        String canonicalPath = httpUrl.encodedPath();
        String canonicalQuery = httpUrl.encodedQuery();
        String canonicalUrl = httpUrl.toString();

        assertHttpUrlMatches(
                scheme, canonicalHost, port, canonicalPath, canonicalQuery, canonicalUrl, httpUrl);
        assertCanonicalServerAuthority(canonicalUrl, hostForUrl, port, defaultPort);

        return new ValidatedUri(
                scheme,
                canonicalHost,
                hostForUrl,
                port,
                canonicalPath,
                canonicalQuery,
                canonicalUrl,
                ipLiteral,
                literalAddress
        );
    }

    private static void validateRawComponent(String name, String value) throws IOException {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || c > 127) {
                throw new IOException("Unsupported character in URL " + name);
            }
        }

        if (ENCODED_CONTROL.matcher(value).find()) {
            throw new IOException("Encoded control character in URL " + name);
        }
    }

    private static String buildCanonicalUrl(
            String scheme,
            String hostForUrl,
            int port,
            String rawPath,
            String rawQuery
    ) {
        StringBuilder out = new StringBuilder();
        out.append(scheme).append("://").append(hostForUrl);

        int defaultPort = scheme.equals("https") ? 443 : 80;
        if (port != defaultPort) {
            out.append(':').append(port);
        }

        out.append(rawPath == null || rawPath.isEmpty() ? "/" : rawPath);
        if (rawQuery != null) {
            out.append('?').append(rawQuery);
        }
        return out.toString();
    }

    private static void assertHttpUrlMatches(
            String scheme,
            String canonicalHost,
            int port,
            String rawPath,
            String rawQuery,
            String canonicalUrl,
            HttpUrl httpUrl
    ) throws IOException {
        if (!httpUrl.scheme().equals(scheme)
                || !httpUrl.username().isEmpty()
                || !httpUrl.password().isEmpty()
                || !httpUrl.host().equals(canonicalHost)
                || httpUrl.port() != port
                || !httpUrl.encodedPath().equals(rawPath)
                || !Objects.equals(httpUrl.encodedQuery(), rawQuery)
                || httpUrl.fragment() != null
                || !httpUrl.toString().equals(canonicalUrl)) {
            throw new IOException("Canonical URL identity mismatch");
        }
    }

    private static void assertCanonicalServerAuthority(
            String canonicalUrl,
            String hostForUrl,
            int port,
            int defaultPort
    ) throws IOException {
        try {
            URI parsed = new URI(canonicalUrl).parseServerAuthority();
            String expectedHost = hostForUrl;

            if (parsed.getHost() == null
                    || !parsed.getHost().equalsIgnoreCase(expectedHost)) {
                throw new IOException("Canonical host mismatch");
            }

            if (port == defaultPort) {
                if (parsed.getPort() != -1) {
                    throw new IOException("Canonical port mismatch");
                }
            } else if (parsed.getPort() != port) {
                throw new IOException("Canonical port mismatch");
            }
        } catch (URISyntaxException e) {
            throw new IOException("Canonical authority mismatch", e);
        }
    }

    private static void checkUriPort(URI uri, boolean wasDefault, int port) throws IOException {
        if (uri.getHost() == null) {
            return; // Unicode IDN authorities are registry-based in java.net.URI.
        }

        if (wasDefault) {
            if (uri.getPort() != -1) {
                throw new IOException("Port parsing mismatch");
            }
        } else if (uri.getPort() != port) {
            throw new IOException("Port parsing mismatch");
        }
    }

    private static int parsePort(String value) throws IOException {
        if (value.isEmpty()) {
            throw new IOException("Empty port");
        }

        long parsed = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                throw new IOException("Invalid port");
            }
            parsed = parsed * 10 + (c - '0');
            if (parsed > 65535L) {
                throw new IOException("Port out of range");
            }
        }

        if (parsed < 1 || parsed > 65535L) {
            throw new IOException("Port out of range");
        }
        return (int) parsed;
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    private static void validateAsciiHost(String host) throws IOException {
        if (host.isEmpty() || host.length() > 253) {
            throw new IOException("Invalid host length");
        }

        if (host.endsWith(".")) {
            throw new IOException("Trailing dot host is not allowed");
        }

        if (ALL_DIGITS_AND_DOTS.matcher(host).matches()) {
            if (tryParseCanonicalIpv4(host) == null) {
                throw new IOException("Ambiguous numeric host");
            }
            return; // Canonical IPv4 literal; policy is checked by the caller.
        }

        String[] labels = getLabels(host);

        if (labels.length > 1) {
            char first = labels[labels.length - 1].charAt(0);
            boolean alpha = (first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z');
            if (!alpha) {
                throw new IOException("Invalid top-level label");
            }
        }
    }

    @NonNull
    private static String[] getLabels(String host) throws IOException {
        String[] labels = host.split("\\.", -1);
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            if (label.isEmpty() || label.length() > 63) {
                throw new IOException("Invalid host label");
            }
            if (label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') {
                throw new IOException("Invalid host label");
            }
            for (int j = 0; j < label.length(); j++) {
                char c = label.charAt(j);
                boolean valid = (c >= 'a' && c <= 'z')
                        || (c >= 'A' && c <= 'Z')
                        || (c >= '0' && c <= '9')
                        || c == '-';
                if (!valid) {
                    throw new IOException("Invalid host character");
                }
            }
            if (label.toLowerCase(Locale.ROOT).startsWith("0x")) {
                throw new IOException("Ambiguous numeric host");
            }
        }
        return labels;
    }

    private static InetAddress tryParseCanonicalIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }

        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            if (part.length() > 1 && part.charAt(0) == '0') {
                return null;
            }

            int value = 0;
            for (int j = 0; j < part.length(); j++) {
                char c = part.charAt(j);
                if (c < '0' || c > '9') {
                    return null;
                }
                value = value * 10 + (c - '0');
            }
            if (value < 0 || value > 255) {
                return null;
            }
            bytes[i] = (byte) value;
        }

        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static InetAddress parseLiteralAddress(String host) throws IOException {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new IOException("Invalid IP literal", e);
        }
    }

    private static String parseIpv6Literal(String inside) throws IOException {
        if (inside.isEmpty() || inside.indexOf(':') < 0 || inside.indexOf('%') >= 0) {
            throw new IOException("Malformed IPv6 host");
        }

        for (int i = 0; i < inside.length(); i++) {
            char c = inside.charAt(i);
            boolean valid = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F')
                    || c == ':'
                    || c == '.';
            if (!valid) {
                throw new IOException("Malformed IPv6 host");
            }
        }

        InetAddress address = parseLiteralAddress(inside);
        if (address instanceof Inet4Address) {
            // IPv4-mapped and compatible forms are rejected by the address policy.
            if (!isProhibitedAddress(address)) {
                throw new IOException("Unsupported IPv6 host form");
            }
            throw new IOException("Prohibited destination address");
        }
        if (!(address instanceof Inet6Address)) {
            throw new IOException("Malformed IPv6 host");
        }
        if (isProhibitedAddress(address)) {
            throw new IOException("Prohibited destination address");
        }

        return canonicalizeIpv6Host(inside);
    }

    private static String canonicalizeIpv6Host(String inside) throws IOException {
        try {
            HttpUrl parsed = HttpUrl.get("http://[" + inside + "]/");
            String canonical = parsed.host();
            if (canonical.isEmpty() || canonical.indexOf(':') < 0) {
                throw new IOException("Malformed IPv6 host");
            }
            return canonical.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new IOException("Malformed IPv6 host", e);
        }
    }

    private static OkHttpClient buildClient(ValidatedUri uri) {
        Dns pinningDns = new PinningDns(uri.canonicalHost);

        return new OkHttpClient.Builder()
                .dns(pinningDns)
                .proxy(Proxy.NO_PROXY)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build();
    }

    static final class PinningDns implements Dns {
        private final String expectedHost;
        private final Dns delegate;

        PinningDns(String expectedHost) {
            this(expectedHost, Dns.SYSTEM);
        }

        PinningDns(String expectedHost, Dns delegate) {
            this.expectedHost = expectedHost;
            this.delegate = delegate;
        }

        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            if (hostname == null || !hostname.equalsIgnoreCase(expectedHost)) {
                throw new UnknownHostException("Unexpected DNS hostname");
            }

            List<InetAddress> answers = delegate.lookup(hostname);
            if (answers == null || answers.isEmpty()) {
                throw new UnknownHostException("No DNS addresses");
            }

            for (InetAddress address : answers) {
                if (address == null || isProhibitedAddress(address)) {
                    throw new UnknownHostException("Prohibited DNS address");
                }
            }

            return Collections.singletonList(answers.get(0));
        }
    }

    private static boolean isRedirectStatus(int status) {
        return status == 301 || status == 302 || status == 303
                || status == 307 || status == 308;
    }

    static ValidatedUri resolveRedirect(ValidatedUri current, Response response)
            throws IOException {
        List<String> locations = response.headers("Location");
        if (locations.isEmpty()) {
            throw new IOException("Redirect without Location");
        }
        if (locations.size() > 1) {
            throw new IOException("Multiple Location headers");
        }
        String location = locations.get(0);
        if (location.trim().isEmpty()) {
            throw new IOException("Redirect without Location");
        }
        if (location.length() > MAX_LOCATION_BYTES) {
            throw new IOException("Redirect Location too long");
        }
        for (int i = 0; i < location.length(); i++) {
            if (Character.isISOControl(location.charAt(i))) {
                throw new IOException("Control character in redirect Location");
            }
        }

        final URI resolved;
        try {
            URI base = new URI(current.canonicalUrl);
            URI target = new URI(location);
            resolved = base.resolve(target);
        } catch (URISyntaxException e) {
            throw new IOException("Malformed redirect Location", e);
        }

        ValidatedUri next = parseValidatedHttpUri(resolved.toString());
        if (current.scheme.equals("https") && !next.scheme.equals("https")) {
            throw new IOException("HTTPS to HTTP redirect rejected");
        }
        return next;
    }

    static void validateResponseHeaders(Response response) throws IOException {
        List<String> contentLengths = response.headers("Content-Length");
        if (contentLengths.size() > 1) {
            throw new IOException("Duplicate Content-Length");
        }
        if (!contentLengths.isEmpty()) {
            long length = parseContentLength(contentLengths.get(0));
            if (length > MAX_PLAYLIST_BYTES) {
                throw new IOException("Playlist too large");
            }
        }

        List<String> transferEncodings = response.headers("Transfer-Encoding");
        if (transferEncodings.size() > 1) {
            throw new IOException("Multiple Transfer-Encoding headers");
        }
        if (!transferEncodings.isEmpty()) {
            String encoding = transferEncodings.get(0).trim();
            if (!encoding.equalsIgnoreCase("chunked")) {
                throw new IOException("Unsupported Transfer-Encoding");
            }
            if (!contentLengths.isEmpty()) {
                throw new IOException("Conflicting Content-Length and Transfer-Encoding");
            }
        }
    }

    private static long parseContentLength(String value) throws IOException {
        if (value == null || value.isEmpty()) {
            throw new IOException("Invalid Content-Length");
        }

        long result = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                throw new IOException("Invalid Content-Length");
            }
            int digit = c - '0';

            if (result > (Long.MAX_VALUE - digit) / 10) {
                throw new IOException("Invalid Content-Length");
            }

            result = result * 10 + digit;
            if (result > MAX_PLAYLIST_BYTES) {
                return result;
            }
        }
        return result;
    }

    static void validateContentEncoding(Response response) throws IOException {
        List<String> encodings = response.headers("Content-Encoding");
        if (encodings.isEmpty()) {
            return;
        }
        if (encodings.size() == 1 && encodings.get(0).trim().equalsIgnoreCase("identity")) {
            return;
        }
        throw new IOException("Unsupported Content-Encoding");
    }

    static final class LimitedInputStream extends FilterInputStream {
        private long remaining;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = limit;
        }

        private int checkExhausted() throws IOException {
            int value = in.read();
            if (value == -1) {
                return -1;
            }
            throw new IOException("Playlist too large");
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return checkExhausted();
            }
            int value = in.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (remaining <= 0) {
                return checkExhausted();
            }

            int allowed = (int) Math.min((long) length, remaining);
            int read = in.read(buffer, offset, allowed);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }
    }

    static final class ByteLineReader implements Closeable {
        private final InputStream in;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream(256);
        private int pending = -1;

        ByteLineReader(InputStream in) {
            this.in = in;
        }

        private int readByte() throws IOException {
            if (pending >= 0) {
                int value = pending;
                pending = -1;
                return value;
            }
            return in.read();
        }

        private String finishLine() throws IOException {
            byte[] bytes = line.toByteArray();
            line.reset();

            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        }

        String readLine() throws IOException {
            line.reset();
            boolean sawAny = false;

            while (true) {
                int value = readByte();

                if (value == -1) {
                    return sawAny ? finishLine() : null;
                }

                sawAny = true;

                if (value == '\n') {
                    return finishLine();
                }

                if (value == '\r') {
                    int next = readByte();
                    if (next != -1 && next != '\n') {
                        pending = next;
                    }
                    return finishLine();
                }

                if (line.size() >= MAX_LINE_BYTES) {
                    throw new IOException("Playlist line too long");
                }
                line.write(value);
            }
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    // ---------------------------------------------------------------------
    // Address policy
    // ---------------------------------------------------------------------

    static boolean matchesPrefix(byte[] address, byte[] prefix, int bits) {
        int required = (bits + 7) / 8;
        if (bits < 0 || prefix.length < required || address.length < required) {
            throw new IllegalArgumentException("Invalid address prefix");
        }

        int fullBytes = bits / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (address[i] != prefix[i]) {
                return false;
            }
        }

        int remainingBits = bits % 8;
        if (remainingBits == 0) {
            return true;
        }

        int mask = (0xff << (8 - remainingBits)) & 0xff;
        return (address[fullBytes] & mask) == (prefix[fullBytes] & mask);
    }

    static boolean isProhibitedAddress(InetAddress address) {
        if (address == null) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes == null) {
            return true;
        }

        if (address instanceof Inet4Address || bytes.length == 4) {
            return isProhibitedIpv4(bytes);
        }

        if (address instanceof Inet6Address || bytes.length == 16) {
            return isProhibitedIpv6(bytes);
        }

        return true;
    }

    private static boolean isProhibitedIpv4(byte[] a) {
        return matchesPrefix(a, prefix4(0, 0, 0, 0, 8), 8)          // 0.0.0.0/8
                || matchesPrefix(a, prefix4(10, 0, 0, 0, 8), 8)     // 10.0.0.0/8
                || matchesPrefix(a, prefix4(100, 64, 0, 0, 10), 10) // 100.64.0.0/10
                || matchesPrefix(a, prefix4(127, 0, 0, 0, 8), 8)    // 127.0.0.0/8
                || matchesPrefix(a, prefix4(169, 254, 0, 0, 16), 16)
                || matchesPrefix(a, prefix4(172, 16, 0, 0, 12), 12)
                || matchesPrefix(a, prefix4(192, 0, 0, 0, 24), 24)
                || matchesPrefix(a, prefix4(192, 31, 196, 0, 24), 24)
                || matchesPrefix(a, prefix4(192, 52, 193, 0, 24), 24)
                || matchesPrefix(a, prefix4(192, 88, 99, 0, 24), 24)
                || matchesPrefix(a, prefix4(192, 168, 0, 0, 16), 16)
                || matchesPrefix(a, prefix4(192, 175, 48, 0, 24), 24)
                || matchesPrefix(a, prefix4(198, 18, 0, 0, 15), 15)
                || matchesPrefix(a, prefix4(198, 51, 100, 0, 24), 24)
                || matchesPrefix(a, prefix4(203, 0, 113, 0, 24), 24)
                || matchesPrefix(a, prefix4(224, 0, 0, 0, 4), 4)    // multicast
                || matchesPrefix(a, prefix4(240, 0, 0, 0, 4), 4);   // reserved
    }

    private static byte[] prefix4(int b0, int b1, int b2, int b3, int bits) {
        return new byte[]{(byte) b0, (byte) b1, (byte) b2, (byte) b3};
    }

    private static boolean isProhibitedIpv6(byte[] a) {
        // IPv4-mapped: ::ffff:0:0/96
        if (matchesPrefix(a, new byte[]{
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff
        }, 96)) {
            return true;
        }

        // IPv4-compatible / unspecified: ::/96
        if (matchesPrefix(a, new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 96)) {
            return true;
        }

        if (matchesPrefix(a, new byte[]{0, 100, (byte) 0xff, (byte) 0x9b,
                0, 0, 0, 0, 0, 0, 0, 0}, 96)) { // 64:ff9b::/96
            return true;
        }

        if (matchesPrefix(a, new byte[]{0, 100, (byte) 0xff, (byte) 0x9b, 0, 1}, 48)) {
            return true; // 64:ff9b:1::/48
        }

        if (matchesPrefix(a, new byte[]{1, 0, 0, 0, 0, 0, 0, 0}, 64)) { // 100::/64
            return true;
        }

        if (matchesPrefix(a, new byte[]{1, 0, 0, 0, 0, 0, 0, 1}, 64)) { // 100:0:0:1::/64
            return true;
        }

        if (matchesPrefix(a, new byte[]{0x20, 0x01, 0x00}, 23)) { // 2001::/23
            return true;
        }

        if (matchesPrefix(a, new byte[]{0x20, 0x02}, 16)) { // 2002::/16
            return true;
        }

        if (matchesPrefix(a, new byte[]{0x26, 0x20, 0x4f, (byte) 0x80, 0, 0}, 48)) {
            return true; // 2620:4f:8000::/48
        }

        if (matchesPrefix(a, new byte[]{0x3f, (byte) 0xff, (byte) 0xf0}, 20)) { // 3fff::/20
            return true;
        }

        if (matchesPrefix(a, new byte[]{0x5f, 0x00}, 16)) { // 5f00::/16
            return true;
        }

        if (matchesPrefix(a, new byte[]{(byte) 0xfc}, 7)) { // fc00::/7
            return true;
        }

        if (matchesPrefix(a, new byte[]{(byte) 0xfe, (byte) 0x80}, 10)) { // fe80::/10
            return true;
        }

        if (matchesPrefix(a, new byte[]{(byte) 0xfe, (byte) 0xf0}, 10)) { // fec0::/10
            return true;
        }

        if (matchesPrefix(a, new byte[]{(byte) 0xff}, 8)) { // ff00::/8
            return true;
        }

        // ISATAP-shaped addresses.
        if ((a[8] == 0 && a[9] == 0 && a[10] == 0x5e && (a[11] & 0xff) == 0xfe)
                || (a[8] == 2 && a[9] == 0 && a[10] == 0x5e && (a[11] & 0xff) == 0xfe)) {
            return true;
        }

        return false;
    }
}
