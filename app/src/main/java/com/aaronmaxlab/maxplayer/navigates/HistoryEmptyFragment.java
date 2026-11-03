package com.aaronmaxlab.maxplayer.navigates;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.aaronmaxlab.maxplayer.AddPlaylistUrlActivity;
import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.FragmentHistoryEmptyBinding;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;


public class HistoryEmptyFragment extends Fragment {

    private FragmentHistoryEmptyBinding binding;


    public HistoryEmptyFragment() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHistoryEmptyBinding.inflate(inflater, container, false);


        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

    }
}