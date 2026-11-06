package com.aaronmaxlab.maxplayer.database.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.models.LocalDbCategory;
import com.aaronmaxlab.maxplayer.database.models.LocalDbHistory;
import com.aaronmaxlab.maxplayer.database.models.LocalDbPlaylist;
import com.aaronmaxlab.maxplayer.utils.AppUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/** Compact TV card renderer for existing database models. */
public class TvMainCardAdapter extends RecyclerView.Adapter<TvMainCardAdapter.CardHolder> {
    public enum Kind { HISTORY, PLAYLIST, CATEGORY }

    public interface Listener {
        void onHistoryClick(LocalDbHistory item);
        void onPlaylistClick(LocalDbPlaylist item);
        void onCategoryClick(LocalDbCategory item);
        void onAddPlaylistClick();
        void onSeeMoreClick(Kind kind);
    }

    private static final Object ADD_PLAYLIST = new Object();
    private static final Object SEE_MORE = new Object();

    private final Kind kind;
    private final Listener listener;
    private final boolean showAddPlaylist;
    private final List<Object> items = new ArrayList<>();
    private final int actionCardHeight;


    public TvMainCardAdapter(Kind kind, Listener listener, int  buttonHeight) {
        this(kind, listener, buttonHeight, true);
    }

    public TvMainCardAdapter(Kind kind, Listener listener, int buttonHeight, boolean showAddPlaylist) {
        this.kind = kind;
        this.listener = listener;
        this.showAddPlaylist = showAddPlaylist;
        this.actionCardHeight = buttonHeight;
        setHasStableIds(true);
    }

    public void submitList(List<?> values) {
        items.clear();
        if (values != null) items.addAll(values);
        if (kind == Kind.PLAYLIST && showAddPlaylist) items.add(ADD_PLAYLIST);
        notifyDataSetChanged();
    }

    /** Submits a bounded home preview and appends See More only when rows were omitted. */
    public void submitPreview(List<?> values, int limit) {
        items.clear();
        if (values != null) {
            int contentCount = Math.min(values.size(), Math.max(0, limit));
            items.addAll(values.subList(0, contentCount));
            if (kind == Kind.PLAYLIST && showAddPlaylist) items.add(ADD_PLAYLIST);
            if (values.size() > limit) items.add(SEE_MORE);
        }
        notifyDataSetChanged();
    }

    @Override public long getItemId(int position) {
        Object item = items.get(position);
        if (item == ADD_PLAYLIST) return Long.MIN_VALUE;
        if (item == SEE_MORE) return Long.MIN_VALUE + 1;
        if (item instanceof LocalDbHistory && ((LocalDbHistory) item).getId() != null) return ((LocalDbHistory) item).getId();
        if (item instanceof LocalDbPlaylist && ((LocalDbPlaylist) item).getId() != null) return ((LocalDbPlaylist) item).getId();
        if (item instanceof LocalDbCategory && ((LocalDbCategory) item).getId() != null) return ((LocalDbCategory) item).getId();
        return position;
    }

