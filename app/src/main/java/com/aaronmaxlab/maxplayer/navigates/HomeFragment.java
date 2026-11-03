package com.aaronmaxlab.maxplayer.navigates;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.widget.PopupMenu;

import com.aaronmaxlab.maxplayer.AddPlaylistActivity;
import com.aaronmaxlab.maxplayer.AddPlaylistHelperActivity;
import com.aaronmaxlab.maxplayer.PlayAudioStreamActivity;
import com.aaronmaxlab.maxplayer.PlayVideoUrlActivity;
import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.adapter.LocalDbPlaylistAdapter;
import com.aaronmaxlab.maxplayer.database.models.LocalDbPlaylist;
import com.aaronmaxlab.maxplayer.databinding.FragmentHomeBinding;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioMiniPlayer;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.AppUtils;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.GridSpacingItemDecoration;

import java.util.List;


public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private AudioMiniPlayer audioMiniPlayer;
    private RecyclerView recyclerView;
    private LocalDbPlaylistAdapter adapter;
    private  View emptyView;
    private SqlDbHelper sqlDbHelper;

    public HomeFragment() {
        // Required empty public constructor
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        emptyView = view.findViewById(R.id.layoutHomeEmpty);

        ClickUtils.applyTouchEffect(binding.addPlaylistBtn, v -> {
          startActivity(new Intent(getContext(), AddPlaylistActivity.class));

        });

        ClickUtils.applyTouchEffect(binding.layoutHomeEmpty.btnAddPlaylist, v -> {
            startActivity(new Intent(getContext(), AddPlaylistActivity.class));
        });

        ClickUtils.applyTouchEffect(binding.layoutHomeEmpty.btnHowToAdd, v -> {
            startActivity(new Intent(getContext(), AddPlaylistHelperActivity.class));
        });

        ClickUtils.applyTouchEffect(binding.layoutHomeEmpty.btnPlayVideoUrl, v ->{
            startActivity(new Intent(getContext(), PlayVideoUrlActivity.class));
        });

        ClickUtils.applyTouchEffect(binding.layoutHomeEmpty.btnPlayAudioStream, v -> {
            startActivity(new Intent(getContext(), PlayAudioStreamActivity.class));
        });

        View overlay = view.findViewById(R.id.audioPlayerOverlay);

        if (overlay != null) {
            audioMiniPlayer = new AudioMiniPlayer(requireActivity(), overlay);
            audioMiniPlayer.attach();
        }

         sqlDbHelper = new SqlDbHelper(requireContext());

        try {
            List<LocalDbPlaylist> playlists = sqlDbHelper.getAllPlaylists();

            if (!playlists.isEmpty()) {

            hideEmptyHome();

            adapter = new LocalDbPlaylistAdapter(
                    requireContext(),
                    playlist -> {
                        // Open the selected playlist.
                        startActivity(PlayerActivity.forPlaylist(requireContext(), playlist.getPlaylistUrl()));
                        sqlDbHelper.lastOpenUpdate(playlist.getId());
                        updateList(sqlDbHelper);

                    },
                    (anchor, playlist) -> {

                        Context themedContext = new ContextThemeWrapper(
                                anchor.getContext(),
                                R.style.PlayerPopupMenuTheme
                        );

                        PopupMenu popupMenu = new PopupMenu(themedContext, anchor);

                        popupMenu.getMenu().add(0, 1, 0, "Edit");
                        popupMenu.getMenu().add(0, 2, 1, "Delete");

                        popupMenu.setOnMenuItemClickListener(item -> {

                            switch (item.getItemId()) {
                                case 1:
                                    showEditPlaylistDialog(playlist, (selectedPlaylist, newName, newUrl) -> {
                                        LocalDbPlaylist updatePlaylist = new LocalDbPlaylist(selectedPlaylist.getId(), newName, newUrl, selectedPlaylist.getPlaylistCount(), null);
                                        final boolean result = sqlDbHelper.updatePlaylist(updatePlaylist);
                                        if (result) {
                                            updateList(sqlDbHelper);
                                            AppToast.success(getActivity(), "Successfully Updated...");
                                        } else {
                                            AppToast.error(getActivity(), "Update Failed...");
                                        }
                                    });
                                    return true;

                                case 2:
                                    AppUtils.showDeleteConfirmation(getContext(), playlist.getPlaylistName(), () -> {
                                        boolean result = sqlDbHelper.deletePlaylist(playlist.getId());

                                        if (result) {
                                            adapter.submitList(sqlDbHelper.getAllPlaylists());
                                            updateList(sqlDbHelper);
                                            AppToast.success(getActivity(), "Playlist deleted.");
                                        } else {
                                            AppToast.error(getActivity(), "Delete failed.");
                                        }
                                    });
                                    return true;

                                default:
                                    return false;
                            }
                        });

                        popupMenu.show();
                    }
            );


            int spacing = (int) (12 * getResources()
                    .getDisplayMetrics().density);

            recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));

            recyclerView.addItemDecoration(
                    new GridSpacingItemDecoration(spacing)
            );

            recyclerView.setAdapter(adapter);
            adapter.submitList(playlists);

        }else {
               showEmptyHome();
            }

        } catch (Exception e) {
            AppToast.error(getActivity(), "Failed to load playlists.");
            android.util.Log.e("PlaylistView", "Failed to load playlists", e);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        recyclerView = binding.playlistRecyclerView;
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        if (audioMiniPlayer != null) {
            audioMiniPlayer.release();
            audioMiniPlayer = null;
        }

        binding = null;
        super.onDestroyView();

    }

    @Override
    public void onResume() {
        super.onResume();
        updateList(sqlDbHelper);
    }


    private void updateList(SqlDbHelper sqlDbHelper){
        try (sqlDbHelper) {
            List<LocalDbPlaylist> updateList = sqlDbHelper.getAllPlaylists();
            adapter.submitList(updateList);

            if(updateList.isEmpty()){
                showEmptyHome();
            }else {
                hideEmptyHome();
            }

        } catch (Exception e) {
            android.util.Log.e("PlaylistView", "Failed to update playlists", e);
        }finally {
            sqlDbHelper.close();
        }
    }

    private void showEmptyHome(){
        emptyView.setVisibility(View.VISIBLE);
    }

    private void hideEmptyHome(){
        emptyView.setVisibility(View.GONE);
    }


    private void showEditPlaylistDialog(
            LocalDbPlaylist playlist,
            OnPlaylistUpdateListener listener
    ) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_playlist, null);

        EditText nameInput = view.findViewById(R.id.playlistNameInput);
        EditText urlInput = view.findViewById(R.id.playlistUrlInput);
        Button updateButton = view.findViewById(R.id.updateButton);
        Button cancelButton = view.findViewById(R.id.cancelButton);

        nameInput.setText(playlist.getPlaylistName());
        urlInput.setText(playlist.getPlaylistUrl());

        AlertDialog dialog = new AlertDialog.Builder(
                requireContext(),
                R.style.PlayerDialogTheme
        )
                .setView(view)
                .create();

        updateButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String url = urlInput.getText().toString().trim();

            if (name.isEmpty()) {
                nameInput.setError("Enter playlist name");
                nameInput.requestFocus();
                return;
            }

            if (url.isEmpty()) {
                urlInput.setError("Enter playlist URL");
                urlInput.requestFocus();
                return;
            }

            listener.onUpdate(playlist, name, url);
            dialog.dismiss();
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    public interface OnPlaylistUpdateListener {
        void onUpdate(
                LocalDbPlaylist playlist,
                String newName,
                String newUrl
        );
    }

}