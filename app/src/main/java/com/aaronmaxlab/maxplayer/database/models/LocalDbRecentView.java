package com.aaronmaxlab.maxplayer.database.models;

import com.aaronmaxlab.maxplayer.utils.PlaylistType;

public class LocalDbRecentView {


    private final Integer id;
    private final String channelName;
    private final String channelUrl;
    private final String channelIcon;
    private final PlaylistType channelType;
    private final Long createdAt;

    public LocalDbRecentView(
            Integer id,
            String channelName,
            String channelUrl,
            String channelIcon,
            PlaylistType channelType,
            Long createdAt
    ){
        this.id = id;
        this.channelName = channelName;
        this.channelUrl = channelUrl;
        this.channelType = channelType;
        this.channelIcon = channelIcon;
        this.createdAt = createdAt;
    }

    public LocalDbRecentView(
            String channelName,
            String channelUrl,
            PlaylistType channelType,
            String channelIcon
    ){
        this(null, channelName , channelUrl, channelIcon, channelType, null);
    }

    public  Integer getId() { return  id;}

    public String getChannelName() { return  channelName;}

    public String getChannelUrl() { return  channelUrl; }

    public String getChannelType() { return  channelType.getDisplayName(); }

    public String getChannelIcon() { return channelIcon; }

    public Long getCreatedAt() {
        return createdAt;
    }


}
