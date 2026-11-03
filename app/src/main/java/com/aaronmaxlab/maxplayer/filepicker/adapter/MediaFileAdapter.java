package com.aaronmaxlab.maxplayer.filepicker.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.filepicker.MediaFile;

import java.util.ArrayList;
import java.util.List;

public class MediaFileAdapter
        extends ListAdapter<MediaFile, MediaFileAdapter.MediaViewHolder> {

    public interface OnMediaClickListener {
        void onMediaClick(MediaFile mediaFile);
    }

    private static final int TRAILING_DEFAULT_DP = 16;
    private static final int TRAILING_VIDEO_DP = 24;

    private static final DiffUtil.ItemCallback<MediaFile> DIFF =
            new DiffUtil.ItemCallback<MediaFile>() {
                @Override
                public boolean areItemsTheSame(@NonNull MediaFile a, @NonNull MediaFile b) {
                    // Replace with a unique id/path/uri if MediaFile has one
                    return a.getType() == b.getType() && a.getName().equals(b.getName());
                }


                @Override
                public boolean areContentsTheSame(@NonNull MediaFile a, @NonNull MediaFile b) {
                    // Implement equals() in MediaFile for a stricter check
                    return a.getType() == b.getType() && a.getName().equals(b.getName());
                }
            };

    private final OnMediaClickListener listener;
    private Style[] styles; // indexed by MediaFile.Type ordinal, built lazily once

    public MediaFileAdapter(OnMediaClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** Keeps the original API. A copy is made so callers can safely mutate their list. */
    @Override
    public void submitList(List<MediaFile> newFiles) {
        super.submitList(newFiles == null ? null : new ArrayList<>(newFiles));
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        if (styles == null) styles = buildStyles(context);

        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_media_file, parent, false);
        MediaViewHolder holder = new MediaViewHolder(view);

        view.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onMediaClick(getItem(pos));
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        MediaFile file = getItem(position);
        Style s = styles[file.getType().ordinal()];

        holder.txtName.setText(file.getName());
        holder.txtType.setText(s.label);
        holder.imageView.setImageResource(s.iconRes);
        ImageViewCompat.setImageTintList(holder.imageView, s.tint);
        holder.btnTrailing.setImageResource(s.trailingRes);
        holder.iconBg.setBackgroundResource(s.iconBgRes);

        ViewGroup.LayoutParams lp = holder.btnTrailing.getLayoutParams();
        if (lp.width != s.trailingSizePx || lp.height != s.trailingSizePx) {
            lp.width = s.trailingSizePx;
            lp.height = s.trailingSizePx;
            holder.btnTrailing.setLayoutParams(lp);
        }
    }

    // ---------- Style table ----------

    private static Style[] buildStyles(Context c) {
        float density = c.getResources().getDisplayMetrics().density;
        int defaultPx = (int) (TRAILING_DEFAULT_DP * density);
        int videoPx = (int) (TRAILING_VIDEO_DP * density);

        MediaFile.Type[] types = MediaFile.Type.values();
        Style[] out = new Style[types.length];

        for (MediaFile.Type t : types) {
            switch (t) {
                case FOLDER:
                    out[t.ordinal()] = new Style(c, "Folder", R.drawable.ic_folder, R.color.ink_dim,
                            R.drawable.ic_chevron_right, R.drawable.bg_card_icon_file, defaultPx);
                    break;
                case VIDEO:
                    out[t.ordinal()] = new Style(c, "Video", R.drawable.ic_video_filled, R.color.ok,
                            R.drawable.ic_play_round, R.drawable.bg_card_icon_playlist, videoPx);
                    break;
                case PLAYLIST:
                    out[t.ordinal()] = new Style(c, "IPTV playlist", R.drawable.ic_playlist_filled, R.color.accent,
                            R.drawable.ic_chevron_right, R.drawable.bg_card_icon_playlist, defaultPx);
                    break;
                case AUDIO:
                    out[t.ordinal()] = new Style(c, "Audio", R.drawable.ic_audio, R.color.blue,
                            R.drawable.ic_chevron_right, R.drawable.bg_card_icon_file, defaultPx);
                    break;
                default:
                    out[t.ordinal()] = new Style(c, "File", R.drawable.ic_file, R.color.ink_dim,
                            R.drawable.ic_chevron_right, R.drawable.bg_card_icon_file, defaultPx);
                    break;
            }
        }
        return out;
    }

    private static final class Style {
        final String label;
        @DrawableRes final int iconRes;
        final ColorStateList tint;
        @DrawableRes final int trailingRes;
        @DrawableRes final int iconBgRes;
        final int trailingSizePx;

        Style(Context c, String label, @DrawableRes int iconRes, @ColorRes int colorRes,
              @DrawableRes int trailingRes, @DrawableRes int iconBgRes, int trailingSizePx) {
            this.label = label;
            this.iconRes = iconRes;
            this.tint = ColorStateList.valueOf(ContextCompat.getColor(c, colorRes));
            this.trailingRes = trailingRes;
            this.iconBgRes = iconBgRes;
            this.trailingSizePx = trailingSizePx;
        }
    }

    // ---------- ViewHolder ----------

    static final class MediaViewHolder extends RecyclerView.ViewHolder {
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