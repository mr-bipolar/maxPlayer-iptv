package com.aaronmaxlab.maxplayer.database.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.models.LocalDbPlaylist;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.Playlist;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import java.util.ArrayList;
import java.util.List;

public class LocalDbPlaylistAdapter
        extends RecyclerView.Adapter<LocalDbPlaylistAdapter.PlaylistViewHolder> {

    private final List<LocalDbPlaylist> playlists = new ArrayList<>();
    private final OnPlaylistClickListener clickListener;
    private final OnPlaylistMenuClickListener menuClickListener;
    private final Context context;
    private final SqlDbHelper sqlDbHelper;

    public interface OnPlaylistClickListener {
        void onPlaylistClick(LocalDbPlaylist playlist);
    }

    public interface OnPlaylistMenuClickListener {
        void onMenuClick(View anchor, LocalDbPlaylist playlist);
    }

    public LocalDbPlaylistAdapter(
            Context context,
            OnPlaylistClickListener clickListener,
            OnPlaylistMenuClickListener menuClickListener
    ) {
        this.context = context;
        this.clickListener = clickListener;
        this.menuClickListener = menuClickListener;
        this.sqlDbHelper = new SqlDbHelper(context);
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.playlist_view, parent, false);

        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull PlaylistViewHolder holder,
            int position
    ) {
        LocalDbPlaylist playlist = playlists.get(position);


        holder.playlistName.setText(UIUtils.toCapitalize(playlist.getPlaylistName()));
        holder.bookmarkIconView.setVisibility(sqlDbHelper.isFavorite(playlist.getPlaylistUrl()) ? View.VISIBLE  : View.INVISIBLE);

        int count = playlist.getPlaylistCount();

        String relativeTime = Playlist.getRelativeTime(playlist.getCreatedAt());


        String channelCount = holder.itemView.getContext()
                .getResources()
                .getQuantityString(
                        R.plurals.playlist_channel_count,
                        count,
                        count
                );

        String subtitle = relativeTime.isEmpty()
                ? channelCount
                : channelCount + " • " + relativeTime;

        holder.playlistSubText.setText(subtitle);

        ClickUtils.applyTouchEffect(holder.itemView, v -> {
            if (clickListener != null) {
                clickListener.onPlaylistClick(playlist);
            }
        });

        holder.playlistMenuBtn.setOnTouchListener((v, event) -> {
            if (v.getParent() != null) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return false;
        });

        holder.playlistMenuBtn.setOnClickListener(v -> {
            if (menuClickListener != null) {
                menuClickListener.onMenuClick(v, playlist);
            }
        });
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    public void submitList(List<LocalDbPlaylist> newPlaylists) {
        playlists.clear();

        if (newPlaylists != null) {
            playlists.addAll(newPlaylists);
        }

        notifyDataSetChanged();
    }

    public LocalDbPlaylist getPlaylistAt(int position) {
        if (position < 0 || position >= playlists.size()) {
            return null;
        }

        return playlists.get(position);
    }

    static class PlaylistViewHolder extends RecyclerView.ViewHolder {

        final TextView playlistName;
        final TextView playlistSubText;
        final FrameLayout playlistMenuBtn;
        final FrameLayout bookmarkIconView;
        final ImageView playlistIcon;

        PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);

            playlistName = itemView.findViewById(R.id.playlistName);
            playlistSubText = itemView.findViewById(R.id.playlistSubText);
            playlistMenuBtn = itemView.findViewById(R.id.playlistMenuBtn);
            bookmarkIconView = itemView.findViewById(R.id.bookmarkIconView);
            playlistIcon = itemView.findViewById(R.id.playlistIcon);
        }
    }
}

