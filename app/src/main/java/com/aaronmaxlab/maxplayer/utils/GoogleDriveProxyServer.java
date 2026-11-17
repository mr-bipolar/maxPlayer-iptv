
package com.aaronmaxlab.maxplayer.utils;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GoogleDriveProxyServer {

    private static final String TAG = "DriveProxy";
    private static volatile GoogleDriveProxyServer instance;

    private final ExecutorService clients = Executors.newCachedThreadPool();
    private final String routeToken = UUID.randomUUID().toString().replace("-", "");

    private ServerSocket serverSocket;
    private int port;

    private GoogleDriveProxyServer() {
        startServer();
    }

    public static GoogleDriveProxyServer getInstance() {
        if (instance == null) {
            synchronized (GoogleDriveProxyServer.class) {
                if (instance == null) {
                    instance = new GoogleDriveProxyServer();
                }
            }
        }
        return instance;
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(
                    0, 20, InetAddress.getByName("127.0.0.1")
            );
            port = serverSocket.getLocalPort();

            Thread thread = new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket socket = serverSocket.accept();
                        clients.execute(() -> handleClient(socket));
                    } catch (Exception e) {
                        if (!serverSocket.isClosed()) {
                            Log.e(TAG, "Accept failed", e);
                        }
                    }
                }
            }, "DriveProxyServer");

            thread.setDaemon(true);
            thread.start();

        } catch (Exception e) {
            throw new IllegalStateException("Could not start Drive proxy", e);
        }
    }

    /**
     * Returns a local playback URL for Google Drive links.
     * All other URLs, including M3U8/HLS, are returned unchanged.
     */
    public String getPlaybackUrl(String originalUrl) {
        if (TextUtils.isEmpty(originalUrl)) return originalUrl;

        String fileId = extractFileId(originalUrl);
        if (fileId == null) return originalUrl;

        return "http://127.0.0.1:" + port
                + "/" + routeToken + "/drive/" + fileId;
    }

    private static String extractFileId(String value) {
        try {
            Uri uri = Uri.parse(value);
            String host = uri.getHost();

            if (host == null) return null;

            host = host.toLowerCase(Locale.ROOT);

            if (!host.equals("drive.google.com")
                    && !host.equals("www.drive.google.com")
                    && !host.equals("docs.google.com")) {
                return null;
            }

            String path = uri.getPath();

            if (path != null) {
                Matcher matcher = Pattern.compile(
                        "/file/d/([a-zA-Z0-9_-]+)"
                ).matcher(path);

                if (matcher.find()) return matcher.group(1);
            }

            String id = uri.getQueryParameter("id");
            if (id != null && id.matches("[a-zA-Z0-9_-]+")) {
                return id;
            }

        } catch (Exception e) {
            Log.w(TAG, "Invalid Drive URL", e);
        }

        return null;
    }

    private void handleClient(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(30000);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.ISO_8859_1
                    )
            );

            String requestLine = reader.readLine();
            if (requestLine == null) return;

            String[] request = requestLine.split(" ");
            if (request.length < 2) {
                writeError(socket, 400, "Bad Request");
                return;
            }

            String method = request[0].toUpperCase(Locale.ROOT);
            String path = request[1];

            String range = null;
            String ifRange = null;

            String header;
            while ((header = reader.readLine()) != null && !header.isEmpty()) {
                int colon = header.indexOf(':');
                if (colon <= 0) continue;

                String name = header.substring(0, colon).trim();
                String value = header.substring(colon + 1).trim();

                if ("Range".equalsIgnoreCase(name)) range = value;
                if ("If-Range".equalsIgnoreCase(name)) ifRange = value;
            }

            String prefix = "/" + routeToken + "/drive/";
            if (!path.startsWith(prefix)) {
                writeError(socket, 404, "Not Found");
                return;
            }

            String fileId = path.substring(prefix.length());
            if (!fileId.matches("[a-zA-Z0-9_-]+")) {
                writeError(socket, 400, "Invalid file ID");
                return;
            }

            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                writeError(socket, 405, "Method Not Allowed");
                return;
            }

            streamDriveFile(socket, fileId, range, ifRange, "HEAD".equals(method));

        } catch (SocketException e) {
            Log.d(TAG, "Player closed proxy connection (normal during playback)");
        } catch (Exception e) {
            Log.e(TAG, "Proxy request failed", e);
        }
    }


    private void streamDriveFile(
            Socket client,
            String fileId,
            String range,
            String ifRange,
            boolean headOnly
    ) throws Exception {

        // Use Google's current download endpoint.
        String downloadUrl =
                "https://drive.usercontent.google.com/download"
                        + "?export=download&confirm=t&id="
                        + URLEncoder.encode(fileId, "UTF-8");

        HttpURLConnection upstream = openUpstream(downloadUrl, range, ifRange);

        try {
            int status = upstream.getResponseCode();
            String contentType = upstream.getContentType();

            // Google may return a confirmation form instead of the video.
            if (isHtml(contentType)) {
                String cookie = getWarningCookie(upstream);
                String html = readSmallBody(upstream);

                String confirm = findInputValue(html, "confirm");
                String uuid = findInputValue(html, "uuid");

                // Some responses provide the confirmation token as a cookie.
                if (TextUtils.isEmpty(confirm)) {
                    confirm = getCookieValue(cookie);
                }

                Log.w(TAG, "Drive confirmation page received"
                        + ", status=" + status
                        + ", confirmFound=" + !TextUtils.isEmpty(confirm)
                        + ", uuidFound=" + !TextUtils.isEmpty(uuid));

                upstream.disconnect();

                if (TextUtils.isEmpty(confirm)) {
                    Log.e(TAG, "Could not extract Drive confirmation token. HTML: "
                            + safePreview(html));

                    writeError(client, 502,
                            "Google Drive confirmation token was not found. "
                                    + "Check that the file is publicly accessible.");
                    return;
                }

                StringBuilder confirmedUrl = new StringBuilder(
                        "https://drive.usercontent.google.com/download"
                                + "?export=download"
                                + "&id=" + URLEncoder.encode(fileId, "UTF-8")
                                + "&confirm=" + URLEncoder.encode(confirm, "UTF-8")
                );

                if (!TextUtils.isEmpty(uuid)) {
                    confirmedUrl.append("&uuid=")
                            .append(URLEncoder.encode(uuid, "UTF-8"));
                }

                upstream = openConfirmedUpstream(
                        confirmedUrl.toString(),
                        range,
                        ifRange,
                        cookie
                );

                status = upstream.getResponseCode();
                contentType = upstream.getContentType();
            }

            Log.i(TAG, "Drive upstream response"
                    + ", status=" + status
                    + ", contentType=" + contentType
                    + ", finalUrl=" + upstream.getURL());

            // Never send Google's HTML confirmation/error page to the player.
            if (isHtml(contentType)) {
                String html = readSmallBody(upstream);

                Log.e(TAG, "Drive still returned HTML after confirmation: "
                        + safePreview(html));

                writeError(client, 502,
                        "Google Drive returned HTML instead of video data. "
                                + "The file may require access or confirmation.");
                return;
            }

            OutputStream out = client.getOutputStream();

            String reason;
            switch (status) {
                case 200:
                    reason = "OK";
                    break;
                case 206:
                    reason = "Partial Content";
                    break;
                case 400:
                    reason = "Bad Request";
                    break;
                case 403:
                    reason = "Forbidden";
                    break;
                case 404:
                    reason = "Not Found";
                    break;
                case 416:
                    reason = "Range Not Satisfiable";
                    break;
                default:
                    reason = "Upstream Response";
                    break;
            }

            StringBuilder response = new StringBuilder();
            response.append("HTTP/1.1 ").append(status)
                    .append(" ").append(reason).append("\r\n");
            response.append("Connection: close\r\n");

            if (!TextUtils.isEmpty(contentType)) {
                response.append("Content-Type: ")
                        .append(contentType).append("\r\n");
            }

            copyHeader(upstream, response, "Content-Length");
            copyHeader(upstream, response, "Content-Range");
            copyHeader(upstream, response, "Accept-Ranges");
            copyHeader(upstream, response, "Last-Modified");
            copyHeader(upstream, response, "ETag");

            response.append("\r\n");

            out.write(response.toString().getBytes(
                    StandardCharsets.ISO_8859_1
            ));
            out.flush();

            if (!headOnly && status != 416) {
                InputStream input = status >= 400
                        ? upstream.getErrorStream()
                        : upstream.getInputStream();

                if (input != null) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;

                    while ((count = input.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                    }

                    input.close();
                    out.flush();
                }
            }

        } finally {
            upstream.disconnect();
        }
    }


    private HttpURLConnection openUpstream(
            String url, String range, String ifRange
    ) throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(url).openConnection();

        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("Accept-Encoding", "identity");

        if (!TextUtils.isEmpty(range)) {
            connection.setRequestProperty("Range", range);
        }

        if (!TextUtils.isEmpty(ifRange)) {
            connection.setRequestProperty("If-Range", ifRange);
        }

        return connection;
    }

    private HttpURLConnection openConfirmedUpstream(
            String url, String range, String ifRange, String cookie
    ) throws Exception {
        HttpURLConnection connection = openUpstream(url, range, ifRange);
        connection.setRequestProperty("Cookie", cookie);
        return connection;
    }

    private static boolean isHtml(String contentType) {
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).contains("text/html");
    }

    private static String getWarningCookie(HttpURLConnection connection) {
        for (String key : connection.getHeaderFields().keySet()) {
            if (key == null || !"Set-Cookie".equalsIgnoreCase(key)) continue;

            for (String value : connection.getHeaderFields().get(key)) {
                if (value != null && value.startsWith("download_warning")) {
                    return value.split(";", 2)[0];
                }
            }
        }
        return null;
    }

    private static String findConfirmToken(String html) {
        if (html == null) return null;

        Matcher matcher = Pattern.compile(
                "(?:confirm=|name=\"confirm\" value=\")([0-9A-Za-z_-]+)"
        ).matcher(html);

        return matcher.find() ? matcher.group(1) : null;
    }

    private static String readSmallBody(HttpURLConnection connection) {
        try (InputStream input = connection.getInputStream()) {
            byte[] buffer = new byte[1024 * 1024];
            int total = 0;
            int count;

            while (total < buffer.length
                    && (count = input.read(buffer, total,
                    buffer.length - total)) != -1) {
                total += count;
            }

            return new String(buffer, 0, total, StandardCharsets.UTF_8);

        } catch (Exception e) {
            return "";
        }
    }

    private static void copyHeader(
            HttpURLConnection connection,
            StringBuilder output,
            String name
    ) {
        String value = connection.getHeaderField(name);
        if (!TextUtils.isEmpty(value)) {
            output.append(name).append(": ").append(value).append("\r\n");
        }
    }

    private static void writeError(Socket socket, int status, String message) {
        try {
            byte[] body = message.getBytes(StandardCharsets.UTF_8);

            OutputStream out = socket.getOutputStream();
            String response = "HTTP/1.1 " + status + " Error\r\n"
                    + "Content-Type: text/plain; charset=utf-8\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";

            out.write(response.getBytes(StandardCharsets.ISO_8859_1));
            out.write(body);
            out.flush();

        } catch (Exception ignored) {
        }
    }


    private static String findInputValue(String html, String wantedName) {
        if (html == null || html.isEmpty()) return null;

        Pattern inputPattern = Pattern.compile(
                "(?is)<input\\b[^>]*>"
        );

        Pattern namePattern = Pattern.compile(
                "(?is)\\bname\\s*=\\s*(['\"])(.*?)\\1"
        );

        Pattern valuePattern = Pattern.compile(
                "(?is)\\bvalue\\s*=\\s*(['\"])(.*?)\\1"
        );

        Matcher inputs = inputPattern.matcher(html);

        while (inputs.find()) {
            String tag = inputs.group();

            Matcher nameMatcher = namePattern.matcher(tag);
            Matcher valueMatcher = valuePattern.matcher(tag);

            if (nameMatcher.find()
                    && wantedName.equalsIgnoreCase(nameMatcher.group(2))
                    && valueMatcher.find()) {

                return decodeHtml(valueMatcher.group(2));
            }
        }

        return null;
    }

    private static String decodeHtml(String value) {
        if (value == null) return null;

        return value.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static String getCookieValue(String cookie) {
        if (TextUtils.isEmpty(cookie)) return null;

        int equals = cookie.indexOf('=');
        if (equals < 0 || equals == cookie.length() - 1) return null;

        return cookie.substring(equals + 1);
    }

    private static String safePreview(String value) {
        if (value == null) return "(empty)";

        String clean = value.replaceAll("\\s+", " ");
        return clean.substring(0, Math.min(clean.length(), 400));
    }

}
