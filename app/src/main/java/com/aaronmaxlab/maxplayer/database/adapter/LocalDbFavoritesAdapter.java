
package com.aaronmaxlab.maxplayer.database.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.models.LocalDbFavorites;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import java.util.ArrayList;
import java.util.List;

public class LocalDbFavoritesAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_FAVORITE = 1;

    public interface Listener {
        void onItemClick(LocalDbFavorites item);
        void onRemoveClick(LocalDbFavorites item);
    }

    private static class Row {
        final String header;
        final LocalDbFavorites favorite;

        private Row(String header, LocalDbFavorites favorite) {
            this.header = header;
            this.favorite = favorite;
        }

        static Row header(String title) {
            return new Row(title, null);
        }

        static Row favorite(LocalDbFavorites item) {
            return new Row(null, item);
        }

        boolean isHeader() {
            return favorite == null;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;

    public LocalDbFavoritesAdapter(Listener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isHeader()
                ? TYPE_HEADER : TYPE_FAVORITE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {

        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(
                    R.layout.item_favorite_header, parent, false);
            return new HeaderHolder(view);
        }

        View view = inflater.inflate(
                R.layout.item_favorite, parent, false);
        return new FavoriteHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder, int position) {

        Row row = rows.get(position);

        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).bind(row.header);
        } else if (holder instanceof FavoriteHolder) {
            ((FavoriteHolder) holder).bind(row.favorite);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    public void submitList(List<LocalDbFavorites> favorites) {
        rows.clear();

        addGroup(favorites, "VOD", "Videos");
        addGroup(favorites, "PLAYLIST", "Playlists");
        addGroup(favorites, "AUDIO", "Streams");
        addGroup(favorites, "LIVE", "Channels");

        notifyDataSetChanged();
    }

    private void addGroup(
            List<LocalDbFavorites> favorites,
            String type,
            String title) {

        if (favorites == null || favorites.isEmpty()) {
            return;
        }

        List<LocalDbFavorites> group = new ArrayList<>();

        for (LocalDbFavorites item : favorites) {
            if (item != null
                    && type.equalsIgnoreCase(
                    String.valueOf(item.getFavType()))) {
                group.add(item);
            }
        }

        if (!group.isEmpty()) {
            rows.add(Row.header(title));

            for (LocalDbFavorites item : group) {
                rows.add(Row.favorite(item));
            }
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        private final TextView title;

        HeaderHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.groupTitle);
        }

        void bind(String text) {
            title.setText(text);
        }
    }

    class FavoriteHolder extends RecyclerView.ViewHolder {
        private final View root;
        private final ImageView icon;
        private final TextView title;
        private final TextView category;
        private final ImageButton favoriteButton;

        FavoriteHolder(@NonNull View itemView) {
            super(itemView);

            root = itemView;
            icon = itemView.findViewById(R.id.mediaIcon);
            title = itemView.findViewById(R.id.mediaTitle);
            category = itemView.findViewById(R.id.mediaCategory);
            favoriteButton = itemView.findViewById(R.id.mediaFav);
        }

        void bind(LocalDbFavorites item) {
            title.setText(UIUtils.toCapitalize(item.getFavName()));
            category.setText(item.getFavTag());

            String type = String.valueOf(item.getFavType());

            if ("AUDIO".equalsIgnoreCase(type)) { icon.setImageResource(R.drawable.ic_radio); }
            else if ("PLAYLIST".equalsIgnoreCase(type)) { icon.setImageResource(R.drawable.ic_folder); }
            else { icon.setImageResource(R.drawable.ic_tv); }

            favoriteButton.setImageResource(R.drawable.ic_heart_filled);

            ClickUtils.applyTouchEffect(root, view -> listener.onItemClick(item));
            ClickUtils.applyTouchEffect(favoriteButton, view -> listener.onRemoveClick(item));

        }
    }
}
