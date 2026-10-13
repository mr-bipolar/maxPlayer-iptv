package com.aaronmaxlab.maxplayer.filepicker.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.filepicker.MediaFile;

import java.util.ArrayList;
import java.util.List;

public class MediaFileAdapter
        extends RecyclerView.Adapter<MediaFileAdapter.MediaViewHolder> {

    public interface OnMediaClickListener {
        void onMediaClick(MediaFile mediaFile);
    }

    private final List<MediaFile> files = new ArrayList<>();
    private final OnMediaClickListener listener;

    public MediaFileAdapter(OnMediaClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<MediaFile> newFiles) {
        files.clear();
        files.addAll(newFiles);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media_file, parent, false);

        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull MediaViewHolder holder,
            int position
    ) {
        MediaFile file = files.get(position);

        holder.txtName.setText(file.getName());

        // color for folders
        int folderColor = ContextCompat.getColor(holder.imageView.getContext(), R.color.ink_dim);
        ColorStateList whiteAlpha = ColorStateList.valueOf(folderColor);
        // color for video files
        int videoColor = ContextCompat.getColor(holder.imageView.getContext(), R.color.ok);
        ColorStateList accent = ColorStateList.valueOf(videoColor);
        // color for playlists
        int playlistColor = ContextCompat.getColor(holder.imageView.getContext(), R.color.accent);
        ColorStateList playlist = ColorStateList.valueOf(playlistColor);

        // 1. Calculate your default/standard size in pixels (e.g., 24dp)
        int defaultSizeInPx = (int) (16 * holder.imageView.getResources().getDisplayMetrics().density);

        // 2. Calculate your custom size for video if needed (e.g., 12dp or whatever size you want)
        int videoSizeInPx = (int) (24 * holder.imageView.getResources().getDisplayMetrics().density);

        switch (file.getType()) {
            case FOLDER:
                holder.imageView.setImageResource(R.drawable.ic_folder);
                ImageViewCompat.setImageTintList(holder.imageView, whiteAlpha);
                holder.txtType.setText("Folder");
                holder.btnTrailing.setImageResource(R.drawable.ic_chevron_right);
                holder.iconBg.setBackgroundResource(R.drawable.bg_card_icon_file);
                // Reset to default size for non-video items
                ViewGroup.LayoutParams folderParams = holder.btnTrailing.getLayoutParams();
                folderParams.width = defaultSizeInPx;
                folderParams.height = defaultSizeInPx;
                holder.btnTrailing.setLayoutParams(folderParams);
                break;


            case VIDEO:
                holder.imageView.setImageResource(R.drawable.ic_video_filled);
                ImageViewCompat.setImageTintList(holder.imageView, accent);
                holder.txtType.setText("Video");
                holder.btnTrailing.setImageResource(R.drawable.ic_play_round);
                holder.iconBg.setBackgroundResource(R.drawable.bg_card_icon_playlist);
                // Apply custom size only for video
                ViewGroup.LayoutParams videoParams = holder.btnTrailing.getLayoutParams();
                videoParams.width = videoSizeInPx;
                videoParams.height = videoSizeInPx;
                holder.btnTrailing.setLayoutParams(videoParams);
                break;

            case PLAYLIST:
                holder.imageView.setImageResource(R.drawable.ic_playlist_filled);
                ImageViewCompat.setImageTintList(holder.imageView, playlist);
                holder.txtType.setText("IPTV playlist");
                holder.btnTrailing.setImageResource(R.drawable.ic_chevron_right);
                holder.iconBg.setBackgroundResource(R.drawable.bg_card_icon_playlist);
                // Reset to default size for non-video items
                ViewGroup.LayoutParams playlistParams = holder.btnTrailing.getLayoutParams();
                playlistParams.width = defaultSizeInPx;
                playlistParams.height = defaultSizeInPx;
                holder.btnTrailing.setLayoutParams(playlistParams);
                break;
        }

        holder.itemView.setOnClickListener(
                v -> listener.onMediaClick(file)
        );
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static  class MediaViewHolder extends RecyclerView.ViewHolder {

        final ImageView imageView;
        final TextView txtName;
        final TextView txtType;
        final ImageView btnTrailing;
        final FrameLayout iconBg;

        MediaViewHolder(@NonNull View itemView) {
            super(itemView);

            imageView = itemView.findViewById(R.id.imageFileIcon);
            txtName = itemView.findViewById(R.id.txtName);
            txtType = itemView.findViewById(R.id.txtType);
            btnTrailing = itemView.findViewById(R.id.btnTrailing);
            iconBg = itemView.findViewById(R.id.iconBg);
        }
    }
}
