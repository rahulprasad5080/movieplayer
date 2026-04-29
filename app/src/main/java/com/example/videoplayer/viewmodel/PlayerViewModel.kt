package com.example.videoplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videoplayer.model.AudioTrack
import com.example.videoplayer.model.PlayerState
import com.example.videoplayer.player.PlayerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel that owns the [PlayerManager] lifecycle and bridges player state
 * to the UI via [StateFlow]s.
 *
 * Using [AndroidViewModel] so we can safely hold the Application context
 * needed to build ExoPlayer without leaking the Activity context.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    /** Single source of truth for all player operations. */
    val playerManager = PlayerManager(application)

    /**
     * Combined UI state derived from individual PlayerManager flows.
     * The UI observes exactly one [StateFlow] instead of three separate ones.
     */
    val playerState: StateFlow<PlayerState> = combine(
        playerManager.isBuffering,
        playerManager.isPlaying,
        playerManager.audioTracks,
        playerManager.selectedAudioIndex,
        playerManager.playerError
    ) { isBuffering, isPlaying, audioTracks, selectedIdx, error ->
        when {
            error != null   -> PlayerState.Error(error)
            isBuffering     -> PlayerState.Buffering
            else            -> PlayerState.Playing(
                isPlaying           = isPlaying,
                audioTracks         = audioTracks,
                selectedTrackIndex  = selectedIdx
            )
        }
    }.stateIn(
        scope          = viewModelScope,
        started        = SharingStarted.WhileSubscribed(5_000),
        initialValue   = PlayerState.Idle
    )

    /** Exposed for quick access to audio tracks list. */
    val audioTracks: StateFlow<List<AudioTrack>> = playerManager.audioTracks

    // ── Delegated Actions ─────────────────────────────────────────────────────

    fun loadAndPlay(url: String) = playerManager.loadAndPlay(url)

    fun pause() = playerManager.pause()

    fun play() = playerManager.play()

    fun togglePlayPause() = playerManager.togglePlayPause()

    fun selectAudioTrack(track: AudioTrack) = playerManager.selectAudioTrack(track)

    /** Called automatically by the Android framework when the ViewModel is cleared. */
    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
