package com.example.videoplayer.model

/**
 * Data class representing an audio track available in the HLS stream.
 *
 * @param index         Position of this track in the track group's format list.
 * @param groupIndex    Index of the track group (C.TRACK_TYPE_AUDIO group) in the track selection.
 * @param language      BCP-47 language code (e.g. "en", "hi", "ta").
 * @param label         Human-readable label for display (e.g. "English", "Hindi").
 * @param isSelected    Whether this track is currently active.
 * @param existIndividualVideo Whether this language should load a separate playback URL.
 * @param playbackUrl   Optional language-specific playback URL for API-driven switching.
 */
data class AudioTrack(
    val index: Int,
    val groupIndex: Int,
    val language: String?,
    val label: String,
    val isSelected: Boolean = false,
    val existIndividualVideo: Boolean = false,
    val playbackUrl: String? = null,
    val languageId: Int? = null,
    val abbreviate: String? = null,
    val order: Int? = null
)
