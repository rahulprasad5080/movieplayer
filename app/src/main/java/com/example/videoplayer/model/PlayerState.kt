package com.example.videoplayer.model

/**
 * Sealed class representing all possible UI states for the player screen.
 */
sealed class PlayerState {
    /** Initial state before any playback request. */
    object Idle : PlayerState()

    /** Player is buffering or preparing the stream. */
    object Buffering : PlayerState()

    /** Player is actively playing or paused. */
    data class Playing(
        val isPlaying: Boolean,
        val audioTracks: List<AudioTrack> = emptyList(),
        val selectedTrackIndex: Int = -1
    ) : PlayerState()

    /** An error occurred during playback. */
    data class Error(val message: String) : PlayerState()
}
