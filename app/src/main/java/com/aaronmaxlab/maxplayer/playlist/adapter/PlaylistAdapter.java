
package com.aaronmaxlab.maxplayer.playlist.adapter;

import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistChannel;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlaylistAdapter
        extends RecyclerView.Adapter<PlaylistAdapter.PlayViewHolder> {

    private static final int TYPE_LIST = 0;
    private static final int TYPE_GRID = 1;

    private final List<PlaylistChannel> channels = new ArrayList<>();
    private int playingPosition = RecyclerView.NO_POSITION;
    private boolean gridMode = false;

    public void setGridMode(boolean gridMode) {
        if (this.gridMode != gridMode) {
            this.gridMode = gridMode;
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemViewType(int position) {
        return gridMode ? TYPE_GRID : TYPE_LIST;
    }

    public void setChannels(List<PlaylistChannel> newChannels) {
        channels.clear();

        if (newChannels != null) {
            channels.addAll(newChannels);
        }

        playingPosition = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    public void setPlayingPosition(int position) {
        int previousPosition = playingPosition;

        if (position < 0 || position >= channels.size()) {
            position = RecyclerView.NO_POSITION;
        }

        playingPosition = position;

        if (previousPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousPosition);
        }

        if (playingPosition != RecyclerView.NO_POSITION
                && playingPosition != previousPosition) {
            notifyItemChanged(playingPosition);
        }
    }

    public int getPlayingPosition() {
        return playingPosition;
    }

    public interface OnChannelClickListener {
        void onChannelClick(PlaylistChannel channel, int position);
    }

    private OnChannelClickListener listener;

    public void setOnChannelClickListener(OnChannelClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlayViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        int layout = viewType == TYPE_GRID
                ? R.layout.item_channel_grid
                : R.layout.item_channel;

        View view = LayoutInflater.from(parent.getContext())
                .inflate(layout, parent, false);

        return new PlayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull PlayViewHolder holder,
            int position
    ) {
        PlaylistChannel channel = channels.get(position);

        if (gridMode) {
            if (holder.channelName != null) {
                holder.channelName.setText(channel.name);
            }

            if (holder.channelIcon != null) {
                Glide.with(holder.itemView)
                        .load(channel.logoUrl)
                        .placeholder(R.drawable.ic_tv)
                        .error(R.drawable.ic_tv)
                        .centerInside()
                        .into(holder.channelIcon);
            }

            if (holder.statusIcon != null) {
                holder.statusIcon.setVisibility( position == playingPosition ? View.VISIBLE : View.INVISIBLE);
            }


        } else {
            if (holder.tvChannelName != null) {
                holder.tvChannelName.setText(channel.name);
            }

            holder.itemView.setSelected(position == playingPosition);

            if (holder.tvChannelNumber != null) {
                holder.tvChannelNumber.setText(
                        String.format(Locale.ROOT, "%02d", position + 1)
                );
            }


            if (position == playingPosition) {
                holder.startEqualizer();
            } else {
                holder.stopEqualizer();
            }
        }

        holder.itemView.setOnClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();

            if (currentPosition != RecyclerView.NO_POSITION
                    && listener != null) {
                listener.onChannelClick(
                        channels.get(currentPosition),
                        currentPosition
                );
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull PlayViewHolder holder) {
        holder.stopEqualizer();

        if (holder.channelIcon != null) {
            Glide.with(holder.itemView).clear(holder.channelIcon);
        }

        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    static class PlayViewHolder extends RecyclerView.ViewHolder {

        @Nullable final ImageView ivPlaying;
        @Nullable final TextView tvChannelName;
        @Nullable final TextView tvChannelNumber;
        @Nullable final TextView channelName;
        @Nullable final ImageView channelIcon;
        @Nullable final FrameLayout statusIcon;

        PlayViewHolder(@NonNull View itemView) {
            super(itemView);

            ivPlaying = itemView.findViewById(R.id.ivPlaying);
            tvChannelName = itemView.findViewById(R.id.tvChannelName);
            tvChannelNumber = itemView.findViewById(R.id.tvChannelNumber);
            channelName = itemView.findViewById(R.id.channelName);
            channelIcon = itemView.findViewById(R.id.channelGridIcon);
            statusIcon = itemView.findViewById(R.id.channelStatus);
        }

        void startEqualizer() {
            if (ivPlaying == null) return;

            Drawable drawable = ivPlaying.getDrawable();

            if (drawable instanceof AnimationDrawable) {
                AnimationDrawable animation = (AnimationDrawable) drawable;

                ivPlaying.setVisibility(View.VISIBLE);

                if (!animation.isRunning()) {
                    animation.start();
                }
            } else {
                ivPlaying.setVisibility(View.VISIBLE);
            }
        }

        void stopEqualizer() {
            if (ivPlaying == null) return;

            Drawable drawable = ivPlaying.getDrawable();

            if (drawable instanceof AnimationDrawable) {
                ((AnimationDrawable) drawable).stop();
            }

            ivPlaying.setVisibility(View.GONE);
        }
    }
}
