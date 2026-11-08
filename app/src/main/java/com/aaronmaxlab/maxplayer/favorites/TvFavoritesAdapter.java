package com.aaronmaxlab.maxplayer.favorites;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.models.LocalDbFavorites;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class TvFavoritesAdapter extends RecyclerView.Adapter<TvFavoritesAdapter.Holder> {

    public interface OnFavoriteClickListener {
        void onFavoriteClick(LocalDbFavorites favorite);
    }

    private final OnFavoriteClickListener listener;
    private final List<LocalDbFavorites> items = new ArrayList<>();

    public TvFavoritesAdapter(OnFavoriteClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<LocalDbFavorites> list) {
        items.clear();
        if (list != null) {
            for (LocalDbFavorites f : list) {
                if (f != null && f.getFavUrl() != null && !f.getFavUrl().trim().isEmpty()) {
                    items.add(f);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        LocalDbFavorites item = items.get(position);
        return item.getId() != null ? item.getId() : position;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tv_favorite_card, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LocalDbFavorites favorite = items.get(position);

        String name = favorite.getFavName();
        if (name == null || name.trim().isEmpty()) {
            name = UIUtils.extractLabel(favorite.getFavUrl());
        }
        holder.title.setText(UIUtils.toCapitalize(name));

        String tag = favorite.getFavTag();
        holder.subtitle.setText(tag != null && !tag.trim().isEmpty() ? tag : favorite.getFavType());

        PlaylistType type = PlaylistType.fromDisplayName(favorite.getFavType());
        switch (type) {
            case AUDIO:
                holder.badge.setText("AUDIO");
                holder.badge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.blue));
                holder.fallback.setImageResource(R.drawable.ic_radio);
                break;
            case VOD:
                holder.badge.setText("VOD");
                holder.badge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.primary_highlight));
                holder.fallback.setImageResource(R.drawable.ic_play);
                break;
            case PLAYLIST:
                holder.badge.setText("PLAYLIST");
                holder.badge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.favorite_muted));
                holder.fallback.setImageResource(R.drawable.ic_nav_playlists);
                break;
            case LIVE:
            default:
                holder.badge.setText("LIVE");
                holder.badge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.accent));
                holder.fallback.setImageResource(R.drawable.ic_tv);
                break;
        }

        String iconUrl = favorite.getFavIcon();
        if (iconUrl != null && !iconUrl.trim().isEmpty()) {
            holder.image.setVisibility(View.VISIBLE);
            holder.fallback.setVisibility(View.GONE);
            Glide.with(holder.image)
                    .load(iconUrl)
                    .placeholder(R.drawable.bg_tv_card_art)
                    .error(R.drawable.bg_tv_card_art)
                    .into(holder.image);
        } else {
            holder.image.setVisibility(View.GONE);
            holder.fallback.setVisibility(View.VISIBLE);
            holder.image.setImageDrawable(null);
        }

        // Ensure focused item scrolls into view
        // Ensure focused item scrolls into view
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.post(() -> {
                    if (v.getParent() instanceof RecyclerView) {
                        RecyclerView rv = (RecyclerView) v.getParent();

                        android.graphics.Rect rect = new android.graphics.Rect();
                        v.getDrawingRect(rect);

                        rv.requestChildRectangleOnScreen(v, rect, false);
                    }
                });
            }
        });

        // Handle favorite selection only when clicked
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFavoriteClick(favorite);
            }
        });
    }



    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image, fallback;
        final TextView badge, title, subtitle;

        Holder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.cardImage);
            fallback = itemView.findViewById(R.id.cardFallbackIcon);
            badge = itemView.findViewById(R.id.cardTypeBadge);
            title = itemView.findViewById(R.id.cardTitle);
            subtitle = itemView.findViewById(R.id.cardSubtitle);
        }
    }
}
