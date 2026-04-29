package com.example.videoplayer.model

/**
 * Data class representing an audio track available in the HLS stream.
 *
 * @param index         Position of this track in the track group's format list.
 * @param groupIndex    Index of the track group (C.TRACK_TYPE_AUDIO group) in the track selection.
 * @param language      BCP-47 language code (e.g. "en", "hi", "ta").
 * @param label         Human-readable label for display (e.g. "English", "Hindi").
 * @param isSelected    Whether this track is currently active.
 */
data class AudioTrack(
    val index: Int,
    val groupIndex: Int,
    val language: String?,
    val label: String,
    val isSelected: Boolean = false
)
