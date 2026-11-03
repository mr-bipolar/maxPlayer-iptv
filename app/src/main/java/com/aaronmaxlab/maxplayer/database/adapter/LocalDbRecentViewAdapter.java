package com.aaronmaxlab.maxplayer.database.adapter;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.models.LocalDbRecentView;
import com.aaronmaxlab.maxplayer.databinding.RecentViewBinding;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import java.util.ArrayList;
import java.util.List;

public class LocalDbRecentViewAdapter
        extends RecyclerView.Adapter<LocalDbRecentViewAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(LocalDbRecentView item);
        void onFavoriteClick(LocalDbRecentView item);
        void onDeleteClick(LocalDbRecentView item);
    }

    private final List<LocalDbRecentView> items = new ArrayList<>();
    private final Listener listener;
    private final Context context;
    private final SqlDbHelper sqlDbHelper;

    public LocalDbRecentViewAdapter( Context context,  Listener listener) {
        this.listener = listener;
        this.context = context;
        this.sqlDbHelper = new SqlDbHelper(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {

        RecentViewBinding binding = RecentViewBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );

        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder, int position) {


        holder.bind(items.get(position), sqlDbHelper.isFavorite(items.get(position).getChannelUrl()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void submitList(List<LocalDbRecentView> list) {
        items.clear();

        if (list != null) {
            items.addAll(list);
        }

        notifyDataSetChanged();
    }

    public void removeItem(LocalDbRecentView item) {
        int position = items.indexOf(item);

        if (position >= 0) {
            items.remove(position);
            notifyItemRemoved(position);
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final RecentViewBinding binding;

        ViewHolder(RecentViewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(LocalDbRecentView item, boolean isFav) {
            // Change these getters if your model uses different names.
            binding.mediaTitle.setText(UIUtils.toCapitalize(item.getChannelName()));
            binding.mediaUrl.setText(item.getChannelUrl());

            binding.mediaIcon.setImageResource(item.getChannelType().contains("AUDIO") ? R.drawable.ic_radio : R.drawable.ic_play);

            binding.mediaFav.setImageResource(
                    isFav
                            ? R.drawable.ic_heart_filled
                            : R.drawable.ic_heart_outline
            );


            binding.getRoot().setOnClickListener(v ->
                    listener.onItemClick(item));

            binding.mediaFav.setOnClickListener(v ->
                    listener.onFavoriteClick(item));

            binding.mediaDelete.setOnClickListener(v ->
                    listener.onDeleteClick(item));
        }
    }
}