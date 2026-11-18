package com.aaronmaxlab.maxplayer.database;

import com.aaronmaxlab.maxplayer.database.models.LocalDbCategory;
import com.aaronmaxlab.maxplayer.database.models.LocalDbChannel;
import com.aaronmaxlab.maxplayer.database.models.LocalDbFavorites;
import com.aaronmaxlab.maxplayer.database.models.LocalDbHistory;
import com.aaronmaxlab.maxplayer.database.models.LocalDbPlaylist;
import com.aaronmaxlab.maxplayer.utils.AppUtils;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class SqlDbHelperPlaylistChannelTest extends DatabaseTestBase {

    @Test
    public void playlistCrudAndDuplicateUrlHandling() {
        LocalDbPlaylist playlist =
                new LocalDbPlaylist("Main List", "http://host/main.m3u", 12);
        assertTrue(helper.addPlaylist(playlist));

        List<LocalDbPlaylist> playlists = helper.getAllPlaylists();
        assertEquals(1, playlists.size());
        LocalDbPlaylist stored = playlists.get(0);
        assertNotNull(stored.getId());
        assertEquals("Main List", stored.getPlaylistName());
        assertEquals("http://host/main.m3u", stored.getPlaylistUrl());
        assertEquals(12, stored.getPlaylistCount());
        assertEquals(0L, stored.getCreatedAt().longValue());

        assertFalse(helper.addPlaylist(
                new LocalDbPlaylist("Duplicate", "http://host/main.m3u", 99)));
        assertEquals(1, helper.getAllPlaylists().size());
        assertEquals("Main List", helper.getAllPlaylists().get(0).getPlaylistName());

        helper.lastOpenUpdate(stored.getId());
        assertTrue(helper.getAllPlaylists().get(0).getCreatedAt() > 0L);
    }

    @Test
    public void playlistUpdatePropagatesToFavoritesAndHistory() {
        String oldUrl = "http://host/old.m3u";
        assertTrue(helper.addPlaylist(new LocalDbPlaylist("Old", oldUrl, 3)));
        int id = helper.getAllPlaylists().get(0).getId();

        assertTrue(helper.setFavorite(new LocalDbFavorites(
                "Old Fav", "tag", oldUrl, "icon", PlaylistType.PLAYLIST)));
        helper.saveHistory(new LocalDbHistory(
                "Old History", "tag", oldUrl, "icon", PlaylistType.PLAYLIST, 0L, 0L));

        assertTrue(helper.updatePlaylist(
                new LocalDbPlaylist(id, "New", "http://host/new.m3u", 9, null)));

        LocalDbPlaylist updated = helper.getAllPlaylists().get(0);
        assertEquals("New", updated.getPlaylistName());
        assertEquals("http://host/new.m3u", updated.getPlaylistUrl());
        assertEquals(9, updated.getPlaylistCount());

        assertEquals("http://host/new.m3u", helper.getAllFavorites().get(0).getFavUrl());
        assertEquals("http://host/new.m3u", helper.getAllHistory().get(0).getHistoryUrl());
    }

    @Test
    public void playlistDeleteCascadesToFavoritesAndHistory() {
        String url = "http://host/delete.m3u";
        assertTrue(helper.addPlaylist(new LocalDbPlaylist("Delete", url, 4)));
        int id = helper.getAllPlaylists().get(0).getId();

        assertTrue(helper.setFavorite(new LocalDbFavorites(
                "Fav", "tag", url, "icon", PlaylistType.PLAYLIST)));
        helper.saveHistory(new LocalDbHistory(
                "History", "tag", url, "icon", PlaylistType.PLAYLIST, 0L, 0L));

        assertTrue(helper.deletePlaylist(id));

        assertTrue(helper.getAllPlaylists().isEmpty());
        assertTrue(helper.getAllFavorites().isEmpty());
        assertTrue(helper.getAllHistory().isEmpty());
    }

    @Test
    public void channelCrudUpdatesCategoryCounts() {
        int liveTvIndex = AppUtils.getCategoryIndex("Live TV");
        int sportsIndex = AppUtils.getCategoryIndex("Sports");

        assertTrue(helper.addChannel(new LocalDbChannel(
                "CNN", "logo", "http://channel/cnn", liveTvIndex)));
        assertEquals(1, categoryCount("Live TV"));

        LocalDbChannel stored = helper.getChannels(liveTvIndex).get(0);
        assertEquals("CNN", stored.getChannelName());
        assertEquals(Integer.valueOf(liveTvIndex), stored.getChannelCategory());

        assertTrue(helper.updateChannel(
                new LocalDbChannel(stored.getId(), "CNN HD", "logo2",
                        "http://channel/cnn-hd", sportsIndex),
                liveTvIndex
        ));

        assertEquals(0, categoryCount("Live TV"));
        assertEquals(1, categoryCount("Sports"));
        assertEquals("CNN HD", helper.getChannels(sportsIndex).get(0).getChannelName());

        assertTrue(helper.deleteChannel("http://channel/cnn-hd", sportsIndex));
        assertEquals(0, categoryCount("Sports"));
        assertTrue(helper.getChannels(null).isEmpty());
    }

    @Test
    public void channelCategoryIndexDoesNotMatchCategoryId_characterizesCurrentBehavior() {
        int liveTvIndex = AppUtils.getCategoryIndex("Live TV");
        int liveTvId = helper.getCategoryId("Live TV");

        assertEquals(0, liveTvIndex);
        assertEquals(1, liveTvId);

        assertTrue(helper.addChannel(new LocalDbChannel(
                "Index Channel", null, "http://channel/index", liveTvIndex)));

        assertEquals(1, helper.getChannels(liveTvIndex).size());
        assertTrue(helper.getChannels(liveTvId).isEmpty());
        assertEquals(1, categoryCount("Live TV"));
    }

    @Test
    public void duplicateChannelIsIgnoredAndDoesNotIncrementCount() {
        int liveTvIndex = AppUtils.getCategoryIndex("Live TV");

        assertTrue(helper.addChannel(new LocalDbChannel(
                "First", null, "http://channel/duplicate", liveTvIndex)));
        assertFalse(helper.addChannel(new LocalDbChannel(
                "Second", null, "http://channel/duplicate", liveTvIndex)));

        assertEquals(1, helper.getChannels(null).size());
        assertEquals(1, categoryCount("Live TV"));
    }

    @Test
    public void deleteChannelRemovesRelatedFavoriteAndHistory() {
        int sportsIndex = AppUtils.getCategoryIndex("Sports");
        String url = "http://channel/related";

        assertTrue(helper.addChannel(new LocalDbChannel(
                "Related", null, url, sportsIndex)));
        assertTrue(helper.setFavorite(new LocalDbFavorites(
                "Related Fav", "tag", url, "icon", PlaylistType.LIVE)));
        helper.saveHistory(new LocalDbHistory(
                "Related History", "tag", url, "icon", PlaylistType.LIVE, 0L, 0L));

        assertTrue(helper.deleteChannel(url, sportsIndex));

        assertFalse(helper.isFavorite(url));
        assertFalse(helper.isHistoryExists(url));
    }

    private int categoryCount(String name) {
        for (LocalDbCategory category : helper.getCategories()) {
            if (name.equals(category.getCategoryName())) {
                return category.getCategoryCount();
            }
        }
        throw new AssertionError("Category not found: " + name);
    }
}
