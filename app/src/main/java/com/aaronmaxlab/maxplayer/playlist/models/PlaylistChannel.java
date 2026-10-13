package com.aaronmaxlab.maxplayer.playlist.models;

import java.io.Serializable;

public class PlaylistChannel implements Serializable {

    private static final long serialVersionUID = 1L;

    public String name = "";
    public String url = "";
    public String category = "";
    public String logoUrl = "";

    public PlaylistChannel(String name, String url, String category) {
        this.name = name;
        this.url = url;
        this.category = category;
    }
}