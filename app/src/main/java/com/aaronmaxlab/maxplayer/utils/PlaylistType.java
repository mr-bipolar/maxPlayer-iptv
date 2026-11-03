package com.aaronmaxlab.maxplayer.utils;

public enum PlaylistType {
    PLAYLIST("PLAYLIST"),
    LIVE("LIVE"),
    VOD("VOD"),
    AUDIO("AUDIO");


    private final String displayName;

    PlaylistType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PlaylistType fromDisplayName(String value) {
        for (PlaylistType type : values()) {
            if (type.displayName.equalsIgnoreCase(value)) {
                return type;
            }
        }

        return LIVE; // Default value
    }
}