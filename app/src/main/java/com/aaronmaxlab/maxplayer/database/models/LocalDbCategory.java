package com.aaronmaxlab.maxplayer.database.models;

public class LocalDbCategory {

    private final Integer id;
    private final String  categoryName;
    private final Integer categoryCount;
    private final String categoryIcon;


    public  LocalDbCategory (
         Integer id,
         String  categoryName,
         Integer categoryCount,
         String categoryIcon
    ) {
        this.id = id;
        this.categoryName = categoryName;
        this.categoryCount = categoryCount;
        this.categoryIcon = categoryIcon;
    }


  public   Integer getId() { return  id;}
  public   String getCategoryName() { return  categoryName;}
  public   Integer getCategoryCount() { return  categoryCount;}
  public   String getCategoryIcon() { return  categoryIcon;}

}