    @NonNull @Override public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 1 || viewType == 2) {
            int layout = viewType == 1 ? R.layout.item_tv_add_playlist : R.layout.item_tv_see_more;
            View actionView = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);

            View cardContainer = actionView.findViewById(R.id.tvSeeMoreCardContainer);

            ViewGroup.LayoutParams params = cardContainer.getLayoutParams();
            params.height = parent.getResources()
                    .getDimensionPixelSize(actionCardHeight);
            cardContainer.setLayoutParams(params);

            return new CardHolder(actionView);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tv_main_card, parent, false);
        return new CardHolder(view);
    }

    @Override public int getItemViewType(int position) {
        if (items.get(position) == ADD_PLAYLIST) return 1;
        if (items.get(position) == SEE_MORE) return 2;
        return 0;
    }

    @Override public void onBindViewHolder(@NonNull CardHolder holder, int position) {
        Object item = items.get(position);
        if (item == ADD_PLAYLIST) {
            ClickUtils.applyTouchEffect(holder.itemView, v -> listener.onAddPlaylistClick());
            return;
        }
        if (item == SEE_MORE) {
            ClickUtils.applyTouchEffect(holder.itemView, v -> listener.onSeeMoreClick(kind));
            return;
        }
        holder.image.setVisibility(View.GONE);
        holder.fallback.setVisibility(View.VISIBLE);
        holder.image.setImageDrawable(null);

        if (item instanceof LocalDbHistory) {
            LocalDbHistory history = (LocalDbHistory) item;
            holder.title.setText(nonEmpty(history.getHistoryName(), holder.itemView.getContext().getString(R.string.app_name)));
            holder.subtitle.setText(history.getHistoryType());
            holder.fallback.setImageResource("AUDIO".equalsIgnoreCase(history.getHistoryType()) ? R.drawable.ic_radio : R.drawable.ic_tv);
            loadImage(holder, history.getHistoryIcon());
            ClickUtils.applyTouchEffect(holder.itemView, v -> listener.onHistoryClick(history));
        } else if (item instanceof LocalDbPlaylist) {
            LocalDbPlaylist playlist = (LocalDbPlaylist) item;
            holder.title.setText(nonEmpty(playlist.getPlaylistName(), holder.itemView.getContext().getString(R.string.nav_playlists)));
            int count = Math.max(0, playlist.getPlaylistCount());
            holder.subtitle.setText(holder.itemView.getResources().getString(R.string.tv_playlist_count_subtitle, count));
            holder.fallback.setImageResource(R.drawable.ic_tv);
            ClickUtils.applyTouchEffect(holder.itemView, v -> listener.onPlaylistClick(playlist));
        } else {
            LocalDbCategory category = (LocalDbCategory) item;
            holder.title.setText(nonEmpty(category.getCategoryName(), ""));
            int count = category.getCategoryCount() == null ? 0 : category.getCategoryCount();
            holder.subtitle.setText(holder.itemView.getResources().getQuantityString(R.plurals.playlist_channel_count, count, count));
            int icon = AppUtils.getCategoryIcon(category.getCategoryIcon());
            holder.fallback.setImageResource(icon != 0 ? icon : R.drawable.ic_tv);
            ClickUtils.applyTouchEffect(holder.itemView, v -> listener.onCategoryClick(category));
        }

        if (kind == Kind.HISTORY) {
            ViewGroup.LayoutParams params = holder.itemView.getLayoutParams();
            params.width = (int) holder.itemView.getResources().getDimension(R.dimen.tv_card_wide_w);
            holder.itemView.setLayoutParams(params);
            View art = holder.itemView.findViewById(R.id.cardArt);
            ViewGroup.LayoutParams artParams = art.getLayoutParams();
            artParams.height = (int) holder.itemView.getResources().getDimension(R.dimen.tv_card_wide_h);
            art.setLayoutParams(artParams);
        }
    }

    private static String nonEmpty(String value, String fallback) {
        String title = value == null || value.trim().isEmpty() ? fallback : value;
        return UIUtils.toCapitalize(title);
    }

    private static void loadImage(CardHolder holder, String image) {
        if (image == null || image.trim().isEmpty()) return;
        holder.image.setVisibility(View.VISIBLE);
        holder.fallback.setVisibility(View.GONE);
        Glide.with(holder.image).load(image).placeholder(R.drawable.bg_tv_card_art).error(R.drawable.bg_tv_card_art).into(holder.image);
    }

    @Override public int getItemCount() { return items.size(); }

    static class CardHolder extends RecyclerView.ViewHolder {
        final ImageView image, fallback;
        final TextView title, subtitle;
        CardHolder(View view) {
            super(view);
            image = view.findViewById(R.id.cardImage);
            fallback = view.findViewById(R.id.cardFallbackIcon);
            title = view.findViewById(R.id.cardTitle);
            subtitle = view.findViewById(R.id.cardSubtitle);
        }
    }
}
