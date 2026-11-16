package com.aaronmaxlab.maxplayer.navigates;

import static com.aaronmaxlab.maxplayer.PlayAudioStreamActivity.EXTRA_STREAM_NAME;
import static com.aaronmaxlab.maxplayer.PlayAudioStreamActivity.EXTRA_STREAM_URI;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aaronmaxlab.maxplayer.AddPlaylistUrlActivity;
import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.database.SqlDbHelper;
import com.aaronmaxlab.maxplayer.database.adapter.LocalDbHistoryAdapter;
import com.aaronmaxlab.maxplayer.database.models.LocalDbHistory;
import com.aaronmaxlab.maxplayer.databinding.FragmentHistoryBinding;
import com.aaronmaxlab.maxplayer.player.PlayerActivity;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioMiniPlayer;
import com.aaronmaxlab.maxplayer.player.media3.audio.AudioPlayerActivity;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.PlaylistType;
import com.aaronmaxlab.maxplayer.utils.UrlUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryFragment extends Fragment {

    private FragmentHistoryBinding binding;
    private SqlDbHelper dbHelper;
    private LocalDbHistoryAdapter historyAdapter;
    private AudioMiniPlayer audioMiniPlayer;


    private final ExecutorService networkExecutor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Intent> playerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> refreshHistory()
            );

    public HistoryFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout using View Binding
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        audioMiniPlayer = new AudioMiniPlayer(
                requireActivity(),
                binding.getRoot().findViewById(R.id.audioPlayerOverlay)
        );
        audioMiniPlayer.attach();

        dbHelper = new SqlDbHelper(requireContext());

        historyAdapter = new LocalDbHistoryAdapter((item, resume) -> {
            String type = item.getHistoryType();
            String url = item.getHistoryUrl();


            if (type == null || url == null || url.isEmpty()) {
                return;
            }

            if (type.contains(PlaylistType.PLAYLIST.name())) {

                startActivity(PlayerActivity.forPlaylist(
                        requireContext(),
                        url
                ));

            } else if (type.contains(PlaylistType.AUDIO.name())) {

                startActivity(new Intent(requireContext(), AudioPlayerActivity.class)
                        .putExtra(EXTRA_STREAM_URI, url)
                        .putExtra(EXTRA_STREAM_NAME, item.getHistoryName()));

            } else if (type.contains(PlaylistType.VOD.name())) {

            networkExecutor.execute(() -> {
                boolean reachable = UrlUtils.isStreamReachable(url);
                boolean isStream = reachable && UrlUtils.isMediaStreamUrl(url);

                mainHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }

                    Long savedPosition = item.getHistoryPosition() == null
                            ? null
                            : item.getHistoryPosition().longValue();

                    startActivity(PlayerActivity.forStream(
                            requireContext(),
                            url,
                            item.getHistoryName(),
                            isStream,
                            savedPosition
                    ));
                });
            });

        } else if (type.contains(PlaylistType.LIVE.name())) {

            networkExecutor.execute(() -> {
                boolean reachable = UrlUtils.isStreamReachable(url);
                boolean isStream = reachable && UrlUtils.isMediaStreamUrl(url);

                mainHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }

                    startActivity(PlayerActivity.forStream(
                            requireContext(),
                            url,
                            item.getHistoryName(),
                            isStream
                    ));
                });
            });
        }
        });

        ClickUtils.applyTouchEffect(binding.layoutHistoryEmpty.browsePlaylist , v -> {
            startActivity(new Intent(requireContext(), AddPlaylistUrlActivity.class));
        });

        binding.historyRecyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));

        binding.historyRecyclerView.setAdapter(historyAdapter);

        loadHistory();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Prevent memory leaks
    }

    @Override
    public void onDestroy() {
        if (audioMiniPlayer != null) {
            audioMiniPlayer.release();
            audioMiniPlayer = null;
        }
        if (dbHelper != null) {
            dbHelper.close();
            dbHelper = null;
        }
        networkExecutor.shutdownNow();
        super.onDestroy();
    }

    private void refreshHistory() {
        if (!isAdded()) return;

        List<LocalDbHistory> histories = dbHelper.getAllHistory();
        historyAdapter.submitList(histories);
    }


    @Override
    public void onResume() {
        super.onResume();
        if (binding != null && dbHelper != null && historyAdapter != null) {
            loadHistory();
        }
    }

    private void loadHistory() {
        if (binding == null || dbHelper == null || historyAdapter == null) {
            return;
        }

        List<LocalDbHistory> history = dbHelper.getAllHistory();
        historyAdapter.submitList(history);
        boolean isEmpty = history.isEmpty();
        binding.layoutHistoryEmpty.getRoot()
                .setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.historyRecyclerView
                .setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }



}