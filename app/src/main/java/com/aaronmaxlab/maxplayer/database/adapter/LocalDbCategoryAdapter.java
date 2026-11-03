package com.aaronmaxlab.maxplayer.database.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.models.LocalDbCategory;
import com.aaronmaxlab.maxplayer.utils.AppUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class LocalDbCategoryAdapter extends RecyclerView.Adapter<LocalDbCategoryAdapter.CategoryHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(LocalDbCategory category);
    }

    public enum FilterType {
        ALL,
        POPULATED,
        EMPTY
    }

    public enum SortType {
        NAME_ASC,
        NAME_DESC,
        MOST_CHANNELS,
        FEWEST_CHANNELS
    }

    private final Context context;
    private final List<LocalDbCategory> originalList = new ArrayList<>();
    private final List<LocalDbCategory> filteredList = new ArrayList<>();
    private final OnCategoryClickListener listener;

    private String searchQuery = "";
    private FilterType filterType = FilterType.ALL;
    private SortType sortType = SortType.NAME_ASC;

    public LocalDbCategoryAdapter(
            Context context,
            List<LocalDbCategory> categories,
            OnCategoryClickListener listener
    ) {
        this.context = context;
        this.listener = listener;

        if (categories != null) {
            originalList.addAll(categories);
        }

        applyFilters();
    }

    @NonNull
    @Override
    public CategoryHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.category_view, parent, false);

        return new CategoryHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull CategoryHolder holder,
            int position
    ) {
        LocalDbCategory category = filteredList.get(position);
        holder.bind(category);
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    public void setCategories(List<LocalDbCategory> categories) {
        originalList.clear();

        if (categories != null) {
            originalList.addAll(categories);
        }

        applyFilters();
    }

    public void search(String query) {
        searchQuery = query == null ? "" : query.trim();
        applyFilters();
    }

    public void setFilter(FilterType filter) {
        filterType = filter;
        applyFilters();
    }

    public void setSort(SortType sort) {
        sortType = sort;
        applyFilters();
    }

    private void applyFilters() {
        filteredList.clear();

        for (LocalDbCategory category : originalList) {
            String name = category.getCategoryName() == null
                    ? ""
                    : category.getCategoryName();

            int count = category.getCategoryCount() == null
                    ? 0
                    : category.getCategoryCount();

            // Search
            if (!name.toLowerCase(Locale.ROOT)
                    .contains(searchQuery.toLowerCase(Locale.ROOT))) {
                continue;
            }

            // Filter
            if (filterType == FilterType.POPULATED && count <= 0) {
                continue;
            }

            if (filterType == FilterType.EMPTY && count > 0) {
                continue;
            }

            filteredList.add(category);
        }

        // Sort
        Comparator<LocalDbCategory> comparator;

        switch (sortType) {
            case NAME_DESC:
                comparator = Comparator.comparing(
                        LocalDbCategory::getCategoryName,
                        String.CASE_INSENSITIVE_ORDER
                ).reversed();
                break;

            case MOST_CHANNELS:
                comparator = Comparator.comparingInt(
                        (LocalDbCategory category) ->
                                category.getCategoryCount() == null
                                        ? 0 : category.getCategoryCount()
                ).reversed();
                break;

            case FEWEST_CHANNELS:
                comparator = Comparator.comparingInt(
                        category -> category.getCategoryCount() == null
                                ? 0 : category.getCategoryCount()
                );
                break;

            case NAME_ASC:
            default:
                comparator = Comparator.comparing(
                        LocalDbCategory::getCategoryName,
                        String.CASE_INSENSITIVE_ORDER
                );
                break;
        }

        filteredList.sort(comparator);
        notifyDataSetChanged();
    }

    class CategoryHolder extends RecyclerView.ViewHolder {

        ImageView categoryIcon;
        TextView categoryLabel;
        TextView categoryTag;

        CategoryHolder(@NonNull View itemView) {
            super(itemView);

            categoryIcon = itemView.findViewById(R.id.categoryIcon);
            categoryLabel = itemView.findViewById(R.id.categoryLabel);
            categoryTag = itemView.findViewById(R.id.categoryTag);
        }

        void bind(LocalDbCategory category) {
            categoryLabel.setText(category.getCategoryName());

            int count = category.getCategoryCount() == null
                    ? 0
                    : category.getCategoryCount();

            categoryTag.setText(String.format("%d%s", count, count == 1 ? " channel" : " channels"));

            String iconName = category.getCategoryIcon();

            int iconRes = AppUtils.getCategoryIcon(iconName);

            categoryIcon.setImageResource(
                    iconRes != 0 ? iconRes : R.drawable.ic_tv
            );

            ClickUtils.applyTouchEffect(itemView, view -> {
                if (listener != null) {
                    listener.onCategoryClick(category);
                }
            });

        }


    }
}