
package com.aaronmaxlab.maxplayer.playlists;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.models.LocalDbPlaylist;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.Playlist;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TvPlaylistsAdapter extends RecyclerView.Adapter<TvPlaylistsAdapter.Holder> {

    public interface OnPlaylistClickListener {
        void onPlaylistClick(LocalDbPlaylist playlist);
    }

    private final OnPlaylistClickListener listener;
    private final List<LocalDbPlaylist> items = new ArrayList<>();

    public TvPlaylistsAdapter(OnPlaylistClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<LocalDbPlaylist> list) {
        items.clear();

        if (list != null) {
            for (LocalDbPlaylist p : list) {
                if (p != null && p.getPlaylistUrl() != null
                        && !p.getPlaylistUrl().trim().isEmpty()) {
                    items.add(p);
                }
            }
        }

        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        LocalDbPlaylist item = items.get(position);
        return item.getId() != null ? item.getId() : position;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tv_playlist_card, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LocalDbPlaylist playlist = items.get(position);

        String name = playlist.getPlaylistName();
        if (name == null || name.trim().isEmpty()) {
            name = UIUtils.extractLabel(playlist.getPlaylistUrl());
        }
        holder.title.setText(UIUtils.toCapitalize(name));

        int count = Math.max(0, playlist.getPlaylistCount());

        String channelCount = holder.itemView.getContext().getResources()
                .getQuantityString(R.plurals.playlist_channel_count, count, count);

        String relativeTime = playlist.getCreatedAt() != null
                ? Playlist.getRelativeTime(playlist.getCreatedAt())
                : "";

        String subtitle = relativeTime.isEmpty()
                ? channelCount
                : channelCount + " • " + relativeTime;

        holder.subtitle.setText(subtitle);

        PlaylistType type = detectType(playlist);

        switch (type) {
            case AUDIO:
                holder.badge.setText("AUDIO");
                holder.badge.setTextColor(ContextCompat.getColor(
                        holder.itemView.getContext(), R.color.blue));
                holder.icon.setImageResource(R.drawable.ic_radio);
                break;

            case VOD:
                holder.badge.setText("VOD");
                holder.badge.setTextColor(ContextCompat.getColor(
                        holder.itemView.getContext(), R.color.primary_highlight));
                holder.icon.setImageResource(R.drawable.ic_play);
                break;

            case LIVE:
            default:
                holder.badge.setText("LIVE");
                holder.badge.setTextColor(ContextCompat.getColor(
                        holder.itemView.getContext(), R.color.accent));
                holder.icon.setImageResource(R.drawable.ic_tv);
                break;
        }

        // Click listener
        ClickUtils.applyTouchEffect(holder.itemView, v -> {
            if (listener != null) {
                listener.onPlaylistClick(playlist);
            }
        });

        // Scroll focused item into view
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.post(() -> {
                    if (v.getParent() instanceof RecyclerView) {
                        RecyclerView rv = (RecyclerView) v.getParent();

                        android.graphics.Rect rect =
                                new android.graphics.Rect();

                        v.getDrawingRect(rect);
                        rv.requestChildRectangleOnScreen(v, rect, false);
                    }
                });
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static PlaylistType detectType(LocalDbPlaylist item) {
        String name = item.getPlaylistName() != null
                ? item.getPlaylistName().toLowerCase(Locale.ROOT)
                : "";

        String url = item.getPlaylistUrl() != null
                ? item.getPlaylistUrl().toLowerCase(Locale.ROOT)
                : "";

        if (name.contains("radio") || name.contains("music")
                || name.contains("audio") || url.contains(".mp3")
                || url.contains(".aac") || url.contains(".ogg")) {
            return PlaylistType.AUDIO;
        }

        if (name.contains("vod") || name.contains("movie")
                || name.contains("series") || name.contains("cinema")
                || name.contains("film") || url.contains(".mp4")
                || url.contains(".mkv")) {
            return PlaylistType.VOD;
        }

        return PlaylistType.LIVE;
    }

    static class Holder extends RecyclerView.ViewHolder {

        final ImageView icon;
        final TextView badge, title, subtitle;

        Holder(@NonNull View itemView) {
            super(itemView);

            icon = itemView.findViewById(R.id.cardIcon);
            badge = itemView.findViewById(R.id.cardTypeBadge);
            title = itemView.findViewById(R.id.cardTitle);
            subtitle = itemView.findViewById(R.id.cardSubtitle);
        }
    }
}
