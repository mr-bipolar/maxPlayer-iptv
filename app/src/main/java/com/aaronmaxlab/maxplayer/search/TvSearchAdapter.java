package com.aaronmaxlab.maxplayer.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class TvSearchAdapter extends RecyclerView.Adapter<TvSearchAdapter.Holder> {

    public interface OnResultClickListener {
        void onResultClick(SqlDbHelper.UnifiedSearchResult result);
    }

    private final OnResultClickListener listener;
    private final List<SqlDbHelper.UnifiedSearchResult> items = new ArrayList<>();

    public TvSearchAdapter(OnResultClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<SqlDbHelper.UnifiedSearchResult> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        SqlDbHelper.UnifiedSearchResult r = items.get(position);
        return ((long) r.getType().ordinal() << 32) | r.getId();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tv_search_result, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        SqlDbHelper.UnifiedSearchResult result = items.get(position);

        String name = result.getName() != null ? result.getName() : "";
        holder.title.setText(UIUtils.toCapitalize(name));

        String sub = (result.getCategoryName() != null && !result.getCategoryName().trim().isEmpty())
                ? result.getCategoryName()
                : (result.getType() != null ? result.getType().getDisplayName() : "");
        holder.subtitle.setText(sub);

        PlaylistType type = result.getType() != null ? result.getType() : PlaylistType.LIVE;
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
                holder.badge.setText("LIST");
                holder.badge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.favorite_muted));
                holder.fallback.setImageResource(R.drawable.ic_nav_playlists);
                break;
            default: // LIVE
                holder.badge.setText("LIVE");
                holder.badge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.accent));
                holder.fallback.setImageResource(R.drawable.ic_tv);
                break;
        }

        String img = result.getImage();
        if (img != null && !img.trim().isEmpty()) {
            holder.image.setVisibility(View.VISIBLE);
            holder.fallback.setVisibility(View.GONE);
            Glide.with(holder.image)
                    .load(img)
                    .placeholder(R.drawable.bg_tv_card_art)
                    .error(R.drawable.bg_tv_card_art)
                    .into(holder.image);
        } else {
            holder.image.setVisibility(View.GONE);
            holder.fallback.setVisibility(View.VISIBLE);
            holder.image.setImageDrawable(null);
        }

        ClickUtils.applyTouchEffect(holder.itemView, v -> {
            if (listener != null) listener.onResultClick(result);
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
