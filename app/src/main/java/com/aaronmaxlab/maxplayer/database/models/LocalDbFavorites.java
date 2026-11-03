package com.aaronmaxlab.maxplayer.database.models;

import com.aaronmaxlab.maxplayer.utils.PlaylistType;

public class LocalDbFavorites {

    private final Integer id;
    private final String favName;
    private final String favTag;
    private final String favUrl;
    private final String favIcon;
    private final PlaylistType favType;



    public LocalDbFavorites(
            Integer id,
            String favName,
            String favTag,
            String favUrl,
            String favIcon,
            PlaylistType favType
    ){
        this.id = id;
        this.favName = favName;
        this.favUrl = favUrl;
        this.favType = favType;
        this.favIcon = favIcon;
        this.favTag = favTag;
    }

    public  LocalDbFavorites(
            String favName,
            String favTag,
            String favUrl,
            String favIcon,
            PlaylistType favType
    ) {
        this(null, favName, favTag, favUrl, favIcon, favType);
    }

    public  Integer getId(){return  id;}
    public  String getFavName(){ return  favName;}
    public  String getFavTag() { return  favTag;}
    public  String getFavUrl() { return  favUrl;}
    public  String getFavType(){ return  favType.getDisplayName();}
    public  String getFavIcon(){ return  favIcon;}

}
