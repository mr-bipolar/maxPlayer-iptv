package com.aaronmaxlab.maxplayer.categories;

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
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import java.util.ArrayList;
import java.util.List;

public class TvCategoriesAdapter extends RecyclerView.Adapter<TvCategoriesAdapter.Holder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(LocalDbCategory category, int position);
    }

    private final OnCategoryClickListener listener;
    private final List<LocalDbCategory> items = new ArrayList<>();

    public TvCategoriesAdapter(OnCategoryClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<LocalDbCategory> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        LocalDbCategory category = items.get(position);
        return category.getId() != null ? category.getId() : position;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tv_category_card, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LocalDbCategory category = items.get(position);

        String name = category.getCategoryName();
        holder.title.setText(name != null ? UIUtils.toCapitalize(name) : "");

        int count = category.getCategoryCount() != null ? category.getCategoryCount() : 0;
        holder.subtitle.setText(holder.itemView.getResources().getQuantityString(
                R.plurals.playlist_channel_count, count, count));

        int iconRes = AppUtils.getCategoryIcon(category.getCategoryIcon());
        holder.icon.setImageResource(iconRes != 0 ? iconRes : R.drawable.ic_tv);

        ClickUtils.applyTouchEffect(holder.itemView, v -> {
            if (listener != null) {
                int adapterPos = holder.getBindingAdapterPosition();
                listener.onCategoryClick(category, adapterPos != RecyclerView.NO_POSITION ? adapterPos : position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView subtitle;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.categoryIcon);
            title = itemView.findViewById(R.id.categoryTitle);
            subtitle = itemView.findViewById(R.id.categorySubtitle);
        }
    }
}
