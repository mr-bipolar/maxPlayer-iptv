package com.aaronmaxlab.maxplayer.navigates;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.aaronmaxlab.maxplayer.AddPlaylistActivity;
import com.aaronmaxlab.maxplayer.AddPlaylistHelperActivity;
import com.aaronmaxlab.maxplayer.PlayAudioStreamActivity;
import com.aaronmaxlab.maxplayer.PlayVideoUrlActivity;
import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.FragmentHomeBinding;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioMiniPlayer;
import com.aaronmaxlab.maxplayer.player.mpv.MpvPlayerActivity;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;


public class HomeFragment extends Fragment {

   private FragmentHomeBinding binding;
    private AudioMiniPlayer audioMiniPlayer;

    public HomeFragment() {
        // Required empty public constructor
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ClickUtils.applyTouchEffect(binding.addPlaylistBtn, v -> {
          startActivity(new Intent(getContext(), AddPlaylistActivity.class));
         //   startActivity(new Intent(getContext(), MpvPlayerActivity.class));
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

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentHomeBinding.inflate(inflater, container, false);
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
}