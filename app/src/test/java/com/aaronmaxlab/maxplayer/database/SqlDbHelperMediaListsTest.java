package com.aaronmaxlab.maxplayer.database;

import com.aaronmaxlab.maxplayer.database.models.LocalDbFavorites;
import com.aaronmaxlab.maxplayer.database.models.LocalDbHistory;
import com.aaronmaxlab.maxplayer.database.models.LocalDbRecentView;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class SqlDbHelperMediaListsTest extends DatabaseTestBase {

    @Test
    public void favoriteToggleLookupAndRemoval() {
        LocalDbFavorites favorite = new LocalDbFavorites(
                "CNN", "News", "http://favorite/cnn", "icon", PlaylistType.LIVE);

        assertTrue(helper.setFavorite(favorite));
        assertTrue(helper.isFavorite("http://favorite/cnn"));

        assertTrue(helper.setFavorite(favorite));
        assertFalse(helper.isFavorite("http://favorite/cnn"));
        assertFalse(helper.removeFav("http://favorite/cnn"));
    }

    @Test
    public void favoriteWhitespaceIsStoredVerbatimButRemovalTrims_characterizesCurrentBehavior() {
        String rawUrl = "  http://favorite/spaced  ";
        LocalDbFavorites favorite = new LocalDbFavorites(
                "Spaced", "tag", rawUrl, "icon", PlaylistType.LIVE);

        assertTrue(helper.setFavorite(favorite));

        assertTrue(helper.isFavorite(rawUrl));
        assertFalse(helper.isFavorite("http://favorite/spaced"));
        assertFalse(helper.removeFav("http://favorite/spaced"));
        assertTrue(helper.isFavorite(rawUrl));
    }

    @Test
    public void favoriteListIsOrderedByIdDescending() {
        assertTrue(helper.setFavorite(new LocalDbFavorites(
                "First", "tag", "http://favorite/first", "icon", PlaylistType.LIVE)));
        assertTrue(helper.setFavorite(new LocalDbFavorites(
                "Second", "tag", "http://favorite/second", "icon", PlaylistType.LIVE)));

        List<LocalDbFavorites> favorites = helper.getAllFavorites();

        assertEquals(2, favorites.size());
        assertEquals("Second", favorites.get(0).getFavName());
        assertEquals("First", favorites.get(1).getFavName());
    }

    @Test
    public void blankFavoriteUrlCannotBeRemoved() {
        assertFalse(helper.removeFav(null));
        assertFalse(helper.removeFav("   "));
    }

    @Test
    public void historyInsertRetrievePositionTypeUpdateAndClear() {
        helper.saveHistory(new LocalDbHistory(
                "Movie", "Movies", "http://history/movie", "icon",
                PlaylistType.VOD, 0L, 0L));

        List<LocalDbHistory> history = helper.getAllHistory();
        assertEquals(1, history.size());
        LocalDbHistory stored = history.get(0);
        assertEquals("Movie", stored.getHistoryName());
        assertEquals("http://history/movie", stored.getHistoryUrl());
        assertEquals("VOD", stored.getHistoryType());
        assertEquals(0L, stored.getHistoryPosition().longValue());
        assertEquals(0L, stored.getHistoryDuration().longValue());
        assertTrue(stored.getCreatedAt() > 0L);

        helper.updateHistoryPosition("http://history/movie", 250L, 2500L);
        LocalDbHistory updated = helper.getAllHistory().get(0);
        assertEquals(250L, updated.getHistoryPosition().longValue());
        assertEquals(2500L, updated.getHistoryDuration().longValue());

        assertTrue(helper.isHistoryExists("http://history/movie"));
        assertTrue(helper.isHistoryTypeMatch("http://history/movie", PlaylistType.VOD));
        assertFalse(helper.isHistoryTypeMatch("http://history/movie", PlaylistType.LIVE));

        assertTrue(helper.updateHistoryType("http://history/movie", PlaylistType.LIVE));
        assertTrue(helper.isHistoryTypeMatch("http://history/movie", PlaylistType.LIVE));

        helper.updateHistoryPosition("", 1L, 1L);
        assertEquals(1, helper.getAllHistory().size());

        assertTrue(helper.clearHistory());
        assertTrue(helper.getAllHistory().isEmpty());
    }

    @Test
    public void historyDuplicateUrlIsIgnoredAndTimestampIsNotRefreshed() {
        helper.saveHistory(new LocalDbHistory(
                "First", "tag", "http://history/duplicate", "icon",
                PlaylistType.VOD, 0L, 0L));
        long firstCreatedAt = helper.getAllHistory().get(0).getCreatedAt();

        helper.saveHistory(new LocalDbHistory(
                "Second", "tag", "http://history/duplicate", "new-icon",
                PlaylistType.LIVE, 999L, 999L));

        List<LocalDbHistory> history = helper.getAllHistory();
        assertEquals(1, history.size());
        assertEquals("First", history.get(0).getHistoryName());
        assertEquals("VOD", history.get(0).getHistoryType());
        assertEquals(firstCreatedAt, history.get(0).getCreatedAt().longValue());
    }

    @Test
    public void historyBoundaryValuesRoundTrip() {
        String url = "http://history/boundary";
        helper.saveHistory(new LocalDbHistory(
                "Boundary", "tag", url, "icon", PlaylistType.VOD,
                Long.MAX_VALUE, Long.MAX_VALUE));

        LocalDbHistory stored = helper.getAllHistory().get(0);
        assertEquals(Long.MAX_VALUE, stored.getHistoryPosition().longValue());
        assertEquals(Long.MAX_VALUE, stored.getHistoryDuration().longValue());

        helper.updateHistoryPosition(url, Long.MIN_VALUE, Long.MAX_VALUE - 1);
        LocalDbHistory updated = helper.getAllHistory().get(0);
        assertEquals(Long.MIN_VALUE, updated.getHistoryPosition().longValue());
        assertEquals(Long.MAX_VALUE - 1, updated.getHistoryDuration().longValue());
    }

    @Test
    public void recentViewInsertUpsertFilterAndRemoval() {
        helper.setRecentViews(new LocalDbRecentView(
                "Channel", "http://recent/channel", PlaylistType.VOD, "old-icon"));

        List<LocalDbRecentView> vodViews = helper.getAllRecentViews(PlaylistType.VOD);
        assertEquals(1, vodViews.size());
        LocalDbRecentView stored = vodViews.get(0);
        assertEquals("Channel", stored.getChannelName());
        assertEquals("http://recent/channel", stored.getChannelUrl());
        assertEquals("VOD", stored.getChannelType());
        assertEquals("old-icon", stored.getChannelIcon());
        assertTrue(stored.getCreatedAt() > 0L);
        assertTrue(helper.getAllRecentViews(PlaylistType.AUDIO).isEmpty());

        int id = stored.getId();
        helper.setRecentViews(new LocalDbRecentView(
                "Updated", "http://recent/channel", PlaylistType.AUDIO, "new-icon"));

        assertTrue(helper.getAllRecentViews(PlaylistType.VOD).isEmpty());
        List<LocalDbRecentView> audioViews = helper.getAllRecentViews(PlaylistType.AUDIO);
        assertEquals(1, audioViews.size());
        assertEquals(Integer.valueOf(id), audioViews.get(0).getId());
        assertEquals("Updated", audioViews.get(0).getChannelName());
        assertEquals("new-icon", audioViews.get(0).getChannelIcon());

        assertTrue(helper.removeRecentItem(id));
        assertFalse(helper.removeRecentItem(id));
        assertTrue(helper.getAllRecentViews(PlaylistType.AUDIO).isEmpty());
    }
}
