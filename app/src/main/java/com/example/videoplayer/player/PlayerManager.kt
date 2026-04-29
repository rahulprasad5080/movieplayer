package com.example.videoplayer.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.example.videoplayer.model.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Manages ExoPlayer using the HLS Manifest Proxy strategy.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerManager(private val context: Context) {

    companion object {
        private const val TAG = "PlayerManager"
    }

    private val trackSelector = DefaultTrackSelector(context)
    
    private val renderersFactory = DefaultRenderersFactory(context.applicationContext)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        .setEnableDecoderFallback(true)
        .setEnableAudioTrackPlaybackParams(true)
        .also {
            it.forceEnableMediaCodecAsynchronousQueueing()
        }

    private val loadControl = DefaultLoadControl.Builder()
        .setAllocator(DefaultAllocator(true, 65536))
        .setBufferDurationsMs(
            180000, // minBufferMs
            300000, // maxBufferMs
            1000,   // bufferForPlaybackMs
            5000    // bufferForPlaybackAfterRebufferMs
        )
        .setPrioritizeTimeOverSizeThresholds(false)
        .setTargetBufferBytes(-1)
        .build()

    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context.applicationContext).build()

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext, renderersFactory)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .setBandwidthMeter(bandwidthMeter)
        .build()
        
    private var currentStreamUrl: String? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrack>> = _audioTracks.asStateFlow()

    private val _selectedAudioIndex = MutableStateFlow(-1)
    val selectedAudioIndex: StateFlow<Int> = _selectedAudioIndex.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            _isBuffering.value = state == Player.STATE_BUFFERING
            _isPlaying.value = player.playWhenReady && state == Player.STATE_READY
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateTrackList(tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Playback error", error)
            _playerError.value = error.message ?: "Playback error"
        }
    }

    init {
        player.addListener(playerListener)
    }

    fun loadAndPlay(url: String) {
        currentStreamUrl = url
        _playerError.value = null
        _audioTracks.value = emptyList()
        ProxyAudioMetadataStore.clear(url)
        
        // Build the Proxy URL
        val proxyUri = Uri.Builder()
            .scheme(PlaylistProxy.SCHEME_MANIFEST)
            .authority("proxy")
            .appendQueryParameter(PlaylistProxy.PARAM_URL, url)
            .build()

        val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        
        // Pipeline: FilteringTsDataSource -> ProxyDataSource -> Http
        val proxyDsFactory = androidx.media3.datasource.DataSource.Factory {
            ProxyDataSource(httpFactory.createDataSource(), TsPmtParser(), PlaylistProxy())
        }
        val dsFactory = FilteringTsDataSource.Factory(proxyDsFactory)

        val source = HlsMediaSource.Factory(dsFactory)
            .setAllowChunklessPreparation(false)
            .createMediaSource(MediaItem.fromUri(proxyUri))

        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }

    fun selectAudioTrack(audioTrack: AudioTrack) {
        // Standard ExoPlayer selection (now works because Proxy added tracks to manifest)
        val tracks = player.currentTracks
        var audioGroupIdx = 0
        for (group in tracks.groups) {
            if (group.type != androidx.media3.common.C.TRACK_TYPE_AUDIO) continue
            if (audioGroupIdx == audioTrack.groupIndex) {
                trackSelector.parameters = trackSelector.parameters.buildUpon()
                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO)
                    .addOverride(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, audioTrack.index))
                    .build()
                return
            }
            audioGroupIdx++
        }
    }

    private val languageMap = mapOf(
        "hin" to "Hindi", "hi" to "Hindi",
        "tam" to "Tamil", "ta" to "Tamil",
        "tel" to "Telugu", "te" to "Telugu",
        "eng" to "English", "en" to "English",
        "kan" to "Kannada", "kn" to "Kannada",
        "mal" to "Malayalam", "ml" to "Malayalam",
        "mar" to "Marathi", "mr" to "Marathi",
        "ben" to "Bengali", "bn" to "Bengali",
        "jpn" to "Japanese", "ja" to "Japanese"
    )

    private fun updateTrackList(tracks: Tracks) {
        val audioGroups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
        val result = mutableListOf<AudioTrack>()
        val discoveredAudioPids = ProxyAudioMetadataStore.get(currentStreamUrl)
        val leadingEmbeddedGroupCount = calculateLeadingEmbeddedGroupCount(
            audioGroupCount = audioGroups.size,
            discoveredAudioCount = discoveredAudioPids.size
        )

        audioGroups.forEachIndexed { audioGroupIdx, group ->
            if (audioGroupIdx < leadingEmbeddedGroupCount) {
                return@forEachIndexed
            }
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val visibleTrackIndex = result.size
                val discoveredTrack = discoveredAudioPids.getOrNull(visibleTrackIndex)
                val resolvedLanguage = discoveredTrack?.language ?: format.language ?: "und"
                val resolvedLabel = discoveredTrack?.language
                    ?.let(::resolveLanguageName)
                    ?: resolveTrackLabel(
                        label = format.label,
                        language = format.language,
                        fallbackIndex = visibleTrackIndex
                    )

                result.add(AudioTrack(
                    index = i,
                    groupIndex = audioGroupIdx,
                    language = resolvedLanguage,
                    label = resolvedLabel,
                    isSelected = group.isTrackSelected(i)
                ))
            }
        }

        _audioTracks.value = result
        _selectedAudioIndex.value = resolveSelectedAudioIndex(result, leadingEmbeddedGroupCount, audioGroups)
    }

    private fun calculateLeadingEmbeddedGroupCount(
        audioGroupCount: Int,
        discoveredAudioCount: Int
    ): Int {
        if (discoveredAudioCount <= 0 || audioGroupCount <= discoveredAudioCount) {
            return 0
        }

        return audioGroupCount - discoveredAudioCount
    }

    private fun resolveSelectedAudioIndex(
        visibleTracks: List<AudioTrack>,
        leadingEmbeddedGroupCount: Int,
        audioGroups: List<Tracks.Group>
    ): Int {
        val selectedVisibleIndex = visibleTracks.indexOfFirst { it.isSelected }
        if (selectedVisibleIndex != -1) {
            return selectedVisibleIndex
        }

        if (leadingEmbeddedGroupCount <= 0 || visibleTracks.isEmpty()) {
            return -1
        }

        val hiddenSelected = audioGroups
            .take(leadingEmbeddedGroupCount)
            .any { hiddenGroup ->
                (0 until hiddenGroup.length).any(hiddenGroup::isTrackSelected)
            }

        return if (hiddenSelected) 0 else -1
    }

    private fun resolveTrackLabel(label: String?, language: String?, fallbackIndex: Int): String {
        val trimmedLabel = label?.trim().orEmpty()
        val displayLanguage = resolveLanguageName(language)

        return when {
            trimmedLabel.isBlank() -> displayLanguage ?: "Audio ${fallbackIndex + 1}"
            looksLikeLanguageCode(trimmedLabel) && displayLanguage != null -> displayLanguage
            isGenericAudioLabel(trimmedLabel) && displayLanguage != null -> displayLanguage
            else -> trimmedLabel
        }
    }

    private fun resolveLanguageName(language: String?): String? {
        val normalized = normalizeLanguageCode(language) ?: return null

        return languageMap[normalized]
            ?: Locale.forLanguageTag(normalized)
                .getDisplayLanguage(Locale.ENGLISH)
                .takeIf { it.isNotBlank() && !it.equals(normalized, ignoreCase = true) }
                ?.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.ENGLISH) else ch.toString()
                }
    }

    private fun normalizeLanguageCode(language: String?): String? {
        val normalized = language
            ?.trim()
            ?.lowercase(Locale.ENGLISH)
            ?.substringBefore('-')
            ?.substringBefore('_')
            ?.takeIf { it.isNotBlank() && it != "und" }

        return normalized
    }

    private fun looksLikeLanguageCode(value: String): Boolean {
        return value.matches(Regex("^[a-zA-Z]{2,3}$"))
    }

    private fun isGenericAudioLabel(value: String): Boolean {
        val normalized = value.trim().lowercase(Locale.ENGLISH)
        return normalized.matches(Regex("^audio( track)?\\s*\\d*$")) ||
            normalized.matches(Regex("^track\\s*\\d*$")) ||
            normalized.matches(Regex("^language\\s*\\d*$")) ||
            normalized.matches(Regex("^\\d+$"))
    }

    fun pause() {
        player.pause()
    }

    fun play() {
        player.play()
    }

    fun release() {
        player.release()
    }
    
    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }
}
