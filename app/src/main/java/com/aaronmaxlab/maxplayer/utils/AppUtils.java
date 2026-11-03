package com.aaronmaxlab.maxplayer.utils;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;

import com.aaronmaxlab.maxplayer.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppUtils {

    public static void showDeleteConfirmation(
            Context context,
            String itemName,
            Runnable onConfirm
    ) {
        if (context == null) return;

        new AlertDialog.Builder(context, R.style.PlayerDialogTheme)
                .setTitle("Delete item?")
                .setMessage("Are you sure you want to delete \"" + itemName + "\"?")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Delete", (dialog, which) -> onConfirm.run())
                .show();
    }

    public static List<String> showCategories(){
        return  new ArrayList<>(Arrays.asList(
                "Live TV", "Sports", "News", "Movies",
                "Series", "Kids", "Documentary",
                "Entertainment", "Music", "Radio", "Talk",
                "Lifestyle", "Education", "Religious",
                "Regional", "International", "Other"
        ));
    }

    // Icons for category
    public static int getCategoryIcon(String iconName) {
        switch (iconName) {
            case "ic_tv":
                return R.drawable.ic_tv;
            case "ic_cat_sport":
                return R.drawable.ic_cat_sport;
            case "ic_cat_news":
                return R.drawable.ic_cat_news;
            case "ic_cat_movies":
                return R.drawable.ic_cat_movies;
            case "ic_series":
                return R.drawable.ic_series;
            case "ic_cat_kids":
                return R.drawable.ic_cat_kids;
            case "ic_documentary":
                return R.drawable.ic_documentary;
            case "ic_entertainment":
                return R.drawable.ic_entertainment;
            case "ic_music":
                return R.drawable.ic_music;
            case "ic_radio":
                return R.drawable.ic_radio;
            case "ic_talk":
                return R.drawable.ic_talk;
            case "ic_life_style":
                return R.drawable.ic_life_style;
            case "ic_education":
                return R.drawable.ic_education;
            case "ic_religious":
                return R.drawable.ic_religious;
            case "ic_regional":
                return R.drawable.ic_regional;
            case "ic_international":
                return R.drawable.ic_international;
            case "ic_18_plus":
                return R.drawable.ic_18_plus;
            default:
                return R.drawable.ic_other;
        }
    }
}
