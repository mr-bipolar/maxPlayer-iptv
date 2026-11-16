package com.aaronmaxlab.maxplayer.navigates;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.channels.ChannelActivity;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.adapter.LocalDbCategoryAdapter;
import com.aaronmaxlab.maxplayer.database.models.LocalDbCategory;
import com.aaronmaxlab.maxplayer.databinding.FragmentPlaylistsBinding;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioMiniPlayer;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;

import java.util.List;
import java.util.Objects;


public class PlaylistsFragment extends Fragment {

    private FragmentPlaylistsBinding binding;
    private LocalDbCategoryAdapter categoryAdapter;
    private SqlDbHelper sqlDbHelper;
    private Integer spanCount = 1;
    private RecyclerView recyclerView;
    private AudioMiniPlayer audioMiniPlayer;

    public PlaylistsFragment() {
        // Required empty public constructor
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentPlaylistsBinding.inflate(inflater, container, false);
        recyclerView = binding.categoryRecyclerView;
        sqlDbHelper = new SqlDbHelper(getContext());

        audioMiniPlayer = new AudioMiniPlayer(
                requireActivity(),
                binding.getRoot().findViewById(R.id.audioPlayerOverlay)
        );
        audioMiniPlayer.attach();

        spanChange();
        setupCategories();

       binding.categorySearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                categoryAdapter.search(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        ClickUtils.applyTouchEffect(binding.filterBtn, view -> showCategoryFilter());
        ClickUtils.applyTouchEffect(binding.sortBtn, view -> showCategorySort());
        ClickUtils.applyTouchEffect(binding.gridView, view -> spanChange());
        ClickUtils.applyTouchEffect(binding.listView, view -> spanChange());
        ClickUtils.applyTouchEffect(binding.btnAddPlaylist, view ->
                startActivity(new Intent(getContext(), com.aaronmaxlab.maxplayer.AddPlaylistActivity.class)));

        return binding.getRoot();
    }


    private void spanChange() {
        spanCount = (spanCount == 2) ? 1 : 2;

        boolean isGrid = spanCount == 2;

        binding.gridView.setBackgroundResource(
                isGrid ? R.drawable.bg_chip_active : R.drawable.bg_chip
        );

        binding.listView.setBackgroundResource(
                isGrid ? R.drawable.bg_chip : R.drawable.bg_chip_active
        );

        int activeColor = ContextCompat.getColor(requireContext(), R.color.primary_highlight);
        int inactiveColor = ContextCompat.getColor(requireContext(), R.color.ink_dim);

        binding.gridText.setTextColor(isGrid ? activeColor : inactiveColor);
        binding.listText.setTextColor(isGrid ? inactiveColor : activeColor);

        GridLayoutManager layoutManager =
                (GridLayoutManager) recyclerView.getLayoutManager();

        if (layoutManager != null) {
            layoutManager.setSpanCount(spanCount);
        }
    }


    private void setupCategories() {

        recyclerView = binding.categoryRecyclerView;

        GridLayoutManager layoutManager =
                new GridLayoutManager(requireContext(), spanCount);

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);

        try {
            sqlDbHelper.syncCategoryCounts();

            List<LocalDbCategory> categories = sqlDbHelper.getCategories();
            if (categories == null || categories.isEmpty()) {
                binding.emptyCategoriesView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                binding.emptyCategoriesView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }

            categoryAdapter = new LocalDbCategoryAdapter(
                    requireContext(),
                    categories,
                    category -> {

                        int categoryId = sqlDbHelper.getCategoryId(category.getCategoryName());

                        if (categoryId == -1) return;

                       if(category.getCategoryCount() > 0){
                           startActivity(ChannelActivity.forCategory(
                                   requireContext(),
                                   categoryId,
                                   category.getCategoryName()
                           ));
                       }else{
                           AppToast.info(getActivity(), "This category is empty.");
                       }
                    }
            );

            recyclerView.setAdapter(categoryAdapter);
        } catch (Exception e) {
            Log.e("CATEGORY", Objects.requireNonNull(e.getMessage()));
        }
    }

    private void showCategoryFilter() {
        String[] options = {
                "All categories",
                "With channels",
                "Empty categories"
        };

        new AlertDialog.Builder(requireContext(), R.style.PlayerDialogTheme)
                .setTitle("Filter categories")
                .setItems(options, (dialog, which) -> {
                    LocalDbCategoryAdapter.FilterType[] filters = {
                            LocalDbCategoryAdapter.FilterType.ALL,
                            LocalDbCategoryAdapter.FilterType.POPULATED,
                            LocalDbCategoryAdapter.FilterType.EMPTY
                    };

                    categoryAdapter.setFilter(filters[which]);
                })
                .show();
    }

    private void showCategorySort() {
        String[] options = {
                "Name: A–Z",
                "Name: Z–A",
                "Most channels",
                "Fewest channels"
        };

        new AlertDialog.Builder(getContext(), R.style.PlayerDialogTheme)
                .setTitle("Sort categories")
                .setItems(options, (dialog, which) -> {
                    LocalDbCategoryAdapter.SortType[] sorts = {
                            LocalDbCategoryAdapter.SortType.NAME_ASC,
                            LocalDbCategoryAdapter.SortType.NAME_DESC,
                            LocalDbCategoryAdapter.SortType.MOST_CHANNELS,
                            LocalDbCategoryAdapter.SortType.FEWEST_CHANNELS
                    };

                    categoryAdapter.setSort(sorts[which]);
                })
                .show();
    }


    @Override
    public void onResume() {
        super.onResume();
        setupCategories();
    }

    @Override
    public void onDestroy() {
        if (audioMiniPlayer != null) {
            audioMiniPlayer.release();
            audioMiniPlayer = null;
        }
        if (sqlDbHelper != null) {
            sqlDbHelper.close();
            sqlDbHelper = null;
        }
        super.onDestroy();
    }
}