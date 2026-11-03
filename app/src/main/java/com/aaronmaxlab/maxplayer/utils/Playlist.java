package com.aaronmaxlab.maxplayer.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.aaronmaxlab.maxplayer.filepicker.models.PlaylistInfo;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Locale;

public class Playlist {

    // file name
    public static String getFileName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                );

                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    // Reusable method to load both name and size
    public static void loadFileMetadata(Context context, Uri uri, PlaylistInfo info) {
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null, null, null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);

                if (nameIndex >= 0) info.fileName = cursor.getString(nameIndex);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) info.fileSize = cursor.getLong(sizeIndex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // remote
    public static void loadRemoteMetadata(
            String url,
            PlaylistInfo info
    ) throws IOException {

        HttpURLConnection connection = null;

        try {
            URL remoteUrl = URI.create(url).toURL();

            connection = (HttpURLConnection) remoteUrl.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);

            connection.connect();

            long size = connection.getContentLengthLong();

            if (size >= 0) {
                info.fileSize = size;
            }

            String path = remoteUrl.getPath();

            if (path != null && !path.isEmpty()) {
                String fileName = path.substring(path.lastIndexOf('/') + 1);

                if (!fileName.isEmpty()) {
                    info.fileName = fileName;
                }
            }

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // file size
    public static String formatFileSize(long bytes) {
        if (bytes < 0) return "Unknown size";
        if (bytes < 1024) return bytes + " B";

        if (bytes < 1024L * 1024) {
            return String.format(
                    Locale.getDefault(), "%.1f KB",
                    bytes / 1024.0
            );
        }

        if (bytes < 1024L * 1024 * 1024) {
            return String.format(
                    Locale.getDefault(), "%.2f MB",
                    bytes / (1024.0 * 1024)
            );
        }

        return String.format(
                Locale.getDefault(), "%.2f GB",
                bytes / (1024.0 * 1024 * 1024)
        );
    }
}