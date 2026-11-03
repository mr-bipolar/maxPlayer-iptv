package com.aaronmaxlab.maxplayer.playlist.models;

import java.util.ArrayList;
import java.util.List;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PlaylistInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    public String fileName = "";
    public String format = "Unknown";
    public String sourceFolder = "Unknown";
    public long fileSize = -1;

    public int channelCount = 0;
    public List<String> categoryNames = new ArrayList<>();
    public List<PlaylistChannel> channels = new ArrayList<>();

    public boolean isPlaylist = false;
    public boolean isHlsManifest = false;
}