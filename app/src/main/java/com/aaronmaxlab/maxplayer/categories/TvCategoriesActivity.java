package com.aaronmaxlab.maxplayer.categories;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.channels.ChannelActivity;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.models.LocalDbCategory;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TvCategoriesActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SqlDbHelper db;
    private TvCategoriesAdapter adapter;

    private RecyclerView grid;
    private ProgressBar loading;
    private View emptyView;
    private TextView errorView;
    private TextView countView;

    private int lastSelectedPosition = 0;
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_categories);

        db = new SqlDbHelper(getApplicationContext());

        grid = findViewById(R.id.tvCategoriesGrid);
        loading = findViewById(R.id.tvCategoriesLoading);
        emptyView = findViewById(R.id.tvCategoriesEmptyView);
        errorView = findViewById(R.id.tvCategoriesErrorView);
        countView = findViewById(R.id.tvCategoriesCount);
        ImageButton backBtn = findViewById(R.id.backButton);

        if (backBtn != null) {
            ClickUtils.applyTouchEffect(backBtn, v -> finish());
        }

        int widthDp = getResources().getConfiguration().screenWidthDp;
        int columns = Math.max(3, Math.min(6, widthDp / 240));

        grid.setLayoutManager(new GridLayoutManager(this, columns));
        grid.setItemAnimator(null);
        grid.setHasFixedSize(true);
        grid.setFocusable(true);
        grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);

        adapter = new TvCategoriesAdapter((category, position) -> {
            lastSelectedPosition = position;
            int categoryId = category.getId() != null ? category.getId() : -1;
            startActivity(ChannelActivity.forCategory(this, categoryId, category.getCategoryName()));
        });
        grid.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCategories();
    }

    private void loadCategories() {
        if (loading.getVisibility() != View.VISIBLE && (adapter == null || adapter.getItemCount() == 0)) {
            loading.setVisibility(View.VISIBLE);
            grid.setVisibility(View.GONE);
            emptyView.setVisibility(View.GONE);
            errorView.setVisibility(View.GONE);
        }

        executor.execute(() -> {
            try {
                db.syncCategoryCounts();
                List<LocalDbCategory> categories = db.getCategories();

                runOnUiThread(() -> {
                    if (destroyed) return;
                    loading.setVisibility(View.GONE);
                    errorView.setVisibility(View.GONE);

                    if (categories == null || categories.isEmpty()) {
                        if (countView != null) countView.setText("0 categories");
                        grid.setVisibility(View.GONE);
                        emptyView.setVisibility(View.VISIBLE);
                    } else {
                        if (countView != null) {
                            countView.setText(categories.size() + " categories");
                        }
                        emptyView.setVisibility(View.GONE);
                        grid.setVisibility(View.VISIBLE);
                        adapter.submitList(categories);

                        restoreGridFocus();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    loading.setVisibility(View.GONE);
                    grid.setVisibility(View.GONE);
                    emptyView.setVisibility(View.GONE);
                    errorView.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void restoreGridFocus() {
        grid.post(() -> {
            if (destroyed || grid.getChildCount() == 0) return;
            final int target = Math.max(0, Math.min(lastSelectedPosition, adapter.getItemCount() - 1));

            RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(target);
            if (vh != null) {
                vh.itemView.requestFocus();
            } else {
                grid.scrollToPosition(target);
                grid.post(() -> {
                    if (destroyed) return;
                    RecyclerView.ViewHolder vh2 = grid.findViewHolderForAdapterPosition(target);
                    if (vh2 != null) {
                        vh2.itemView.requestFocus();
                    } else if (grid.getChildCount() > 0) {
                        grid.getChildAt(0).requestFocus();
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        if (db != null) db.close();
        super.onDestroy();
    }
}
