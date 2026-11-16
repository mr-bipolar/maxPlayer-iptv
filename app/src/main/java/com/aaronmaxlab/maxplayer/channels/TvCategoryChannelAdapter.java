package com.aaronmaxlab.maxplayer.channels;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.models.LocalDbChannel;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class TvCategoryChannelAdapter extends RecyclerView.Adapter<TvCategoryChannelAdapter.Holder> {

    public interface Listener {
        void onChannelClick(LocalDbChannel channel, boolean isRadio);
    }

    private final List<LocalDbChannel> channels = new ArrayList<>();
    private final Listener listener;
    private String categoryName;

    public TvCategoryChannelAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<LocalDbChannel> list) {
        channels.clear();
        if (list != null) {
            channels.addAll(list);
        }
        notifyDataSetChanged();
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        Integer id = channels.get(position).getId();
        return id != null ? id.longValue() : position;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tv_category_channel_card, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LocalDbChannel channel = channels.get(position);

        String name = channel.getChannelName();
        holder.name.setText(name != null ? UIUtils.toCapitalize(name) : "");

        holder.category.setText(categoryName != null ? categoryName : "");

        int catId = channel.getChannelCategory() != null ? channel.getChannelCategory() : 0;
        boolean isRadio = isRadioCategory(catId);

        if (isRadio) {
            holder.badge.setText("AUDIO");
            holder.badge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.blue));
            holder.fallback.setImageResource(R.drawable.ic_radio);
        } else {
            holder.badge.setText("LIVE");
            holder.badge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.accent));
            holder.fallback.setImageResource(R.drawable.ic_tv);
        }

        String image = channel.getChannelImage();
        if (image != null && !image.trim().isEmpty()) {
            holder.image.setVisibility(View.VISIBLE);
            holder.fallback.setVisibility(View.GONE);
            Glide.with(holder.image)
                    .load(image)
                    .placeholder(isRadio ? R.drawable.ic_radio : R.drawable.ic_tv)
                    .error(isRadio ? R.drawable.ic_radio : R.drawable.ic_tv)
                    .centerInside()
                    .into(holder.image);
        } else {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setVisibility(View.GONE);
            holder.fallback.setVisibility(View.VISIBLE);
        }

        ClickUtils.applyTouchEffect(holder.itemView, v -> {
            if (listener != null) {
                listener.onChannelClick(channel, isRadio);
            }
        });
    }

    private boolean isRadioCategory(int categoryIndex) {
        return categoryIndex == 17 || categoryIndex == 18 || categoryIndex == 9;
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final ImageView fallback;
        final TextView badge;
        final TextView name;
        final TextView category;

        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.cardImage);
            fallback = view.findViewById(R.id.cardFallback);
            badge = view.findViewById(R.id.cardBadge);
            name = view.findViewById(R.id.cardTitle);
            category = view.findViewById(R.id.cardCategory);
        }
    }
}
