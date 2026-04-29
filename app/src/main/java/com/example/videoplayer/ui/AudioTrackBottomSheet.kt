package com.example.videoplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.videoplayer.databinding.BottomSheetAudioTracksBinding
import com.example.videoplayer.model.AudioTrack
import com.example.videoplayer.ui.adapter.AudioTrackAdapter
import com.example.videoplayer.viewmodel.PlayerViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * Material BottomSheetDialogFragment that lists all available audio tracks
 * and allows the user to switch between them with a single tap.
 *
 * Communication with [PlayerActivity] happens through the shared [PlayerViewModel]
 * obtained via [activityViewModels] – no interface callbacks needed.
 */
class AudioTrackBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAudioTracksBinding? = null
    private val binding get() = _binding!!

    // Shared ViewModel – same instance as PlayerActivity's
    private val viewModel: PlayerViewModel by activityViewModels()

    private val adapter = AudioTrackAdapter { track ->
        onTrackSelected(track)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAudioTracksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up RecyclerView
        binding.rvAudioTracks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAudioTracks.adapter = adapter

        // Observe audio tracks reactively
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.audioTracks.collect { tracks ->
                    if (tracks.isEmpty()) {
                        binding.tvNoTracks.visibility = View.VISIBLE
                        binding.rvAudioTracks.visibility = View.GONE
                    } else {
                        binding.tvNoTracks.visibility = View.GONE
                        binding.rvAudioTracks.visibility = View.VISIBLE
                        adapter.submitList(tracks)
                    }
                }
            }
        }

        // Observe selected track index to keep the check mark in sync
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playerManager.selectedAudioIndex.collect { index ->
                    adapter.selectedIndex = index
                }
            }
        }
    }

    /**
     * Called when the user taps an audio track row.
     * Delegates to ViewModel which routes to PlayerManager → TrackSelectionOverride.
     */
    private fun onTrackSelected(track: AudioTrack) {
        viewModel.selectAudioTrack(track)
        // Dismiss after selection for a clean UX
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AudioTrackBottomSheet"

        fun newInstance() = AudioTrackBottomSheet()
    }
}
