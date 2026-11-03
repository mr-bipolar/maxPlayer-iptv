package com.aaronmaxlab.maxplayer.database.models;


public class LocalDbPlaylist {

    private final Integer id;
    private final String playlistName;
    private final String playlistUrl;
    private final int playlistCount;
    private final Long createdAt;

    public LocalDbPlaylist(
            Integer id,
            String playlistName,
            String playlistUrl,
            int playlistCount,
            Long createdAt
    ) {
        this.id = id;
        this.playlistName = playlistName;
        this.playlistUrl = playlistUrl;
        this.playlistCount = playlistCount;
        this.createdAt = createdAt;
    }

    public LocalDbPlaylist(
            String playlistName,
            String playlistUrl,
            int playlistCount
    ) {
        this(null, playlistName, playlistUrl, playlistCount, null);
    }

    public Integer getId() {
        return id;
    }

    public String getPlaylistName() {
        return playlistName;
    }

    public String getPlaylistUrl() {
        return playlistUrl;
    }

    public int getPlaylistCount() {
        return playlistCount;
    }

    public Long getCreatedAt() {
        return createdAt;
    }
}
