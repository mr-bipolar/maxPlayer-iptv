package com.aaronmaxlab.maxplayer.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.aaronmaxlab.maxplayer.database.models.LocalDbCategory;
import com.aaronmaxlab.maxplayer.database.models.LocalDbChannel;
import com.aaronmaxlab.maxplayer.database.models.LocalDbFavorites;
import com.aaronmaxlab.maxplayer.database.models.LocalDbHistory;
import com.aaronmaxlab.maxplayer.database.models.LocalDbPlaylist;
import com.aaronmaxlab.maxplayer.database.models.LocalDbRecentView;
import com.aaronmaxlab.maxplayer.utils.AppUtils;
import com.aaronmaxlab.maxplayer.utils.MediaTypes;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SqlDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "m3u8Library.db";
    private static final int DATABASE_VERSION = 7;

    // Tables
    private static final String TABLE_CHANNELS = "channels";
    private static final String TABLE_OLD_CATEGORY = "category";
    private static final String TABLE_CATEGORIES = "categories";
    private static final String TABLE_OLD_PLAYLIST = "playlist";
    private static final String TABLE_PLAYLISTS = "playlists";
    private static final String TABLE_FAVORITES = "favorites";
    private static final String TABLE_HISTORY = "history";
    private static final String TABLE_RECENT_VIEWS = "recentViews";

    // Common columns
    private static final String COL_ID = "id";
    private static final String COL_CATEGORY = "category";
    private static final String COL_COUNT = "count";
    private static final String COL_ICON = "icon";
    private static final String COL_MEDIA_TYPE = "mediaType";

    // Channel columns
    private static final String COL_CHANNEL_NAME = "channelName";
    private static final String COL_CHANNEL_IMAGE = "channelImage";
    private static final String COL_CHANNEL_ICON_OLD = "channelIcon";
    private static final String COL_CHANNEL_URL = "channelUrl";

    // Playlist columns
    private static final String COL_PLAYLIST_NAME = "playlistName";
    private static final String COL_PLAYLIST_URL = "playlistUrl";
    private static final String COL_PLAYLIST_COUNT = "playlistCount";
    private static final String COL_CREATED_AT = "createdAt";

    // Favorite columns
    private static final String COL_FAVORITE_NAME = "favoriteName";
    private static final String COL_FAVORITE_TAG = "favoriteTag";
    private static final String COL_FAVORITE_TYPE = "favoriteType";
    private static final String COL_FAVORITE_URL = "favoriteUrl";
    public static final  String COL_FAVORITE_ICON = "favIcon";

    // History columns
    private static final String COL_HISTORY_NAME = "historyName";
    private static final String COL_HISTORY_TAG = "historyTag";
    private static final String COL_HISTORY_POSITION = "historyPlayPosition";
    private static final String COL_HISTORY_DURATION = "historyPlayDuration";
    private static final String COL_HISTORY_URL = "historyUrl";
    private static final String COL_HISTORY_TYPE = "historyType";
    public static final  String COL_HISTORY_ICON = "historyIcon";

    private static final String[][] DEFAULT_CATEGORIES = {

            {"Live TV", "ic_tv"},
            {"Sports", "ic_cat_sport"},
            {"News", "ic_cat_news"},
            {"Movies", "ic_cat_movies"},
            {"Series", "ic_series"},
            {"Kids", "ic_cat_kids"},
            {"Documentary", "ic_documentary"},
            {"Entertainment", "ic_entertainment"},
            {"Music", "ic_music"},
            {"Radio", "ic_radio"},
            {"Talk", "ic_talk"},
            {"Lifestyle", "ic_life_style"},
            {"Education", "ic_education"},
            {"Religious", "ic_religious"},
            {"Regional", "ic_regional"},
            {"International", "ic_international"},
            {"18+", "ic_18_plus"},
            {"Podcast", "ic_podcasts"},
            {"MP3", "ic_audio"},
            {"Other", "ic_other"}
    };



    private final Context context;

    public SqlDbHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createCategoriesTable(db);
        createChannelsTable(db, TABLE_CHANNELS);
        createPlaylistsTable(db, TABLE_PLAYLISTS);
        createFavoritesTable(db);
        createHistoryTable(db);
        createRecentViewsTable(db);
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion
    ) {
        if (oldVersion < 3) {
            migrateToVersion3(db);
        }

        if (oldVersion < 4) {
            migrateToVersion4(db);
        }

        if (oldVersion < 5) {
            migrateToVersion5(db);
        }

        if (oldVersion < 6) {
            migrateToVersion6(db);
        }

        if (oldVersion < 7) {
            migrateToVersion7(db);
        }
    }

    private void migrateToVersion3(SQLiteDatabase db) {
        // Create the new category table and standard categories first.
        createCategoriesTable(db);
        seedCategories(db);

        // The v1 -> v2 upgrade is not necessarily run separately,
        // so make sure the old playlist table exists.
        if (!tableExists(db, TABLE_OLD_PLAYLIST)) {
            db.execSQL(
                    "CREATE TABLE " + TABLE_OLD_PLAYLIST + " (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "playlistName TEXT, " +
                            "playlistUrl TEXT UNIQUE, " +
                            "playlistCount INTEGER)"
            );
        }

        // Preserve old category names that are not in the new list.
        if (tableExists(db, TABLE_OLD_CATEGORY)) {
            try (Cursor cursor = db.rawQuery(
                    "SELECT " + COL_CATEGORY +
                            " FROM " + TABLE_OLD_CATEGORY,
                    null
            )) {
                while (cursor.moveToNext()) {
                    String category = cursor.getString(0);
                    if (category != null && !category.trim().isEmpty()) {
                        insertCategoryIfMissing(db, category.trim());
                    }
                }
            }
        }

        // Rebuild channels to change category from TEXT to INTEGER
        // and channelIcon to channelImage.
        migrateChannels(db);

        // Migrate playlists without inventing historical timestamps.
        migratePlaylists(db);

        // Create the remaining tables for the new version.
        createFavoritesTable(db);
        createHistoryTable(db);
        createRecentViewsTable(db);

        // Drop legacy tables only after migration steps succeed.
        if (tableExists(db, TABLE_OLD_CATEGORY)) {
            db.execSQL("DROP TABLE " + TABLE_OLD_CATEGORY);
        }

        if (tableExists(db, TABLE_OLD_PLAYLIST)) {
            db.execSQL("DROP TABLE " + TABLE_OLD_PLAYLIST);
        }
    }


    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        seedCategories(db);
    }

    private void logRecentViewsColumns(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery(
                "PRAGMA table_info(recentViews)", null)) {

            StringBuilder columns = new StringBuilder();

            while (cursor.moveToNext()) {
                columns.append(cursor.getString(1)).append(", ");
            }

            Log.e("DB_SCHEMA", "Database: " + db.getPath());
            Log.e("DB_SCHEMA", "recentViews columns: " + columns);
        }
    }

    // VERSION 4
    private void migrateToVersion4(SQLiteDatabase db) {

        logRecentViewsColumns(db);

        if (!tableExists(db, TABLE_RECENT_VIEWS)) {
            createRecentViewsTable(db);
            return;
        }

        if (!columnExists(db, TABLE_RECENT_VIEWS, COL_MEDIA_TYPE)) {
            db.execSQL(
                    "ALTER TABLE " + TABLE_RECENT_VIEWS +
                            " ADD COLUMN " + COL_MEDIA_TYPE + " TEXT"
            );
        }

        if (!columnExists(db, TABLE_RECENT_VIEWS, COL_CREATED_AT)) {
            db.execSQL(
                    "ALTER TABLE " + TABLE_RECENT_VIEWS +
                            " ADD COLUMN " + COL_CREATED_AT + " INTEGER"
            );
        }

        logRecentViewsColumns(db);
    }

    // VERSION 5
    private void migrateToVersion5(SQLiteDatabase db) {
        if (!tableExists(db, TABLE_RECENT_VIEWS)) {
            createRecentViewsTable(db);
            return;
        }

        if (!columnExists(db, TABLE_RECENT_VIEWS, COL_MEDIA_TYPE)) {
            db.execSQL(
                    "ALTER TABLE " + TABLE_RECENT_VIEWS +
                            " ADD COLUMN " + COL_MEDIA_TYPE + " TEXT"
            );
        }

        if (!columnExists(db, TABLE_RECENT_VIEWS, COL_CREATED_AT)) {
            db.execSQL(
                    "ALTER TABLE " + TABLE_RECENT_VIEWS +
                            " ADD COLUMN " + COL_CREATED_AT + " INTEGER"
            );
        }

        Log.e("DB_SCHEMA", "recentViews migration to version 5 completed");
    }

    // VERSION 6
    private void migrateToVersion6(SQLiteDatabase db) {
        if (!tableExists(db, TABLE_HISTORY)) {
            createHistoryTable(db);
            return;
        }

        if (!columnExists(db, TABLE_HISTORY, COL_HISTORY_DURATION)) {
            db.execSQL(
                    "ALTER TABLE " + TABLE_HISTORY +
                            " ADD COLUMN " + COL_HISTORY_DURATION +
                            " INTEGER DEFAULT 0"
            );
        }

        Log.e("DB_SCHEMA", "History migration to version 6 completed");
    }

    // VERSION 7
    private void migrateToVersion7(SQLiteDatabase db) {

        if (!columnExists(db, TABLE_FAVORITES, COL_FAVORITE_ICON)) {
            db.execSQL("ALTER TABLE " + TABLE_FAVORITES +
                    " ADD COLUMN " + COL_FAVORITE_ICON + " TEXT");
        }

        if (!columnExists(db, TABLE_HISTORY, COL_HISTORY_ICON)) {
            db.execSQL("ALTER TABLE " + TABLE_HISTORY +
                    " ADD COLUMN " + COL_HISTORY_ICON + " TEXT");
        }

        if (!columnExists(db, TABLE_RECENT_VIEWS, COL_ICON)) {
            db.execSQL("ALTER TABLE " + TABLE_RECENT_VIEWS +
                    " ADD COLUMN " + COL_ICON + " TEXT");
        }

        Log.d("DB_SCHEMA", "Database migration to version 7 completed");
    }

    // CREATE CATEGORIES TABLE
    private void createCategoriesTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_CATEGORIES + " (" +
                        COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_CATEGORY + " TEXT NOT NULL COLLATE NOCASE UNIQUE, " +
                        COL_COUNT + " INTEGER DEFAULT 0, " +
                        COL_ICON + " TEXT)"
        );
    }

    // SEED CATEGORIES
    private void seedCategories(SQLiteDatabase db) {

        for (String[] item : DEFAULT_CATEGORIES) {

            ContentValues values = new ContentValues();

            values.put(COL_CATEGORY, item[0]);
            values.put(COL_COUNT, 0);
            values.put(COL_ICON, item[1]);

            db.insertWithOnConflict(
                    TABLE_CATEGORIES,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
            );
        }
    }

    private String getCategoryIcon(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "ic_other";
        }
        for (String[] item : DEFAULT_CATEGORIES) {
            if (item[0].equalsIgnoreCase(category.trim())) {
                return item[1];
            }
        }
        return "ic_other";
    }


    // INSERT MISSING CATEGORY
    private void insertCategoryIfMissing(
            SQLiteDatabase db,
            String category
    ) {

        if (category == null || category.trim().isEmpty()) {
            return;
        }

        String categoryName = category.trim();

        ContentValues values = new ContentValues();

        values.put(COL_CATEGORY, categoryName);
        values.put(COL_COUNT, 0);
        values.put(COL_ICON, getCategoryIcon(categoryName));

        db.insertWithOnConflict(
                TABLE_CATEGORIES,
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );
    }

    // CHANNELS TABLE
    private void createChannelsTable(SQLiteDatabase db, String tableName) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                        COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_CHANNEL_NAME + " TEXT, " +
                        COL_CHANNEL_IMAGE + " TEXT, " +
                        COL_CHANNEL_URL + " TEXT UNIQUE, " +
                        COL_CATEGORY + " INTEGER)"
        );
    }

    // MIGRATE CHANNELS
    private void migrateChannels(SQLiteDatabase db) {
        if (!tableExists(db, TABLE_CHANNELS)) {
            createChannelsTable(db, TABLE_CHANNELS);
            return;
        }

        // Keep the old table until all rows have been copied.
        db.execSQL("DROP TABLE IF EXISTS channels_v3");
        createChannelsTable(db, "channels_v3");

        boolean hasOldIcon = columnExists(
                db, TABLE_CHANNELS, COL_CHANNEL_ICON_OLD
        );
        boolean hasNewImage = columnExists(
                db, TABLE_CHANNELS, COL_CHANNEL_IMAGE
        );

        String imageColumn = hasOldIcon
                ? COL_CHANNEL_ICON_OLD
                : hasNewImage ? COL_CHANNEL_IMAGE : null;

        String imageExpression = imageColumn == null
                ? "NULL"
                : "old." + imageColumn;

        String categoryExpression =
                "COALESCE(cat.id, otherCat.id)";

        db.execSQL(
                "INSERT INTO channels_v3 (" +
                        COL_ID + ", " +
                        COL_CHANNEL_NAME + ", " +
                        COL_CHANNEL_IMAGE + ", " +
                        COL_CHANNEL_URL + ", " +
                        COL_CATEGORY +
                        ") SELECT " +
                        "old." + COL_ID + ", " +
                        "old." + COL_CHANNEL_NAME + ", " +
                        imageExpression + ", " +
                        "old." + COL_CHANNEL_URL + ", " +
                        categoryExpression +
                        " FROM " + TABLE_CHANNELS + " old " +
                        "LEFT JOIN " + TABLE_CATEGORIES + " cat " +
                        "ON LOWER(TRIM(cat." + COL_CATEGORY + ")) = " +
                        "LOWER(TRIM(old." + COL_CATEGORY + ")) " +
                        "LEFT JOIN " + TABLE_CATEGORIES + " otherCat " +
                        "ON LOWER(otherCat." + COL_CATEGORY + ") = 'other'"
        );

        db.execSQL("DROP TABLE " + TABLE_CHANNELS);
        db.execSQL(
                "ALTER TABLE channels_v3 RENAME TO " + TABLE_CHANNELS
        );
    }

    // CREATE PLAYLIST TABLE
    private void createPlaylistsTable(SQLiteDatabase db, String tableName) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                        COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_PLAYLIST_NAME + " TEXT, " +
                        COL_PLAYLIST_URL + " TEXT UNIQUE, " +
                        COL_PLAYLIST_COUNT + " INTEGER, " +
                        COL_CREATED_AT + " INTEGER)"
        );
    }

    // MIGRATE PLAYLISTS
    private void migratePlaylists(SQLiteDatabase db) {
        createPlaylistsTable(db, TABLE_PLAYLISTS);

        if (!tableExists(db, TABLE_OLD_PLAYLIST)) {
            return;
        }

        db.execSQL(
                "INSERT OR IGNORE INTO " + TABLE_PLAYLISTS + " (" +
                        COL_ID + ", " +
                        COL_PLAYLIST_NAME + ", " +
                        COL_PLAYLIST_URL + ", " +
                        COL_PLAYLIST_COUNT + ", " +
                        COL_CREATED_AT +
                        ") SELECT " +
                        COL_ID + ", " +
                        COL_PLAYLIST_NAME + ", " +
                        COL_PLAYLIST_URL + ", " +
                        COL_PLAYLIST_COUNT + ", " +
                        "NULL FROM " + TABLE_OLD_PLAYLIST
        );
    }

    // FAVORITES TABLE
    private void createFavoritesTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_FAVORITES + " (" +
                        COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_FAVORITE_NAME + " TEXT, " +
                        COL_FAVORITE_TAG + " TEXT, " +
                        COL_FAVORITE_TYPE + " TEXT, " +
                        COL_FAVORITE_ICON + " TEXT, " +
                        COL_FAVORITE_URL + " TEXT UNIQUE)"
        );
    }

    // HISTORY TABLE
    private void createHistoryTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_HISTORY + " (" +
                        COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_HISTORY_NAME + " TEXT, " +
                        COL_HISTORY_TAG + " TEXT, " +
                        COL_HISTORY_TYPE + " TEXT, " +
                        COL_HISTORY_POSITION + " INTEGER DEFAULT 0, " +
                        COL_HISTORY_DURATION + " INTEGER DEFAULT 0, " +
                        COL_HISTORY_URL + " TEXT UNIQUE, " +
                        COL_HISTORY_ICON + " TEXT, " +
                        COL_CREATED_AT + " INTEGER)"
        );
    }

    // RECENT VIEWS TABLE
    private void createRecentViewsTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_RECENT_VIEWS + " (" +
                        COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_CHANNEL_NAME + " TEXT, " +
                        COL_MEDIA_TYPE + " TEXT, " +
                        COL_ICON + " TEXT, " +
                        COL_CHANNEL_URL + " TEXT UNIQUE, " +
                        COL_CREATED_AT + " INTEGER)"
        );
    }

    // CHECK TABLE EXISTS
    private boolean tableExists(SQLiteDatabase db, String tableName) {
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM sqlite_master " +
                        "WHERE type = 'table' AND name = ?",
                new String[]{tableName}
        )) {
            return cursor.moveToFirst();
        }
    }

    // CHECK COLUMN EXISTS
    private boolean columnExists(
            SQLiteDatabase db,
            String tableName,
            String columnName
    ) {
        try (Cursor cursor = db.rawQuery(
                "PRAGMA table_info(" + tableName + ")",
                null
        )) {
            while (cursor.moveToNext()) {
                if (columnName.equalsIgnoreCase(cursor.getString(1))) {
                    return true;
                }
            }
        }
        return false;
    }

    // ============================================
    // Database functions playlists
    public boolean addPlaylist(LocalDbPlaylist playlist) {

        try (SQLiteDatabase database = this.getWritableDatabase()) {
            ContentValues cv = new ContentValues();

            cv.put(COL_PLAYLIST_NAME, playlist.getPlaylistName());
            cv.put(COL_PLAYLIST_URL, playlist.getPlaylistUrl());
            cv.put(COL_PLAYLIST_COUNT, playlist.getPlaylistCount());

            long result = database.insertWithOnConflict(
                    TABLE_PLAYLISTS,
                    null,
                    cv,
                    SQLiteDatabase.CONFLICT_IGNORE
            );

            return result != -1;
        }
    }

    public List<LocalDbPlaylist> getAllPlaylists() {

        List<LocalDbPlaylist> playlistList = new ArrayList<>();

        try (SQLiteDatabase database = this.getReadableDatabase();
             Cursor cursor = database.query(
                     TABLE_PLAYLISTS,
                     null,
                     null,
                     null,
                     null,
                     null,
                     null)) {

            if (cursor.moveToFirst()) {
                do {

                    int playlistId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
                    String playlistName = cursor.getString(cursor.getColumnIndexOrThrow(COL_PLAYLIST_NAME));
                    String playlistUrl = cursor.getString(cursor.getColumnIndexOrThrow(COL_PLAYLIST_URL));
                    int playlistCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PLAYLIST_COUNT));
                    Long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT));

                    playlistList.add(new LocalDbPlaylist(playlistId, playlistName , playlistUrl, playlistCount, createdAt));

                } while (cursor.moveToNext());
            }
        }

        return playlistList;
    }

    public boolean updatePlaylist(LocalDbPlaylist playlist) {
        if (playlist == null || playlist.getId() == null) {
            return false;
        }

        SQLiteDatabase database = this.getWritableDatabase();
        try {
            database.beginTransaction();

            String oldUrl = null;
            try (Cursor cursor = database.query(
                    TABLE_PLAYLISTS,
                    new String[]{COL_PLAYLIST_URL},
                    COL_ID + "=?",
                    new String[]{String.valueOf(playlist.getId())},
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    oldUrl = cursor.getString(0);
                }
            }

            ContentValues cv = new ContentValues();
            cv.put(COL_PLAYLIST_NAME, playlist.getPlaylistName());
            cv.put(COL_PLAYLIST_URL, playlist.getPlaylistUrl());
            cv.put(COL_PLAYLIST_COUNT, playlist.getPlaylistCount());

            int result = database.update(
                    TABLE_PLAYLISTS,
                    cv,
                    COL_ID + "=?",
                    new String[]{String.valueOf(playlist.getId())}
            );

            if (result == 0) {
                return false;
            }

            if (oldUrl != null && !oldUrl.trim().isEmpty()) {
                String newUrl = playlist.getPlaylistUrl();
                String newName = playlist.getPlaylistName();

                ContentValues favCv = new ContentValues();
                if (newName != null) favCv.put(COL_FAVORITE_NAME, newName);
                if (newUrl != null && !newUrl.trim().isEmpty()) {
                    favCv.put(COL_FAVORITE_URL, newUrl.trim());
                }
                database.update(TABLE_FAVORITES, favCv, COL_FAVORITE_URL + "=?", new String[]{oldUrl.trim()});

                ContentValues histCv = new ContentValues();
                if (newName != null) histCv.put(COL_HISTORY_NAME, newName);
                if (newUrl != null && !newUrl.trim().isEmpty()) {
                    histCv.put(COL_HISTORY_URL, newUrl.trim());
                }
                database.update(TABLE_HISTORY, histCv, COL_HISTORY_URL + "=?", new String[]{oldUrl.trim()});
            }

            database.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            Log.e("PLAYER_DB", "Playlist update failed", e);
            return false;
        } finally {
            if (database.inTransaction()) {
                database.endTransaction();
            }
        }
    }

    public void lastOpenUpdate(int playlistId) {
        try (SQLiteDatabase database = this.getWritableDatabase()) {
            ContentValues cv = new ContentValues();
            cv.put(COL_CREATED_AT, System.currentTimeMillis());

            database.update(
                    TABLE_PLAYLISTS,
                    cv,
                    COL_ID + "=?",
                    new String[]{String.valueOf(playlistId)}
            );

        }
    }

    public boolean deletePlaylist(int playlistId) {
        SQLiteDatabase database = this.getWritableDatabase();
        try {
            database.beginTransaction();

            String oldUrl = null;
            try (Cursor cursor = database.query(
                    TABLE_PLAYLISTS,
                    new String[]{COL_PLAYLIST_URL},
                    COL_ID + "=?",
                    new String[]{String.valueOf(playlistId)},
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    oldUrl = cursor.getString(0);
                }
            }

            int result = database.delete(
                    TABLE_PLAYLISTS,
                    COL_ID + "=?",
                    new String[]{String.valueOf(playlistId)}
            );

            if (result == 0) {
                return false;
            }

            if (oldUrl != null && !oldUrl.trim().isEmpty()) {
                database.delete(TABLE_FAVORITES, COL_FAVORITE_URL + "=?", new String[]{oldUrl.trim()});
                database.delete(TABLE_HISTORY, COL_HISTORY_URL + "=?", new String[]{oldUrl.trim()});
            }

            database.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            Log.e("PLAYER_DB", "Playlist delete failed", e);
            return false;
        } finally {
            if (database.inTransaction()) {
                database.endTransaction();
            }
        }
    }

    // ============================================
    // Database functions recent views
    // ============================================
    public void setRecentViews(LocalDbRecentView recentViews) {
        try (SQLiteDatabase database = this.getWritableDatabase()) {

            ContentValues cv = new ContentValues();

            cv.put(COL_CHANNEL_NAME, recentViews.getChannelName());
            cv.put(COL_MEDIA_TYPE, recentViews.getChannelType());
            cv.put(COL_CHANNEL_URL, recentViews.getChannelUrl());
            cv.put(COL_CREATED_AT, System.currentTimeMillis());
            cv.put(COL_ICON, recentViews.getChannelIcon());

            String where = COL_CHANNEL_URL + "=?";
            String[] args = {recentViews.getChannelUrl()};

            int updated = database.update(
                    TABLE_RECENT_VIEWS,
                    cv,
                    where,
                    args
            );

            if (updated > 0) {
                Log.d("LOCAL_DB", "Recent view updated.");
            } else {
                long result = database.insert(
                        TABLE_RECENT_VIEWS,
                        null,
                        cv
                );

                if (result != -1) {
                    Log.d("LOCAL_DB", "Recent view inserted.");
                } else {
                    Log.e("LOCAL_DB", "Recent view insert failed.");
                }
            }
        } catch (Exception e) {
            Log.e("LOCAL_DB", "Recent view error.", e);
        }
    }

    public List<LocalDbRecentView> getAllRecentViews(PlaylistType type) {

        List<LocalDbRecentView> recentViewList = new ArrayList<>();

        String selection = COL_MEDIA_TYPE + " = ?";
        String[] selectionArgs = { type.getDisplayName() };

        try (SQLiteDatabase database = this.getReadableDatabase();
             Cursor cursor = database.query(
                     TABLE_RECENT_VIEWS,
                     null,
                     selection,
                     selectionArgs,
                     null,
                     null,
                     COL_CREATED_AT + " DESC"
             )) {

            while (cursor.moveToNext()) {
                int channelId = cursor.getInt(
                        cursor.getColumnIndexOrThrow(COL_ID));

                String channelName = cursor.getString(
                        cursor.getColumnIndexOrThrow(COL_CHANNEL_NAME));

                String channelUrl = cursor.getString(
                        cursor.getColumnIndexOrThrow(COL_CHANNEL_URL));

                String mediaType = cursor.getString(
                        cursor.getColumnIndexOrThrow(COL_MEDIA_TYPE));

                String channelIcon = cursor.getString(
                        cursor.getColumnIndexOrThrow(COL_ICON));

                long createdAt = cursor.getLong(
                        cursor.getColumnIndexOrThrow(COL_CREATED_AT));

                PlaylistType playlistType =
                        PlaylistType.fromDisplayName(mediaType);

                recentViewList.add(new LocalDbRecentView(
                        channelId,
                        channelName,
                        channelUrl,
                        channelIcon,
                        playlistType,
                        createdAt
                ));
            }
        }

        return recentViewList;
    }

    public boolean removeRecentItem(int itemId){
        try (SQLiteDatabase database = this.getWritableDatabase()) {
            int result = database.delete(
                    TABLE_RECENT_VIEWS,
                    COL_ID + "=?",
                    new String[]{String.valueOf(itemId)}
            );

            return result > 0;
        }
    }

    // ============================================
    // Database functions favorites

    public boolean setFavorite(LocalDbFavorites favorites) {
        try (SQLiteDatabase database = this.getWritableDatabase()) {

            String url = favorites.getFavUrl();

            // Check whether this URL is already a favorite.
            boolean exists;

            try (Cursor cursor = database.query(
                    TABLE_FAVORITES,
                    new String[]{COL_FAVORITE_URL},
                    COL_FAVORITE_URL + " = ?",
                    new String[]{url},
                    null,
                    null,
                    null
            )) {
                exists = cursor.moveToFirst();
            }

            // If it exists, remove it.
            if (exists) {
              return  removeFav(url);
            }

            // Otherwise, add it.
            ContentValues cv = new ContentValues();
            cv.put(COL_FAVORITE_NAME, favorites.getFavName());
            cv.put(COL_FAVORITE_TAG, favorites.getFavTag());
            cv.put(COL_FAVORITE_TYPE, favorites.getFavType());
            cv.put(COL_FAVORITE_ICON, favorites.getFavIcon());
            cv.put(COL_FAVORITE_URL, url);

            long result = database.insertWithOnConflict(
                    TABLE_FAVORITES,
                    null,
                    cv,
                    SQLiteDatabase.CONFLICT_IGNORE
            );

            return result != -1;

        } catch (Exception e) {
            Log.e("FAV", "Failed to toggle favorite", e);
            return false;
        }
    }

    public boolean isFavorite(String url) {
        try (SQLiteDatabase database = this.getReadableDatabase();
             Cursor cursor = database.query(
                     TABLE_FAVORITES,
                     new String[]{COL_FAVORITE_URL},
                     COL_FAVORITE_URL + " = ?",
                     new String[]{url},
                     null,
                     null,
                     null
             )) {

            return cursor.moveToFirst();
        }
    }


    public boolean removeFav(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        SQLiteDatabase database = this.getWritableDatabase();

        int rowsDeleted = database.delete(
                TABLE_FAVORITES,
                COL_FAVORITE_URL + " = ?",
                new String[]{url.trim()}
        );

        return rowsDeleted > 0;
    }


    public List<LocalDbFavorites> getAllFavorites() {
        List<LocalDbFavorites> favoritesList = new ArrayList<>();

        SQLiteDatabase database = this.getReadableDatabase();

        try (Cursor cursor = database.query(
                TABLE_FAVORITES,
                null,
                null,
                null,
                null,
                null,
                COL_ID + " DESC"
        )) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(
                        cursor.getColumnIndexOrThrow(COL_FAVORITE_NAME));

                String tag = cursor.getString(
                        cursor.getColumnIndexOrThrow(COL_FAVORITE_TAG));

                String type = cursor.getString(
                        cursor.getColumnIndexOrThrow(COL_FAVORITE_TYPE));

                String url = cursor.getString(
                        cursor.getColumnIndexOrThrow(COL_FAVORITE_URL));

                String icon = cursor.getString(
                        cursor.getColumnIndexOrThrow(COL_FAVORITE_ICON));

                PlaylistType playlistType =
                        PlaylistType.fromDisplayName(type);

                favoritesList.add(new LocalDbFavorites(
                        name, tag, url, icon, playlistType
                ));
            }
        }

        return favoritesList;
    }

    // ============================================
    // Database functions history

    public void saveHistory(LocalDbHistory dbHistory) {
        try (SQLiteDatabase database = this.getWritableDatabase()) {
            ContentValues values = new ContentValues();

            values.put(COL_HISTORY_NAME, dbHistory.getHistoryName());
            values.put(COL_HISTORY_TAG, dbHistory.getHistoryTag());
            values.put(COL_HISTORY_URL, dbHistory.getHistoryUrl());
            values.put(COL_HISTORY_TYPE, dbHistory.getHistoryType());
            values.put(COL_HISTORY_ICON, dbHistory.getHistoryIcon());
            values.put(COL_HISTORY_POSITION, dbHistory.getHistoryPosition());
            values.put(COL_HISTORY_DURATION, dbHistory.getHistoryDuration());
            values.put(COL_CREATED_AT, System.currentTimeMillis());

            long result = database.insertWithOnConflict(
                    TABLE_HISTORY,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
            );

            if (result != -1) {
                Log.d("LOCAL_DB", "History saved.");
            } else {
                Log.d("LOCAL_DB", "History already exists or insert failed.");
            }
        }
    }

    public void updateHistoryPosition(String url, Long position, Long duration) {

        if (url == null || url.isEmpty()) {
            Log.w("LOCAL_DB", "Cannot update history: URL is empty.");
            return;
        }

        try (SQLiteDatabase database = this.getWritableDatabase()) {
            ContentValues values = new ContentValues();

            values.put(COL_HISTORY_POSITION, position);
            values.put(COL_HISTORY_DURATION, duration);

            int rows = database.update(
                    TABLE_HISTORY,
                    values,
                    COL_HISTORY_URL + " = ?",
                    new String[]{url}
            );

        }
    }



    public List<LocalDbHistory> getAllHistory() {
        List<LocalDbHistory> historyList = new ArrayList<>();

        SQLiteDatabase database = this.getReadableDatabase();

        try (Cursor cursor = database.query(
                TABLE_HISTORY,
                null,
                null,
                null,
                null,
                null,
                COL_CREATED_AT + " DESC"
        )) {
            int idIndex = cursor.getColumnIndexOrThrow(COL_ID);
            int nameIndex = cursor.getColumnIndexOrThrow(COL_HISTORY_NAME);
            int tagIndex = cursor.getColumnIndexOrThrow(COL_HISTORY_TAG);
            int urlIndex = cursor.getColumnIndexOrThrow(COL_HISTORY_URL);
            int typeIndex = cursor.getColumnIndexOrThrow(COL_HISTORY_TYPE);
            int iconIndex = cursor.getColumnIndexOrThrow(COL_HISTORY_ICON);
            int positionIndex = cursor.getColumnIndexOrThrow(COL_HISTORY_POSITION);
            int durationIndex = cursor.getColumnIndexOrThrow(COL_HISTORY_DURATION);
            int createdAtIndex = cursor.getColumnIndexOrThrow(COL_CREATED_AT);

            while (cursor.moveToNext()) {
                LocalDbHistory item = new LocalDbHistory(
                        cursor.getInt(idIndex),
                        cursor.getString(nameIndex),
                        cursor.getString(tagIndex),
                        cursor.getString(urlIndex),
                        cursor.getString(iconIndex),
                        PlaylistType.fromDisplayName(cursor.getString(typeIndex)),
                        cursor.getLong(positionIndex),
                        cursor.getLong(durationIndex),
                        cursor.getLong(createdAtIndex)
                );

                historyList.add(item);
            }
        }

        return historyList;
    }

    public boolean clearHistory() {
        try {
            SQLiteDatabase db = getWritableDatabase();

            if (!tableExists(db, TABLE_HISTORY)) {
                createHistoryTable(db);
                return true;
            }

            db.delete(TABLE_HISTORY, null, null);
            return true;
        } catch (Exception e) {
            Log.w("LOCAL_DB", "Cannot delete history: " + e.getMessage());
            return  false;
        }
    }

    public boolean isHistoryExists(String url) {
        try (SQLiteDatabase database = this.getReadableDatabase();
             Cursor cursor = database.query(
                     TABLE_HISTORY,
                     new String[]{COL_HISTORY_URL},
                     COL_HISTORY_URL + " = ?",
                     new String[]{url},
                     null,
                     null,
                     null
             )) {
            return cursor.moveToFirst();
        }
    }

    public boolean isHistoryTypeMatch(String url, PlaylistType type) {
        try (SQLiteDatabase database = getReadableDatabase();
             Cursor cursor = database.query(
                     TABLE_HISTORY,
                     new String[]{COL_HISTORY_TYPE},
                     COL_HISTORY_URL + " = ? AND " + COL_HISTORY_TYPE + " = ?",
                     new String[]{url, type.getDisplayName()},
                     null,
                     null,
                     null
             )) {
            return cursor.moveToFirst();
        }
    }

    public boolean updateHistoryType(String url, PlaylistType type) {
        if (url == null || url.isEmpty() || type == null) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COL_HISTORY_TYPE, type.getDisplayName());

        int rows = db.update(
                TABLE_HISTORY,
                values,
                COL_HISTORY_URL + " = ?",
                new String[]{url}
        );

        return rows > 0;
    }

    // ============================================
    // Database functions channel
    public boolean addChannel(LocalDbChannel dbChannel) {
        SQLiteDatabase database = this.getWritableDatabase();

        try {
            ContentValues values = new ContentValues();

            values.put(COL_CHANNEL_NAME, dbChannel.getChannelName());
            values.put(COL_CHANNEL_IMAGE, dbChannel.getChannelImage());
            values.put(COL_CHANNEL_URL, dbChannel.getChannelUrl());
            values.put(COL_CATEGORY, dbChannel.getChannelCategory());

            database.beginTransaction();

            long result = database.insertWithOnConflict(
                    TABLE_CHANNELS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
            );

            if (result == -1) {
                return false;
            }

            incrementCategoryCount(database, dbChannel.getChannelCategory());

            database.setTransactionSuccessful();
            return true;

        } catch (Exception e) {
            Log.e("PLAYER_DB", "Database insert failed", e);
            return false;

        } finally {
            if (database.inTransaction()) {
                database.endTransaction();
            }
        }
    }

    public List<LocalDbChannel> getChannels(Integer categoryId) {
        List<LocalDbChannel> channels = new ArrayList<>();

        SQLiteDatabase db = getReadableDatabase();

        String selection = null;
        String[] selectionArgs = null;

        // null = all channels
        if (categoryId != null) {
            selection = COL_CATEGORY + " = ?";
            selectionArgs = new String[]{String.valueOf(categoryId)};
        }

        try (Cursor cursor = db.query(
                TABLE_CHANNELS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                COL_CHANNEL_NAME + " COLLATE NOCASE ASC"
        )) {
            while (cursor.moveToNext()) {
                LocalDbChannel channel = new LocalDbChannel(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_CHANNEL_NAME)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_CHANNEL_IMAGE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_CHANNEL_URL)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_CATEGORY))
                );

                channels.add(channel);
            }
        }

        return channels;
    }


    public boolean updateChannel(LocalDbChannel dbChannel, int changeIndex) {
        if (dbChannel == null || dbChannel.getId() == null) {
            return false;
        }

        SQLiteDatabase database = this.getWritableDatabase();

        try {
            database.beginTransaction();

            String oldUrl = null;
            try (Cursor cursor = database.query(
                    TABLE_CHANNELS,
                    new String[]{COL_CHANNEL_URL},
                    COL_ID + " = ?",
                    new String[]{String.valueOf(dbChannel.getId())},
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    oldUrl = cursor.getString(0);
                }
            }

            ContentValues values = new ContentValues();
            values.put(COL_CHANNEL_NAME, dbChannel.getChannelName());
            values.put(COL_CHANNEL_IMAGE, dbChannel.getChannelImage());
            values.put(COL_CHANNEL_URL, dbChannel.getChannelUrl());
            values.put(COL_CATEGORY, dbChannel.getChannelCategory());

            int result = database.update(
                    TABLE_CHANNELS,
                    values,
                    COL_ID + " = ?",
                    new String[]{String.valueOf(dbChannel.getId())}
            );

            if (result == 0) {
                return false;
            }

            if (changeIndex != dbChannel.getChannelCategory()) {
                decrementCategoryCount(database, changeIndex);
                incrementCategoryCount(database, dbChannel.getChannelCategory());
            }

            if (oldUrl != null && !oldUrl.trim().isEmpty()) {
                String newUrl = dbChannel.getChannelUrl() != null ? dbChannel.getChannelUrl().trim() : null;
                String newName = dbChannel.getChannelName();
                String newImg = dbChannel.getChannelImage();

                ContentValues favCv = new ContentValues();
                if (newName != null) favCv.put(COL_FAVORITE_NAME, newName);
                if (newUrl != null && !newUrl.isEmpty()) favCv.put(COL_FAVORITE_URL, newUrl);
                if (newImg != null) favCv.put(COL_FAVORITE_ICON, newImg);
                database.update(TABLE_FAVORITES, favCv, COL_FAVORITE_URL + " = ?", new String[]{oldUrl.trim()});

                ContentValues histCv = new ContentValues();
                if (newName != null) histCv.put(COL_HISTORY_NAME, newName);
                if (newUrl != null && !newUrl.isEmpty()) histCv.put(COL_HISTORY_URL, newUrl);
                if (newImg != null) histCv.put(COL_HISTORY_ICON, newImg);
                database.update(TABLE_HISTORY, histCv, COL_HISTORY_URL + " = ?", new String[]{oldUrl.trim()});
            }

            database.setTransactionSuccessful();
            return true;

        } catch (Exception e) {
            Log.e("PLAYER_DB", "Database update failed", e);
            return false;

        } finally {
            if (database.inTransaction()) {
                database.endTransaction();
            }
        }
    }

    public boolean deleteChannel(String url, int changeIndex) {

        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        SQLiteDatabase database = this.getWritableDatabase();

        try {
            database.beginTransaction();

            int rowsDeleted = database.delete(
                    TABLE_CHANNELS,
                    COL_CHANNEL_URL + " = ?",
                    new String[]{url.trim()}
            );

            if (rowsDeleted == 0) {
                return false;
            }

            decrementCategoryCount(database, changeIndex);

            database.delete(TABLE_FAVORITES, COL_FAVORITE_URL + " = ?", new String[]{url.trim()});
            database.delete(TABLE_HISTORY, COL_HISTORY_URL + " = ?", new String[]{url.trim()});

            database.setTransactionSuccessful();

            return true;

        } catch (Exception e) {
            Log.e("PLAYER_DB", "Channel deletion failed", e);
            return false;

        } finally {
            if (database.inTransaction()) {
                database.endTransaction();
            }
        }
    }

    // ============================================
    // Database functions categories

    public void syncCategoryCounts() {

        SQLiteDatabase database = this.getWritableDatabase();
        List<String> categories = AppUtils.showCategories();

        try {
            database.beginTransaction();

            for (int i = 0; i < categories.size(); i++) {

                long count = DatabaseUtils.longForQuery(
                        database,
                        "SELECT COUNT(*) FROM " + TABLE_CHANNELS +
                                " WHERE " + COL_CATEGORY + " = ?",
                        new String[]{String.valueOf(i)}
                );

                ContentValues values = new ContentValues();
                values.put(COL_COUNT, count);

                database.update(
                        TABLE_CATEGORIES,
                        values,
                        COL_CATEGORY + " = ?",
                        new String[]{categories.get(i)}
                );
            }

            database.setTransactionSuccessful();

        } catch (Exception e) {
            Log.e("PLAYER_DB", "Category count sync failed", e);

        } finally {
            if (database.inTransaction()) {
                database.endTransaction();
            }
        }
    }

    public List<LocalDbCategory> getCategories() {
        List<LocalDbCategory> categories = new ArrayList<>();

        SQLiteDatabase database = this.getReadableDatabase();

        try (Cursor cursor = database.query(
                TABLE_CATEGORIES,
                null,
                null,
                null,
                null,
                null,
                COL_ID + " ASC"
        )) {
            while (cursor.moveToNext()) {
                categories.add(new LocalDbCategory(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_COUNT)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_ICON))
                ));
            }
        }

        return categories;
    }

    private void incrementCategoryCount(SQLiteDatabase db, int categoryIndex) {
        List<String> categories = AppUtils.showCategories();

        if (categoryIndex < 0 || categoryIndex >= categories.size()) return;

        db.execSQL(
                "UPDATE " + TABLE_CATEGORIES +
                        " SET " + COL_COUNT + " = " + COL_COUNT + " + 1" +
                        " WHERE " + COL_CATEGORY + " = ?",
                new Object[]{categories.get(categoryIndex)}
        );
    }

    private void decrementCategoryCount(SQLiteDatabase db, int categoryIndex) {
        List<String> categories = AppUtils.showCategories();

        if (categoryIndex < 0 || categoryIndex >= categories.size()) return;

        db.execSQL(
                "UPDATE " + TABLE_CATEGORIES +
                        " SET " + COL_COUNT + " = MAX(0, " + COL_COUNT + " - 1)" +
                        " WHERE " + COL_CATEGORY + " = ?",
                new Object[]{categories.get(categoryIndex)}
        );
    }

    public int getCategoryId(String categoryName) {
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                TABLE_CATEGORIES,
                new String[]{COL_ID},
                COL_CATEGORY + " = ?",
                new String[]{categoryName},
                null, null, null
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }

        return -1;
    }

    public static class UnifiedSearchResult {
        private final int id;
        private final String name;
        private final String url;
        private final String image;
        private final String categoryName;
        private final PlaylistType type;

        public UnifiedSearchResult(int id, String name, String url, String image, String categoryName, PlaylistType type) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.image = image;
            this.categoryName = categoryName;
            this.type = type != null ? type : PlaylistType.LIVE;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getUrl() { return url; }
        public String getImage() { return image; }
        public String getCategoryName() { return categoryName; }
        public PlaylistType getType() { return type; }
    }

    public List<UnifiedSearchResult> searchAllMedia(String query) {
        List<UnifiedSearchResult> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        String searchPattern = "%" + query.trim() + "%";
        SQLiteDatabase db = getReadableDatabase();

        String channelQuery = "SELECT c." + COL_ID + ", c." + COL_CHANNEL_NAME + ", c." + COL_CHANNEL_IMAGE +
                ", c." + COL_CHANNEL_URL + ", cat." + COL_CATEGORY + " as catName " +
                " FROM " + TABLE_CHANNELS + " c " +
                " LEFT JOIN " + TABLE_CATEGORIES + " cat ON c." + COL_CATEGORY + " = cat." + COL_ID +
                " WHERE c." + COL_CHANNEL_NAME + " LIKE ? OR cat." + COL_CATEGORY + " LIKE ?" +
                " ORDER BY c." + COL_CHANNEL_NAME + " COLLATE NOCASE ASC LIMIT 120";

        try (Cursor cursor = db.rawQuery(channelQuery, new String[]{searchPattern, searchPattern})) {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                String name = cursor.getString(1);
                String image = cursor.getString(2);
                String url = cursor.getString(3);
                String catName = cursor.getString(4);

                PlaylistType type = determineMediaType(catName, url);
                results.add(new UnifiedSearchResult(id, name, url, image, catName, type));
            }
        } catch (Exception e) {
            Log.e("SQL_SEARCH", "Channel search failed", e);
        }

        String playlistQuery = "SELECT " + COL_ID + ", " + COL_PLAYLIST_NAME + ", " + COL_PLAYLIST_URL + ", " + COL_PLAYLIST_COUNT +
                " FROM " + TABLE_PLAYLISTS +
                " WHERE " + COL_PLAYLIST_NAME + " LIKE ? LIMIT 20";

        try (Cursor cursor = db.rawQuery(playlistQuery, new String[]{searchPattern})) {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                String name = cursor.getString(1);
                String url = cursor.getString(2);
                int count = cursor.getInt(3);
                String catName = count + " Channels";
                results.add(new UnifiedSearchResult(id, name, url, null, catName, PlaylistType.PLAYLIST));
            }
        } catch (Exception e) {
            Log.e("SQL_SEARCH", "Playlist search failed", e);
        }

        return results;
    }

    private PlaylistType determineMediaType(String categoryName, String url) {
        String lowerCat = categoryName != null ? categoryName.toLowerCase(Locale.ROOT) : "";
        String lowerUrl = url != null ? url.toLowerCase(Locale.ROOT) : "";

        if (lowerCat.contains("radio") || lowerCat.contains("music") || lowerCat.contains("audio") || lowerCat.contains("mp3")
                || lowerCat.contains("podcasts")) {
            return PlaylistType.AUDIO;
        }
        for (String ext : MediaTypes.AUDIO_EXT) {
            if (lowerUrl.contains("." + ext)) {
                return PlaylistType.AUDIO;
            }
        }

        if (lowerCat.contains("movie") || lowerCat.contains("vod") || lowerCat.contains("series")
                || lowerCat.contains("cinema") || lowerCat.contains("film") || lowerCat.contains("documentary")) {
            return PlaylistType.VOD;
        }
        for (String ext : MediaTypes.VIDEO_EXT) {
            if (lowerUrl.contains("." + ext) && !lowerUrl.contains(".m3u") && !lowerUrl.contains(".ts")) {
                return PlaylistType.VOD;
            }
        }

        return PlaylistType.LIVE;
    }
}

