package com.aaronmaxlab.maxplayer.filepicker;

import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

public class MediaFile {

    public enum Type {
        FOLDER,
        VIDEO,
        AUDIO,
        PLAYLIST
    }

    private final String name;
    private final Uri uri;
    private final Type type;
    private final DocumentFile documentFile;

    public MediaFile(
            String name,
            Uri uri,
            Type type,
            DocumentFile documentFile
    ) {
        this.name = name;
        this.uri = uri;
        this.type = type;
        this.documentFile = documentFile;
    }


    public String getName() {
        return name;
    }

    public Uri getUri() {
        return uri;
    }

    public Type getType() {
        return type;
    }

    public DocumentFile getDocumentFile() {
        return documentFile;
    }

}
