package com.example.videoplayer.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.videoplayer.databinding.BottomSheetAudioTracksBinding;
import com.example.videoplayer.model.AudioTrack;
import com.example.videoplayer.ui.adapter.AudioTrackAdapter;
import com.example.videoplayer.viewmodel.PlayerViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

/**
 * Material BottomSheetDialogFragment that lists all available audio tracks
 * and allows the user to switch between them with a single tap.
 */
public class AudioTrackBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetAudioTracksBinding binding;

    // Shared ViewModel – same instance as PlayerActivity's
    private PlayerViewModel viewModel;

    private final AudioTrackAdapter adapter = new AudioTrackAdapter(track -> onTrackSelected(track));

    public static AudioTrackBottomSheet newInstance() {
        return new AudioTrackBottomSheet();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetAudioTracksBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get ViewModel from the parent Activity using ViewModelProvider (generic, no coupling)
        if (getActivity() != null) {
            viewModel = new ViewModelProvider(getActivity()).get(PlayerViewModel.class);
        }

        if (viewModel == null) {
            return;
        }

        // Set up RecyclerView
        binding.rvAudioTracks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAudioTracks.setAdapter(adapter);

        // Observe audio tracks reactively
        final Observer<List<AudioTrack>> tracksObserver = tracks -> {
            if (tracks == null || tracks.isEmpty()) {
                binding.tvNoTracks.setVisibility(View.VISIBLE);
                binding.rvAudioTracks.setVisibility(View.GONE);
            } else {
                binding.tvNoTracks.setVisibility(View.GONE);
                binding.rvAudioTracks.setVisibility(View.VISIBLE);
                adapter.submitList(tracks);
            }
        };

        viewModel.audioTracks.observe(getViewLifecycleOwner(), tracksObserver);

        // Observe selected track index to keep the check mark in sync
        final Observer<Integer> indexObserver = index -> {
            if (index != null) {
                adapter.setSelectedIndex(index);
            }
        };

        viewModel.playerManager.selectedAudioIndex.observe(getViewLifecycleOwner(), indexObserver);
    }

    /**
     * Called when the user taps an audio track row.
     * Delegates to ViewModel which routes to PlayerManager -> TrackSelectionOverride.
     */
    private void onTrackSelected(AudioTrack track) {
        if (viewModel != null) {
            viewModel.selectAudioTrack(track);
        }
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public static final String TAG = "AudioTrackBottomSheet";
}
