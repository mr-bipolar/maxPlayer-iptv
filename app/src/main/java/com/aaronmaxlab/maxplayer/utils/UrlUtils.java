package com.aaronmaxlab.maxplayer.utils;


import android.net.Uri;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class UrlUtils {

    public static boolean isValidStreamUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        try {
            Uri uri = Uri.parse(url.trim());
            String scheme = uri.getScheme();

            return uri.getHost() != null
                    && !uri.getHost().isEmpty()
                    && ("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme))
                    && uri.getUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isStreamReachable(String streamUrl) {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(streamUrl);

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Range", "bytes=0-0");
            connection.setRequestProperty("Icy-MetaData", "0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(false);

            int responseCode = connection.getResponseCode();

            return responseCode >= 200 && responseCode < 300;

        } catch (IOException | IllegalArgumentException e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
