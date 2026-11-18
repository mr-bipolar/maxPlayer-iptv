package com.aaronmaxlab.maxplayer.database;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import com.aaronmaxlab.maxplayer.database.models.LocalDbCategory;
import com.aaronmaxlab.maxplayer.database.models.LocalDbPlaylist;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class SqlDbHelperSchemaTest extends DatabaseTestBase {

    private static final String[] EXPECTED_TABLES = {
            "categories", "channels", "favorites", "history", "playlists", "recentViews"
    };

    private static final String[] CATEGORY_NAMES = {
            "Live TV", "Sports", "News", "Movies", "Series", "Kids", "Documentary",
            "Entertainment", "Music", "Radio", "Talk", "Lifestyle", "Education",
            "Religious", "Regional", "International", "18+", "Podcast", "MP3", "Other"
    };

    private static final String[] CATEGORY_ICONS = {
            "ic_tv", "ic_cat_sport", "ic_cat_news", "ic_cat_movies", "ic_series",
            "ic_cat_kids", "ic_documentary", "ic_entertainment", "ic_music", "ic_radio",
            "ic_talk", "ic_life_style", "ic_education", "ic_religious", "ic_regional",
            "ic_international", "ic_18_plus", "ic_podcasts", "ic_audio", "ic_other"
    };

    @Test
    public void createsSixExpectedTables() {
        SQLiteDatabase db = helper.getWritableDatabase();

        List<String> tables = queryStrings(
                db,
                "SELECT name FROM sqlite_master " +
                        "WHERE type = 'table' " +
                        "AND name NOT LIKE 'sqlite_%' " +
                        "AND name != 'android_metadata' ORDER BY name"
        );

        assertEquals(Arrays.asList(EXPECTED_TABLES), tables);
    }

    @Test
    public void schemaHasExpectedColumns() {
        SQLiteDatabase db = helper.getWritableDatabase();

        assertColumns(db, "categories", "id", "category", "count", "icon");
        assertColumns(db, "channels", "id", "channelName", "channelImage", "channelUrl", "category");
        assertColumns(db, "playlists", "id", "playlistName", "playlistUrl", "playlistCount", "createdAt");
        assertColumns(db, "favorites", "id", "favoriteName", "favoriteTag", "favoriteType", "favIcon", "favoriteUrl");
        assertColumns(db, "history", "id", "historyName", "historyTag", "historyType",
                "historyPlayPosition", "historyPlayDuration", "historyUrl", "historyIcon", "createdAt");
        assertColumns(db, "recentViews", "id", "channelName", "mediaType", "icon", "channelUrl", "createdAt");
    }

    @Test
    public void schemaHasExpectedConstraintsAndIndexes() {
        SQLiteDatabase db = helper.getWritableDatabase();

        assertPrimaryKey(db, "categories", "id");
        assertPrimaryKey(db, "channels", "id");
        assertPrimaryKey(db, "playlists", "id");
        assertPrimaryKey(db, "favorites", "id");
        assertPrimaryKey(db, "history", "id");
        assertPrimaryKey(db, "recentViews", "id");

        assertNotNullColumn(db, "categories", "category");
        assertUniqueIndex(db, "categories", "category");
        assertUniqueIndex(db, "channels", "channelUrl");
        assertUniqueIndex(db, "playlists", "playlistUrl");
        assertUniqueIndex(db, "favorites", "favoriteUrl");
        assertUniqueIndex(db, "history", "historyUrl");
        assertUniqueIndex(db, "recentViews", "channelUrl");

        assertTrue(queryStrings(
                db,
                "SELECT name FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL"
        ).isEmpty());

        for (String table : EXPECTED_TABLES) {
            try (Cursor cursor = db.rawQuery("PRAGMA foreign_key_list(" + table + ")", null)) {
                assertFalse("Unexpected FK on " + table, cursor.moveToFirst());
            }
        }
    }

    @Test
    public void schemaDefaultsAreApplied() {
        SQLiteDatabase db = helper.getWritableDatabase();

        db.execSQL("INSERT INTO categories(category, icon) VALUES('TempCategory', 'ic_other')");
        assertEquals(0L, DatabaseUtils.longForQuery(
                db,
                "SELECT count FROM categories WHERE category = 'TempCategory'",
                null
        ));

        db.execSQL("INSERT INTO history(historyUrl) VALUES('http://default-history')");
        assertEquals(0L, DatabaseUtils.longForQuery(
                db,
                "SELECT historyPlayPosition FROM history WHERE historyUrl = 'http://default-history'",
                null
        ));
        assertEquals(0L, DatabaseUtils.longForQuery(
                db,
                "SELECT historyPlayDuration FROM history WHERE historyUrl = 'http://default-history'",
                null
        ));
    }

    @Test
    public void seedsAllDefaultCategories() {
        List<LocalDbCategory> categories = helper.getCategories();

        assertEquals(20, categories.size());
        for (int i = 0; i < CATEGORY_NAMES.length; i++) {
            LocalDbCategory category = categories.get(i);
            assertEquals(Integer.valueOf(i + 1), category.getId());
            assertEquals(CATEGORY_NAMES[i], category.getCategoryName());
            assertEquals(CATEGORY_ICONS[i], category.getCategoryIcon());
            assertEquals(Integer.valueOf(0), category.getCategoryCount());
        }
    }

    @Test
    public void repeatedOpenDoesNotDuplicateCategories() {
        assertEquals(20, helper.getCategories().size());

        helper.close();
        helper = new SqlDbHelper(context);
        helper.getWritableDatabase();

        assertEquals(20, helper.getCategories().size());
        assertEquals("Live TV", helper.getCategories().get(0).getCategoryName());
    }

    @Test
    public void categoryNameIsUniqueIgnoringCase() {
        SQLiteDatabase db = helper.getWritableDatabase();

        db.execSQL("INSERT OR IGNORE INTO categories(category, count, icon) VALUES('live tv', 0, 'x')");

        assertEquals(20, helper.getCategories().size());
        assertEquals("Live TV", helper.getCategories().get(0).getCategoryName());
    }

    @Test
    public void nullCategoryIsRejected() {
        SQLiteDatabase db = helper.getWritableDatabase();

        assertFails(() -> db.execSQL(
                "INSERT INTO categories(category, count, icon) VALUES(NULL, 0, 'x')"
        ));

        assertEquals(20, helper.getCategories().size());
    }

    @Test
    public void nullUrlUniqueColumnsAllowMultipleRows_characterizesCurrentBehavior() {
        assertTrue(helper.addPlaylist(new LocalDbPlaylist("First null", null, 1)));
        assertTrue(helper.addPlaylist(new LocalDbPlaylist("Second null", null, 2)));

        List<LocalDbPlaylist> playlists = helper.getAllPlaylists();
        assertEquals(2, playlists.size());
        assertEquals("First null", playlists.get(0).getPlaylistName());
        assertEquals("Second null", playlists.get(1).getPlaylistName());
        assertFalse(playlists.get(0).getPlaylistUrl() != null);
    }

    private static List<String> queryStrings(SQLiteDatabase db, String sql) {
        List<String> values = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                values.add(cursor.getString(0));
            }
        }
        return values;
    }

    private static void assertColumns(SQLiteDatabase db, String table, String... expected) {
        List<String> actual = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) {
                actual.add(cursor.getString(nameIndex));
            }
        }
        assertEquals(Arrays.asList(expected), actual);
    }

    private static void assertPrimaryKey(SQLiteDatabase db, String table, String expectedColumn) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            int pkIndex = cursor.getColumnIndexOrThrow("pk");
            while (cursor.moveToNext()) {
                if (cursor.getInt(pkIndex) > 0) {
                    assertEquals(expectedColumn, cursor.getString(nameIndex));
                    return;
                }
            }
        }
        org.junit.Assert.fail("No primary key found on " + table);
    }

    private static void assertNotNullColumn(SQLiteDatabase db, String table, String expectedColumn) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            int notNullIndex = cursor.getColumnIndexOrThrow("notnull");
            while (cursor.moveToNext()) {
                if (expectedColumn.equals(cursor.getString(nameIndex))) {
                    assertEquals(1, cursor.getInt(notNullIndex));
                    return;
                }
            }
        }
        org.junit.Assert.fail("Column not found: " + table + "." + expectedColumn);
    }

    private static void assertUniqueIndex(SQLiteDatabase db, String table, String expectedColumn) {
        try (Cursor indexes = db.rawQuery("PRAGMA index_list(" + table + ")", null)) {
            int nameIndex = indexes.getColumnIndexOrThrow("name");
            int uniqueIndex = indexes.getColumnIndexOrThrow("unique");
            while (indexes.moveToNext()) {
                if (indexes.getInt(uniqueIndex) != 1) {
                    continue;
                }
                try (Cursor info = db.rawQuery(
                        "PRAGMA index_info(" + indexes.getString(nameIndex) + ")", null)) {
                    int columnIndex = info.getColumnIndexOrThrow("name");
                    if (info.moveToFirst() && expectedColumn.equals(info.getString(columnIndex))) {
                        return;
                    }
                }
            }
        }
        org.junit.Assert.fail("No UNIQUE index found for " + table + "." + expectedColumn);
    }
}
