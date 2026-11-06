package com.aaronmaxlab.maxplayer.navigates;

import static com.aaronmaxlab.maxplayer.PlayAudioStreamActivity.EXTRA_STREAM_NAME;
import static com.aaronmaxlab.maxplayer.PlayAudioStreamActivity.EXTRA_STREAM_URI;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.adapter.LocalDbFavoritesAdapter;
import com.aaronmaxlab.maxplayer.database.models.LocalDbFavorites;
import com.aaronmaxlab.maxplayer.databinding.FragmentFavoritesBinding;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioPlayerActivity;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;
import com.aaronmaxlab.maxplayer.utils.StreamLauncher;
import com.aaronmaxlab.maxplayer.utils.UrlUtils;

import java.util.List;


public class FavoritesFragment extends Fragment {

    private FragmentFavoritesBinding binding;
    private SqlDbHelper sqlDbHelper;
    private LocalDbFavoritesAdapter favoritesAdapter;
    private RecyclerView recyclerView;

    public FavoritesFragment() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentFavoritesBinding.inflate(inflater, container, false);
        recyclerView = binding.favoritesRecyclerView;

        setupFavorites();

        if (binding.browsePlaylists != null) {
            com.aaronmaxlab.maxplayer.utils.ClickUtils.applyTouchEffect(binding.browsePlaylists, v -> {
                if (getActivity() != null) {
                    com.google.android.material.bottomnavigation.BottomNavigationView nav =
                            getActivity().findViewById(R.id.bottomNavigation);
                    if (nav != null) {
                        nav.setSelectedItemId(R.id.navPlaylists);
                    }
                }
            });
        }

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

    }

    @Override
    public void onDestroy() {
        binding = null;
        super.onDestroy();
    }

    private void setupFavorites() {
        sqlDbHelper = new SqlDbHelper(requireContext());


        recyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));

        favoritesAdapter = new LocalDbFavoritesAdapter(
                new LocalDbFavoritesAdapter.Listener() {

                    @Override
                    public void onItemClick(LocalDbFavorites item) {
                        String type = item.getFavType();

                        if (type == null || item.getFavUrl() == null) {
                            return;
                        }

                        if (type.contains(PlaylistType.PLAYLIST.name())) {

                            startActivity(PlayerActivity.forPlaylist(
                                    requireContext(),
                                    item.getFavUrl()
                            ));

                        } else if (type.contains(PlaylistType.AUDIO.name())) {

                            startActivity(new Intent(requireContext(), AudioPlayerActivity.class)
                                    .putExtra(EXTRA_STREAM_URI, item.getFavUrl())
                                    .putExtra(EXTRA_STREAM_NAME, item.getFavName()));

                        } else if (type.contains(PlaylistType.VOD.name())) {
                            StreamLauncher.launch(requireContext(), item.getFavUrl(), item.getFavName());
                        }
                    }

                    @Override
                    public void onRemoveClick(LocalDbFavorites item) {
                        boolean removed =
                                sqlDbHelper.removeFav(item.getFavUrl());

                        if (removed) {
                            loadFavorites();
                            AppToast.success(requireActivity(), "Removed from favorites");
                        }
                    }
                });

        recyclerView.setAdapter(favoritesAdapter);

        loadFavorites();
    }

    private void loadFavorites() {
        List<LocalDbFavorites> favoritesList = sqlDbHelper.getAllFavorites();
        recyclerView.setVisibility(favoritesList.isEmpty() ? View.GONE : View.VISIBLE);
        binding.emptyFav.setVisibility(favoritesList.isEmpty() ? View.VISIBLE : View.GONE);
        favoritesAdapter.submitList(favoritesList);
    }
}