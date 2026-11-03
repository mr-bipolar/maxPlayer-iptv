package com.aaronmaxlab.maxplayer.database.models;

import com.aaronmaxlab.maxplayer.utils.PlaylistType;

public class LocalDbHistory {

    private final Integer id;
    private final String historyName;
    private final String historyTag;
    private final String historyUrl;
    private final String historyIcon;
    private final PlaylistType historyType;
    private final Long historyPosition;
    private final Long historyDuration;
    private final Long createdAt;


    public LocalDbHistory(
            Integer id,
            String historyName,
            String historyTag,
            String historyUrl,
            String historyIcon,
            PlaylistType historyType,
            Long historyPosition,
            Long historyDuration,
            Long createdAt
    ){
        this.id = id;
        this.historyName = historyName;
        this.historyUrl = historyUrl;
        this.historyType = historyType;
        this.historyTag = historyTag;
        this.historyIcon = historyIcon;
        this.historyPosition = historyPosition;
        this.historyDuration = historyDuration;
        this.createdAt = createdAt;
    }

    public  LocalDbHistory(
            String historyName,
            String historyTag,
            String historyUrl,
            String historyIcon,
            PlaylistType historyType,
            Long historyPosition,
            Long historyDuration
    ) {
        this(null, historyName, historyTag, historyUrl, historyIcon, historyType,  historyPosition, historyDuration, null);
    }

    public  Integer getId(){return  id;}
    public  String getHistoryName(){ return  historyName;}
    public  String getHistoryTag() { return  historyTag;}
    public  String getHistoryUrl() { return  historyUrl;}
    public  String getHistoryType(){ return  historyType.getDisplayName();}
    public  String getHistoryIcon(){ return  historyIcon;}
    public  Long getHistoryPosition() { return  historyPosition;}
    public  Long getHistoryDuration() { return  historyDuration;}
    public  Long getCreatedAt() { return  createdAt;}
}
