
package com.aaronmaxlab.maxplayer.database.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.models.LocalDbChannel;
import com.aaronmaxlab.maxplayer.databinding.ChannelViewBinding;
import com.aaronmaxlab.maxplayer.databinding.ChannelViewGridBinding;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class LocalDbChannelAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_LIST = 0;
    private static final int TYPE_GRID = 1;

    private final LayoutInflater inflater;
    private final List<LocalDbChannel> originalList = new ArrayList<>();
    private final List<LocalDbChannel> filteredList = new ArrayList<>();

    private boolean gridMode = false;
    private boolean ascending = true;
    private final boolean showMenu;

    private final OnChannelClickListener listener;

    public interface OnChannelClickListener {
        void onChannelClick(LocalDbChannel channel);
        void onMenuClick(LocalDbChannel channel, View anchor);
    }

    public LocalDbChannelAdapter(
            Context context,
            boolean showMenu ,
            List<LocalDbChannel> channels,
            OnChannelClickListener listener
    ) {
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
        this.showMenu = showMenu;

        if (channels != null) {
            originalList.addAll(channels);
            filteredList.addAll(channels);
        }

        setHasStableIds(false);
    }

    @Override
    public int getItemViewType(int position) {
        return gridMode ? TYPE_GRID : TYPE_LIST;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        if (viewType == TYPE_GRID) {
            ChannelViewGridBinding binding =
                    ChannelViewGridBinding.inflate(inflater, parent, false);

            return new GridHolder(binding);
        }

        ChannelViewBinding binding =
                ChannelViewBinding.inflate(inflater, parent, false);

        return new ListHolder(binding);
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position
    ) {
        LocalDbChannel channel = filteredList.get(position);

        if (holder instanceof ListHolder) {
            ((ListHolder) holder).bind(channel);
        } else if (holder instanceof GridHolder) {
            ((GridHolder) holder).bind(channel);
        }


    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    private String selectedCategoryName;

    public void setCategoryName(String categoryName) {
        this.selectedCategoryName = categoryName;
        notifyDataSetChanged();
    }

    //  LIST HOLDER

    private class ListHolder extends RecyclerView.ViewHolder {

        private final ChannelViewBinding binding;

        ListHolder(ChannelViewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(LocalDbChannel channel) {

            binding.channelName.setText(channel.getChannelName());
            binding.channelCategory.setText(selectedCategoryName);
            binding.channelIcon.setImageResource(R.drawable.ic_tv);

            ClickUtils.applyTouchEffect(itemView, view -> {
                if (listener != null) {
                    listener.onChannelClick(channel);
                }
            });

            binding.channelMenu.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMenuClick(channel, v);
                }
            });


        }
    }

    // GRID HOLDER

    private class GridHolder extends RecyclerView.ViewHolder {

        private final ChannelViewGridBinding binding;

        GridHolder(ChannelViewGridBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(LocalDbChannel channel) {

            binding.channelName.setText(channel.getChannelName());
            loadChannelLogo(binding.channelGridIcon, channel);
            binding.channelMenu.setVisibility(
                    showMenu ? View.VISIBLE : View.GONE
            );

            ClickUtils.applyTouchEffect(itemView, view -> {
                if (listener != null) {
                    listener.onChannelClick(channel);
                }
            });

            binding.channelMenu.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMenuClick(channel, v);
                }
            });

        }
    }

    // SEARCH

    public void search(String query) {

        filteredList.clear();

        if (query == null || query.trim().isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            String keyword = query.trim().toLowerCase(Locale.ROOT);

            for (LocalDbChannel channel : originalList) {
                String name = channel.getChannelName() == null
                        ? ""
                        : channel.getChannelName().toLowerCase(Locale.ROOT);

                if (name.contains(keyword)) {
                    filteredList.add(channel);
                }
            }
        }

        applySort();
    }

    // SORT SET UP

    public void sortUpDown() {
        ascending = !ascending;
        applySort();
    }

    private void applySort() {

        Comparator<LocalDbChannel> comparator =
                Comparator.comparing(
                        channel -> channel.getChannelName() == null
                                ? ""
                                : channel.getChannelName(),
                        String.CASE_INSENSITIVE_ORDER
                );

        if (!ascending) {
            comparator = comparator.reversed();
        }

        filteredList.sort(comparator);
        notifyDataSetChanged();
    }

    public boolean isAscending() {
        return ascending;
    }

    // LIST / GRID

    public void setGridMode(boolean enabled) {

        if (gridMode == enabled) return;

        gridMode = enabled;
        notifyDataSetChanged();
    }


    // IMAGE FOR LOGO
    private void loadChannelLogo(ImageView imageView, LocalDbChannel channel) {
        String imageUrl = channel.getChannelImage();

        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            Glide.with(imageView).clear(imageView);

            imageView.setImageResource(R.drawable.ic_tv);

            ImageViewCompat.setImageTintList(
                    imageView,
                    ColorStateList.valueOf(
                            ContextCompat.getColor(
                                    imageView.getContext(),
                                    R.color.ink_faint
                            )
                    )
            );

            return;
        }

        ImageViewCompat.setImageTintList(imageView, null);

        boolean isPoster = channel.getChannelCategory() != null
                && channel.getChannelCategory() >= 3
                && channel.getChannelCategory() <= 4;

        int padding = isPoster ? 0 : dpToPx(20, imageView);

        imageView.setPadding(padding, padding, padding, padding);

        Glide.with(imageView)
                .load(imageUrl)
                .placeholder(R.drawable.ic_tv)
                .error(R.drawable.ic_tv)
                .override(120, 120)
                .centerInside()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(imageView);
    }


    private int dpToPx(int dp, ImageView imageView) {
        return (int) (dp * imageView.getResources()
                .getDisplayMetrics().density + 0.5f);
    }


}
