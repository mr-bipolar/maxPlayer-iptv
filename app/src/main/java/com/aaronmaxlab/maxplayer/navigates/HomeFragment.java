package com.aaronmaxlab.maxplayer.navigates;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.aaronmaxlab.maxplayer.AddPlaylistActivity;
import com.aaronmaxlab.maxplayer.AddPlaylistHelperActivity;
import com.aaronmaxlab.maxplayer.PlayAudioStreamActivity;
import com.aaronmaxlab.maxplayer.PlayVideoUrlActivity;
import com.aaronmaxlab.maxplayer.databinding.FragmentHomeBinding;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;


public class HomeFragment extends Fragment {

   private FragmentHomeBinding binding;

    public HomeFragment() {
        // Required empty public constructor
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ClickUtils.applyTouchEffect(binding.addPlaylistBtn, v -> {
            Toast.makeText(requireContext(), "Image clicked!", Toast.LENGTH_SHORT).show();
        });

        ClickUtils.applyTouchEffect(binding.layoutHomeEmpty.btnAddPlaylist, v -> {
            Intent intent = new Intent(getContext(), AddPlaylistActivity.class);
            startActivity(intent);
        });

        ClickUtils.applyTouchEffect(binding.layoutHomeEmpty.btnHowToAdd, v -> {

            Intent intent = new Intent(getContext(), AddPlaylistHelperActivity.class);
            startActivity(intent);

        });

        ClickUtils.applyTouchEffect(binding.layoutHomeEmpty.btnPlayVideoUrl, v ->{
            Intent intent = new Intent(getContext(), PlayVideoUrlActivity.class);
            startActivity(intent);
        });

        ClickUtils.applyTouchEffect(binding.layoutHomeEmpty.btnPlayAudioStream, v -> {
            Intent intent = new Intent(getContext(), PlayAudioStreamActivity.class);
            startActivity(intent);
        });
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
        super.onDestroyView();
        binding = null;
    }
}