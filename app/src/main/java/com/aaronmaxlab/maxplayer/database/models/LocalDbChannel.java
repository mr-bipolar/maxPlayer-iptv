package com.aaronmaxlab.maxplayer.database.models;

public class LocalDbChannel {
    private final Integer id;
    private final String  channelName;
    private final String channelImage;
    private final String channelUrl;
    private final Integer channelCategory;

    public  LocalDbChannel(
           Integer id,
           String  channelName,
           String  channelImage,
           String  channelUrl,
           Integer channelCategory
    ){
       this.id = id;
       this.channelName = channelName;
       this.channelImage = channelImage;
       this.channelUrl = channelUrl;
       this.channelCategory = channelCategory;
    }

    public  LocalDbChannel(
            String  channelName,
            String  channelImage,
            String  channelUrl,
            Integer channelCategory
    ){
       this(null, channelName, channelImage, channelUrl, channelCategory);
    }

    public Integer getId() { return  id; }
    public String  getChannelName()  { return  channelName; }
    public String  getChannelImage() { return  channelImage; }
    public String  getChannelUrl()   { return  channelUrl; }
    public Integer getChannelCategory() { return  channelCategory; }
}
