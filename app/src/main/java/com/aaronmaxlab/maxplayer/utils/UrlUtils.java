package com.aaronmaxlab.maxplayer.utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.util.Locale;

public class UrlUtils {

    private static final int PROBE_TIMEOUT_MS = 5000;

    public static boolean isValidStreamUrl(String url) {
        return MediaTypes.isSupportedUrl(url);      // http, https, rtmp, rtsp, udp, ftp...
    }

    public static boolean isValidPlaylistUrl(String url) {
        return MediaTypes.isHttpUrl(url);           // playlists can only be downloaded over http/https
    }

    /** Call from a background thread. */
    public static boolean isStreamReachable(String streamUrl) {
        String scheme = MediaTypes.scheme(streamUrl);
        if (scheme == null) return false;

        switch (scheme) {
            case "http":
            case "https":
                return isHttpReachable(streamUrl);

            case "ftp":    return isPortReachable(streamUrl, 21);
            case "rtmp":   return isPortReachable(streamUrl, 1935);
            case "rtmps":  return isPortReachable(streamUrl, 443);
            case "rtsp":   return isPortReachable(streamUrl, 554);
            case "mms":
            case "mmst":   return isPortReachable(streamUrl, 1755);
            case "mmsh":   return isPortReachable(streamUrl, 80);
            case "tcp":    return isPortReachable(streamUrl, -1);   // tcp://host:port always has a port

            default:
                // file, content, data, udp, rtp, lavf: there is nothing to probe, let the player try.
                return true;
        }
    }

    private static boolean isHttpReachable(String streamUrl) {
        HttpURLConnection connection = null;
        try {
            connection = openProbe(streamUrl);
            int code = connection.getResponseCode();
            return code >= 200 && code < 300;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Opens (and immediately closes) a TCP connection to the server: "can I reach this host and port?" */
    private static boolean isPortReachable(String streamUrl, int defaultPort) {
        try {
            URI uri = URI.create(streamUrl);
            String host = uri.getHost();
            int port = uri.getPort() != -1 ? uri.getPort() : defaultPort;
            if (host == null || port <= 0) return true;      // cannot tell: let the player try

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
                return true;
            }
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    /** One probe request for http/https (small Range request, no caching). */
    private static HttpURLConnection openProbe(String streamUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(streamUrl).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Range", "bytes=0-0");
        connection.setRequestProperty("Icy-MetaData", "0");
        connection.setConnectTimeout(PROBE_TIMEOUT_MS);
        connection.setReadTimeout(PROBE_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        return connection;
    }

    /** Call from a background thread. */
    public static boolean isMediaStreamUrl(String streamUrl) {
        String scheme = MediaTypes.scheme(streamUrl);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return isStreamReachable(streamUrl);       // ftp, rtmp, rtsp, udp...: no Content-Type to inspect
        }

        HttpURLConnection connection = null;
        try {
            connection = openProbe(streamUrl);

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return false;

            String contentType = connection.getContentType();

            // Some IPTV servers don't provide a Content-Type.
            if (contentType == null || contentType.trim().isEmpty()) return true;

            contentType = contentType.toLowerCase(Locale.ROOT);

            return contentType.startsWith("video/")
                    || contentType.startsWith("audio/")
                    || contentType.contains("mpegurl")
                    || contentType.contains("m3u")
                    || contentType.contains("mp2t")
                    || contentType.contains("octet-stream");

        } catch (IOException | IllegalArgumentException e) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
