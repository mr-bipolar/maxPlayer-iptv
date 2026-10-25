package com.aaronmaxlab.maxplayer.playlist.adapter;

import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.playlist.models.PlaylistChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class PlaylistAdapter
        extends RecyclerView.Adapter<PlaylistAdapter.PlayViewHolder> {

    private final List<PlaylistChannel> channels = new ArrayList<>();
    private int playingPosition = RecyclerView.NO_POSITION;

    public void setChannels( List<PlaylistChannel> newChannels) {
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
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_channel, parent, false);

        return new PlayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull PlayViewHolder holder,
            int position
    ) {
        PlaylistChannel channel = channels.get(position);

        holder.tvChannelName.setText(channel.name);
        holder.itemView.setSelected(position == playingPosition);
        if (holder.tvChannelNumber != null) {
            holder.tvChannelNumber.setText(String.format(Locale.ROOT, "%02d", position + 1));
        }

        if (position == playingPosition) {
            holder.startEqualizer();
        } else {
            holder.stopEqualizer();
        }

        holder.itemView.setOnClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();

            if (currentPosition != RecyclerView.NO_POSITION && listener != null) {
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
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    static class PlayViewHolder extends RecyclerView.ViewHolder {

        final ImageView ivPlaying;
        final TextView tvChannelName;
        @Nullable final TextView tvChannelNumber;


        PlayViewHolder(@NonNull View itemView) {
            super(itemView);

            ivPlaying = itemView.findViewById(R.id.ivPlaying);
            tvChannelName = itemView.findViewById(R.id.tvChannelName);
            tvChannelNumber = itemView.findViewById(R.id.tvChannelNumber);
        }

        void startEqualizer() {
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
            Drawable drawable = ivPlaying.getDrawable();

            if (drawable instanceof AnimationDrawable) {
                ((AnimationDrawable) drawable).stop();
            }

            ivPlaying.setVisibility(View.GONE);
        }
    }
}
