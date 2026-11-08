
package com.aaronmaxlab.maxplayer.database.adapter;

import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.models.LocalDbChannel;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public final class ChannelGridAdapter
        extends RecyclerView.Adapter<ChannelGridAdapter.Holder> {

    public interface Listener {
        void onChannelSelected(LocalDbChannel channel);
    }

    private final List<LocalDbChannel> channels = new ArrayList<>();
    private final Listener listener;

    public ChannelGridAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<LocalDbChannel> values) {
        channels.clear();

        if (values != null) {
            channels.addAll(values);
        }

        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        Integer id = channels.get(position).getId();
        return id == null ? position : id.longValue();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tv_channel_grid, parent, false);

        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull Holder holder,
            int position
    ) {
        LocalDbChannel channel = channels.get(position);

        // Channel name
        holder.name.setText(
                channel.getChannelName() == null
                        ? ""
                        : channel.getChannelName()
        );

        // Channel logo
        String image = channel.getChannelImage();

        if (image == null || image.trim().isEmpty()) {
            Glide.with(holder.logo).clear(holder.logo);
            holder.logo.setImageResource(R.drawable.ic_tv);
        } else {
            Glide.with(holder.logo)
                    .load(image)
                    .placeholder(R.drawable.ic_tv)
                    .error(R.drawable.ic_tv)
                    .centerInside()
                    .into(holder.logo);
        }

        // Channel selection
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChannelSelected(channel);
            }
        });

        // Keep focused item visible during D-pad navigation
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.post(() -> {
                    if (v.getParent() instanceof RecyclerView) {
                        RecyclerView recyclerView =
                                (RecyclerView) v.getParent();

                        Rect rect = new Rect();
                        v.getDrawingRect(rect);

                        recyclerView.requestChildRectangleOnScreen(
                                v,
                                rect,
                                false
                        );
                    }
                });
            }
        });
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {

        final ImageView logo;
        final TextView name;

        Holder(@NonNull View view) {
            super(view);

            logo = view.findViewById(R.id.logo);
            name = view.findViewById(R.id.name);
        }
    }
}
