package com.example.videoplayer.ui

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.example.videoplayer.R
import com.example.videoplayer.databinding.ActivityPlayerBinding
import com.example.videoplayer.model.PlayerState
import com.example.videoplayer.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

/**
 * Full-screen video player activity.
 *
 * Architecture:
 *  - [PlayerViewModel] owns [PlayerManager] (ExoPlayer + TrackSelector).
 *  - This Activity attaches the player to [PlayerView] and observes [PlayerState].
 *  - Audio track switching is delegated to [AudioTrackBottomSheet].
 *
 * Lifecycle handling:
 *  - The ViewModel survives configuration changes (rotation), so we simply
 *    re-attach/detach the player from the view on resume/pause.
 *  - We do NOT call player.release() here – the ViewModel handles that in onCleared().
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STREAM_URL = "extra_stream_url"
    }

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while video is playing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureSystemUI()
        attachPlayerView()
        observePlayerState()
        setupControls()

        // Load stream only on first creation, not on config change
        if (savedInstanceState == null) {
            val url = intent.getStringExtra(EXTRA_STREAM_URL)
                ?: run {
                    Toast.makeText(this, "No stream URL provided", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
            viewModel.loadAndPlay(url)
        }
    }

    // ── Player View Attachment ────────────────────────────────────────────────

    /**
     * Attach ExoPlayer to the PlayerView.
     * We always attach in onStart and detach in onStop so the player can
     * continue running headlessly during a brief config change.
     */
    override fun onStart() {
        super.onStart()
        binding.playerView.player = viewModel.playerManager.player
    }

    override fun onStop() {
        super.onStop()
        binding.playerView.player = null
        if (!isChangingConfigurations) {
            viewModel.pause()
        }
    }

    // ── System UI ─────────────────────────────────────────────────────────────

    private fun configureSystemUI() {
        // Full-screen immersive mode
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    // ── Player View Setup ─────────────────────────────────────────────────────

    private fun attachPlayerView() {
        binding.playerView.apply {
            // Show controller automatically with 3s timeout
            controllerShowTimeoutMs = 3000
            controllerAutoShow = true

            // Use RESIZE_MODE_FIT by default so the video is always fully visible
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT

            // Keep controls visible so we can overlay the audio button
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        }
    }

    // ── State Observation ─────────────────────────────────────────────────────

    private fun observePlayerState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playerState.collect { state ->
                    when (state) {
                        is PlayerState.Idle -> showBuffering(false)
                        is PlayerState.Buffering -> showBuffering(true)
                        is PlayerState.Playing -> {
                            showBuffering(false)
                            updateAudioTrackButton(state.audioTracks.size)
                        }
                        is PlayerState.Error -> {
                            showBuffering(false)
                            showError(state.message)
                        }
                    }
                }
            }
        }
    }

    // ── Controls Setup ────────────────────────────────────────────────────────

    private fun setupControls() {
        // Audio track button → open popup dialog with radio buttons
        binding.btnAudioTrack.setOnClickListener {
            val tracks = viewModel.audioTracks.value
            if (tracks.isEmpty()) {
                Toast.makeText(this, "No audio tracks active", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val trackLabels = tracks.map { it.label }.toTypedArray()
            val selectedIndex = viewModel.playerManager.selectedAudioIndex.value

            com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
                .setTitle("Select Audio Language")
                .setSingleChoiceItems(trackLabels, selectedIndex) { dialog, which ->
                    viewModel.selectAudioTrack(tracks[which])
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        // Back button
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private fun showBuffering(show: Boolean) {
        binding.progressBuffering.visibility = if (show) View.VISIBLE else View.GONE
    }

    /**
     * Show/hide the audio track button based on whether multiple tracks exist.
     * The button is always shown so users know it's there, but dimmed if only one track.
     */
    private fun updateAudioTrackButton(trackCount: Int) {
        binding.btnAudioTrack.isEnabled = trackCount > 0
        binding.btnAudioTrack.alpha = if (trackCount > 0) 1f else 0.4f
        binding.tvTrackCount.text = if (trackCount > 0) "$trackCount" else ""
    }

    private fun showError(message: String) {
        Toast.makeText(this, "Playback error: $message", Toast.LENGTH_LONG).show()
    }
}
