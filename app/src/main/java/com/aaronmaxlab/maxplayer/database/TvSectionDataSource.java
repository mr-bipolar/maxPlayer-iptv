package com.aaronmaxlab.maxplayer.database;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.adapter.TvMainCardAdapter;
import com.aaronmaxlab.maxplayer.database.models.LocalDbCategory;
import java.util.ArrayList;
import java.util.List;

/** Shared section metadata and database-backed loading for TV home previews and full lists. */
public final class TvSectionDataSource {
    public static final int MAIN_SCREEN_LIMIT = 6;

    private TvSectionDataSource() { }

    public static int titleRes(TvMainCardAdapter.Kind kind) {
        switch (kind) {
            case HISTORY: return R.string.section_continue_watching;
            case PLAYLIST: return R.string.section_my_playlists;
            case CATEGORY: return R.string.tv_row_categories;
            default: throw new IllegalArgumentException("Unsupported TV section: " + kind);
        }
    }

    /** Loads the complete section data; only category previews omit categories with no channels. */
    public static List<?> load(SqlDbHelper db, TvMainCardAdapter.Kind kind, boolean preview) {
        switch (kind) {
            case HISTORY: return db.getAllHistory();
            case PLAYLIST: return db.getAllPlaylists();
            case CATEGORY:
                db.syncCategoryCounts();
                List<LocalDbCategory> categories = db.getCategories();
                if (preview) {
                    List<LocalDbCategory> populated = new ArrayList<>();
                    for (LocalDbCategory category : categories) {
                        if (category.getCategoryCount() != null && category.getCategoryCount() > 0) populated.add(category);
                    }
                    return populated;
                }
                return categories;
            default: throw new IllegalArgumentException("Unsupported TV section: " + kind);
        }
    }

}
