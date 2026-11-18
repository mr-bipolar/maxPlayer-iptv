package com.aaronmaxlab.maxplayer.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.aaronmaxlab.maxplayer.playlist.models.PlaylistInfo;

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


    // time
    public static String getRelativeTime(Long createdAt) {
        if (createdAt == null || createdAt <= 0) {
            return "";
        }

        long now = System.currentTimeMillis();
        long diff = Math.max(0L, now - createdAt);

        long minutes = diff / (60 * 1000L);
        long hours = diff / (60 * 60 * 1000L);
        long days = diff / (24 * 60 * 60 * 1000L);

        if (minutes < 1) {
            return "Opened just now";
        }

        if (hours < 1) {
            return "Opened " + minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }

        if (days < 1) {
            return "Opened " + hours + (hours == 1 ? " hour ago" : " hours ago");
        }

        if (days == 1) {
            return "Opened yesterday";
        }

        return "Opened " + days + " days ago";
    }

}