package com.aaronmaxlab.maxplayer.database;

import com.aaronmaxlab.maxplayer.database.models.LocalDbChannel;
import com.aaronmaxlab.maxplayer.database.models.LocalDbFavorites;
import com.aaronmaxlab.maxplayer.database.models.LocalDbHistory;
import com.aaronmaxlab.maxplayer.database.models.LocalDbPlaylist;
import com.aaronmaxlab.maxplayer.database.models.LocalDbRecentView;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper.UnifiedSearchResult;
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
public class SqlDbHelperSearchPersistenceTest extends DatabaseTestBase {

    @Test
    public void searchReturnsEmptyForNullOrBlankQueries() {
        assertTrue(helper.searchAllMedia(null).isEmpty());
        assertTrue(helper.searchAllMedia("").isEmpty());
        assertTrue(helper.searchAllMedia("   ").isEmpty());
    }

    @Test
    public void searchMatchesChannelNamesAndCurrentCategoryMapping_characterizesCurrentBehavior() {
        assertTrue(helper.addChannel(new LocalDbChannel(
                "Alpha News", "logo", "http://channel/alpha",
                AppUtils.getCategoryIndex("News"))));
        assertTrue(helper.addChannel(new LocalDbChannel(
                "beta SPORTS", null, "http://channel/beta",
                AppUtils.getCategoryIndex("Sports"))));
        assertTrue(helper.addChannel(new LocalDbChannel(
                "Gamma", null, "http://channel/gamma",
                AppUtils.getCategoryIndex("Other"))));

        List<UnifiedSearchResult> news = helper.searchAllMedia("news");
        assertEquals(1, news.size());
        assertEquals("Alpha News", news.get(0).getName());
        assertEquals("http://channel/alpha", news.get(0).getUrl());
        assertEquals("logo", news.get(0).getImage());
        assertEquals(PlaylistType.LIVE, news.get(0).getType());

        // Channels store the 0-based AppUtils category index, while search joins it to
        // the 1-based categories.id. "Alpha News" was stored with index 2, so it is
        // joined to category id 2 ("Sports") and is also returned by this query.
        List<UnifiedSearchResult> sports = helper.searchAllMedia("SPORTS");
        assertEquals(2, sports.size());
        assertTrue(hasName(sports, "beta SPORTS"));
        assertTrue(hasName(sports, "Alpha News"));
    }

    @Test
    public void searchMatchesPlaylistNamesButNotPlaylistUrls() {
        assertTrue(helper.addPlaylist(new LocalDbPlaylist(
                "Playlist Archive", "http://host/unmatched-url.m3u", 7)));

        List<UnifiedSearchResult> byName = helper.searchAllMedia("archive");
        assertEquals(1, byName.size());
        assertEquals("Playlist Archive", byName.get(0).getName());
        assertEquals("http://host/unmatched-url.m3u", byName.get(0).getUrl());
        assertEquals("7 Channels", byName.get(0).getCategoryName());
        assertEquals(PlaylistType.PLAYLIST, byName.get(0).getType());

        assertTrue(helper.searchAllMedia("unmatched-url").isEmpty());
    }

    @Test
    public void searchAppliesChannelAndPlaylistResultLimits() {
        for (int i = 0; i < 130; i++) {
            assertTrue(helper.addChannel(new LocalDbChannel(
                    String.format("Match %03d", i), null,
                    "http://channel/match-" + i,
                    AppUtils.getCategoryIndex("Live TV"))));
        }
        for (int i = 0; i < 25; i++) {
            assertTrue(helper.addPlaylist(new LocalDbPlaylist(
                    String.format("Match Playlist %02d", i),
                    "http://host/match-" + i + ".m3u", i)));
        }

        List<UnifiedSearchResult> results = helper.searchAllMedia("Match");

        assertEquals(140, results.size());
        assertEquals(120, countByType(results, PlaylistType.LIVE));
        assertEquals(20, countByType(results, PlaylistType.PLAYLIST));
        assertFalse(hasName(results, "Match 120"));
        assertFalse(hasName(results, "Match Playlist 20"));
    }

    @Test
    public void searchDoesNotEscapeLikeWildcards_characterizesCurrentBehavior() {
        assertTrue(helper.addChannel(new LocalDbChannel(
                "Percent%Channel", null, "http://channel/percent",
                AppUtils.getCategoryIndex("Other"))));
        assertTrue(helper.addChannel(new LocalDbChannel(
                "Underscore_Channel", null, "http://channel/underscore",
                AppUtils.getCategoryIndex("Other"))));
        assertTrue(helper.addPlaylist(new LocalDbPlaylist(
                "Ordinary Playlist", "http://host/ordinary.m3u", 1)));

        assertEquals(3, helper.searchAllMedia("%").size());
        assertEquals(3, helper.searchAllMedia("_").size());
    }

    @Test
    public void dataPersistsAfterHelperIsClosedAndReopened() {
        int otherIndex = AppUtils.getCategoryIndex("Other");

        assertTrue(helper.addPlaylist(new LocalDbPlaylist(
                "Persisted Playlist", "http://host/persisted.m3u", 2)));
        assertTrue(helper.addChannel(new LocalDbChannel(
                "Persisted Channel", "logo", "http://channel/persisted", otherIndex)));
        assertTrue(helper.setFavorite(new LocalDbFavorites(
                "Persisted Favorite", "tag", "http://favorite/persisted",
                "icon", PlaylistType.LIVE)));
        helper.saveHistory(new LocalDbHistory(
                "Persisted History", "tag", "http://history/persisted", "icon",
                PlaylistType.VOD, 10L, 100L));
        helper.setRecentViews(new LocalDbRecentView(
                "Persisted Recent", "http://recent/persisted", PlaylistType.AUDIO,
                "icon"));

        helper.close();
        helper = new SqlDbHelper(context);
        helper.getWritableDatabase();

        assertEquals(20, helper.getCategories().size());
        assertEquals(1, helper.getAllPlaylists().size());
        assertEquals("Persisted Playlist", helper.getAllPlaylists().get(0).getPlaylistName());
        assertEquals(1, helper.getChannels(null).size());
        assertEquals("Persisted Channel", helper.getChannels(null).get(0).getChannelName());
        assertEquals(1, helper.getAllFavorites().size());
        assertTrue(helper.isFavorite("http://favorite/persisted"));
        assertEquals(1, helper.getAllHistory().size());
        assertEquals(10L, helper.getAllHistory().get(0).getHistoryPosition().longValue());
        assertEquals(1, helper.getAllRecentViews(PlaylistType.AUDIO).size());
        assertEquals("Persisted Recent", helper.getAllRecentViews(PlaylistType.AUDIO).get(0).getChannelName());

        assertNotNull(helper.searchAllMedia("Persisted"));
        assertEquals(2, helper.searchAllMedia("Persisted").size());
    }

    @Test
    public void databasePathIsRedirectedToTheTemporaryDirectory() {
        assertEquals(
                context.root().toFile().getAbsoluteFile(),
                context.getDatabasePath("m3u8Library.db").getParentFile().getAbsoluteFile()
        );
    }

    private static int countByType(List<UnifiedSearchResult> results, PlaylistType type) {
        int count = 0;
        for (UnifiedSearchResult result : results) {
            if (result.getType() == type) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasName(List<UnifiedSearchResult> results, String name) {
        for (UnifiedSearchResult result : results) {
            if (name.equals(result.getName())) {
                return true;
            }
        }
        return false;
    }
}
