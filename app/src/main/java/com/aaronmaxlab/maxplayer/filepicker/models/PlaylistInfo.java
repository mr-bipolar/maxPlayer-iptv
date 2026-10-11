package com.aaronmaxlab.maxplayer.filepicker.models;

import java.util.ArrayList;
import java.util.List;

public class PlaylistInfo {

    public String fileName = "";
    public String format = "Unknown";
    public String sourceFolder = "Unknown";
    public long fileSize = -1;

    public int channelCount = 0;
    public List<String> categoryNames = new ArrayList<>();

    public boolean isPlaylist = false;
    public boolean isHlsManifest = false;
}
