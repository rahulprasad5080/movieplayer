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
import com.example.videoplayer.model.ApiTrackConfig
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
    private var activeLanguageId: Int? = null
    private var activeTrackLabel: String? = null

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

    /** Exposed so PlayerViewModel can re-pass the track config when reloading after an API URL fetch. */
    internal var apiTrackConfigs: List<ApiTrackConfig> = emptyList()
    private var currentEmbeddedTracks: List<AudioTrack> = emptyList()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            val stateName = when(state) {
                Player.STATE_IDLE -> "STATE_IDLE"
                Player.STATE_BUFFERING -> "STATE_BUFFERING"
                Player.STATE_READY -> "STATE_READY"
                Player.STATE_ENDED -> "STATE_ENDED"
                else -> "UNKNOWN($state)"
            }
            Log.i(TAG, "[PlayerLog] onPlaybackStateChanged: $stateName (playWhenReady=${player.playWhenReady})")
            _isBuffering.value = state == Player.STATE_BUFFERING
            _isPlaying.value = player.playWhenReady && state == Player.STATE_READY
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.i(TAG, "[PlayerLog] onIsPlayingChanged: isPlaying=$isPlaying")
            _isPlaying.value = isPlaying
        }

        override fun onTracksChanged(tracks: Tracks) {
            Log.i(TAG, "[PlayerLog] onTracksChanged: groupsCount=${tracks.groups.size}")
            updateTrackList(tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "[PlayerLog] Playback error: ${error.message}", error)
            _playerError.value = error.message ?: "Playback error"
        }
    }

    init {
        player.addListener(playerListener)
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
        
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        player.setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
    }

    fun loadAndPlay(
        url: String,
        apiTracks: List<ApiTrackConfig> = apiTrackConfigs,
        selectedLanguageId: Int? = activeLanguageId,
        selectedTrackLabel: String? = activeTrackLabel,
        preservePosition: Boolean = false
    ) {
        val savedPosition = if (preservePosition && player.playbackState != Player.STATE_IDLE) {
            player.currentPosition
        } else {
            0L
        }

        Log.i(TAG, "[PlayerLog] loadAndPlay() requested:")
        Log.i(TAG, "  -> url: $url")
        Log.i(TAG, "  -> preservePosition: $preservePosition (savedPosition=${savedPosition}ms)")
        Log.i(TAG, "  -> apiTracks count: ${apiTracks.size}")
        Log.i(TAG, "  -> selectedLanguageId: $selectedLanguageId, selectedTrackLabel: $selectedTrackLabel")

        currentStreamUrl = url
        activeLanguageId = selectedLanguageId
        activeTrackLabel = selectedTrackLabel
        _playerError.value = null
        apiTrackConfigs = apiTracks
        currentEmbeddedTracks = emptyList()
        ProxyAudioMetadataStore.clear(url)

        // Clear previous track selection overrides so new stream starts clean
        trackSelector.parameters = trackSelector.parameters.buildUpon()
            .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO)
            .build()

        publishVisibleTracks()
        
        // Build the Proxy URL
        val proxyUri = Uri.Builder()
            .scheme(PlaylistProxy.SCHEME_MANIFEST)
            .authority("proxy")
            .appendQueryParameter(PlaylistProxy.PARAM_URL, url)
            .build()

        Log.i(TAG, "[PlayerLog] Created Manifest Proxy URI: $proxyUri")

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
        if (savedPosition > 0L) {
            player.seekTo(savedPosition)
            Log.i(TAG, "[PlayerLog] Preserved playback position: restored seekTo(${savedPosition}ms)")
        }
        player.prepare()
        player.playWhenReady = true
        Log.i(TAG, "[PlayerLog] MediaSource set to ExoPlayer, preparation initiated.")
    }

    /**
     * Switches to an embedded audio track using ExoPlayer's TrackSelectionOverride.
     */
    fun selectAudioTrack(audioTrack: AudioTrack) {
        Log.i(
            TAG,
            "[PlayerLog] selectAudioTrack() called:" +
                "\n  label=${audioTrack.label}" +
                "\n  languageId=${audioTrack.languageId}" +
                "\n  existIndividualVideo=${audioTrack.existIndividualVideo}" +
                "\n  groupIndex=${audioTrack.groupIndex}" +
                "\n  index=${audioTrack.index}"
        )

        activeLanguageId = audioTrack.languageId
        activeTrackLabel = audioTrack.label

        // Standard ExoPlayer selection for embedded audio renditions.
        val tracks = player.currentTracks
        val embeddedTrack = findEmbeddedTrackByLabel(audioTrack.label)
        if (embeddedTrack == null) {
            Log.w(TAG, "[PlayerLog] Could not resolve embedded track for ${audioTrack.label}; falling back to direct selector scan")
        }

        var audioGroupIdx = 0
        var overrideApplied = false
        for (group in tracks.groups) {
            if (group.type != androidx.media3.common.C.TRACK_TYPE_AUDIO) continue
            val selectedIndex = embeddedTrack?.index ?: audioTrack.index
            val matches = embeddedTrack?.groupIndex?.let { it == audioGroupIdx } ?: true
            if (matches) {
                Log.i(
                    TAG,
                    "[PlayerLog] Applying embedded audio override:" +
                        " group=$audioGroupIdx, trackIndex=$selectedIndex, label=${audioTrack.label}"
                )
                trackSelector.parameters = trackSelector.parameters.buildUpon()
                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO)
                    .addOverride(
                        androidx.media3.common.TrackSelectionOverride(
                            group.mediaTrackGroup,
                            selectedIndex
                        )
                    )
                    .build()
                overrideApplied = true
                break
            }
            audioGroupIdx++
        }

        if (!overrideApplied) {
            Log.w(TAG, "[PlayerLog] No embedded audio group matched for ${audioTrack.label}")
        }

        publishVisibleTracks()
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
        val discoveredAudioPids = ProxyAudioMetadataStore.get(currentStreamUrl)
        val embeddedResult = mutableListOf<AudioTrack>()
        val leadingEmbeddedGroupCount = calculateLeadingEmbeddedGroupCount(
            audioGroupCount = audioGroups.size,
            discoveredAudioCount = discoveredAudioPids.size
        )

        Log.i(
            TAG,
            "[PlayerLog] updateTrackList(): totalAudioGroups=${audioGroups.size}, " +
                "discoveredPidsCount=${discoveredAudioPids.size}, leadingSkipped=$leadingEmbeddedGroupCount"
        )

        audioGroups.forEachIndexed { audioGroupIdx, group ->
            if (audioGroupIdx < leadingEmbeddedGroupCount) {
                return@forEachIndexed
            }
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val visibleTrackIndex = embeddedResult.size
                val discoveredTrack = discoveredAudioPids.getOrNull(visibleTrackIndex)
                val resolvedLanguage = discoveredTrack?.language ?: format.language ?: "und"
                val resolvedLabel = discoveredTrack?.language
                    ?.let(::resolveLanguageName)
                    ?: resolveTrackLabel(
                        label = format.label,
                        language = format.language,
                        fallbackIndex = visibleTrackIndex
                    )

                embeddedResult.add(AudioTrack(
                    index = i,
                    groupIndex = audioGroupIdx,
                    language = resolvedLanguage,
                    label = resolvedLabel,
                    isSelected = group.isTrackSelected(i)
                ))
            }
        }

        currentEmbeddedTracks = embeddedResult
        autoSelectActiveEmbeddedTrack(embeddedResult)
        publishVisibleTracks(embeddedResult = embeddedResult, audioGroups = audioGroups)
    }

    private fun autoSelectActiveEmbeddedTrack(embeddedTracks: List<AudioTrack>) {
        if (embeddedTracks.isEmpty()) return
        val targetLabel = activeTrackLabel ?: return
        val matchingTrack = findEmbeddedTrackByLabel(targetLabel) ?: return

        var audioGroupIdx = 0
        for (group in player.currentTracks.groups) {
            if (group.type != androidx.media3.common.C.TRACK_TYPE_AUDIO) continue
            if (audioGroupIdx == matchingTrack.groupIndex) {
                Log.i(
                    TAG,
                    "[PlayerLog] autoSelectActiveEmbeddedTrack: Auto-applying override for targetLabel='$targetLabel' -> group=$audioGroupIdx, trackIndex=${matchingTrack.index}"
                )
                trackSelector.parameters = trackSelector.parameters.buildUpon()
                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO)
                    .addOverride(
                        androidx.media3.common.TrackSelectionOverride(
                            group.mediaTrackGroup,
                            matchingTrack.index
                        )
                    )
                    .build()
                break
            }
            audioGroupIdx++
        }
    }

    private fun publishVisibleTracks(
        embeddedResult: List<AudioTrack> = currentEmbeddedTracks,
        audioGroups: List<Tracks.Group> = emptyList()
    ) {
        if (apiTrackConfigs.isEmpty()) {
            _audioTracks.value = embeddedResult
            _selectedAudioIndex.value = resolveSelectedAudioIndex(
                visibleTracks = embeddedResult,
                leadingEmbeddedGroupCount = 0,
                audioGroups = audioGroups.ifEmpty { emptyList() }
            )
            Log.i(TAG, "[PlayerLog] Visible audio tracks updated from embedded manifest only: count=${embeddedResult.size}")
            return
        }

        val mergedTracks = apiTrackConfigs.mapIndexed { position, config ->
            val embeddedMatch = findEmbeddedTrackByConfig(config, embeddedResult)
            val playbackUrl = resolvePlaybackUrl(config)

            val isSelected = when {
                activeLanguageId != null -> config.languageId == activeLanguageId
                activeTrackLabel != null -> normalizeTrackToken(config.languageName) == normalizeTrackToken(activeTrackLabel)
                config.existIndividualVideo -> playbackUrl != null && normalizeUrl(playbackUrl) == normalizeUrl(currentStreamUrl)
                else -> embeddedMatch?.isSelected == true || config.isDefault
            }

            AudioTrack(
                index = embeddedMatch?.index ?: position,
                groupIndex = embeddedMatch?.groupIndex ?: position,
                language = config.abbreviate ?: config.languageName,
                label = config.languageName,
                isSelected = isSelected,
                existIndividualVideo = config.existIndividualVideo,
                playbackUrl = playbackUrl,
                languageId = config.languageId,
                abbreviate = config.abbreviate,
                order = config.order
            )
        }

        _audioTracks.value = mergedTracks
        _selectedAudioIndex.value = mergedTracks.indexOfFirst { it.isSelected }

        Log.i(
            TAG,
            "[PlayerLog] Published visible tracks (count=${mergedTracks.size}, selectedIndex=${_selectedAudioIndex.value}):"
        )
        mergedTracks.forEachIndexed { index, track ->
            Log.i(
                TAG,
                "  -> Track[$index]: label='${track.label}', languageId=${track.languageId}, " +
                    "individualVideo=${track.existIndividualVideo}, isSelected=${track.isSelected}, " +
                    "playbackUrl=${track.playbackUrl}"
            )
        }
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

    private fun findEmbeddedTrackByConfig(
        config: ApiTrackConfig,
        embeddedTracks: List<AudioTrack>
    ): AudioTrack? {
        if (embeddedTracks.isEmpty()) return null

        config.index?.let { apiIndex ->
            embeddedTracks.firstOrNull { it.index == apiIndex }?.let { return it }
        }

        val candidates = listOfNotNull(
            config.languageName,
            config.abbreviate,
            config.languageId?.toString()
        ).map { normalizeTrackToken(it) }

        return embeddedTracks.firstOrNull { track ->
            val trackTokens = listOfNotNull(
                track.label,
                track.language,
                resolveLanguageName(track.language),
                track.abbreviate
            ).map { normalizeTrackToken(it) }

            candidates.any { candidate -> candidate in trackTokens }
        } ?: embeddedTracks.firstOrNull()
    }

    private fun findEmbeddedTrackByLabel(label: String): AudioTrack? {
        val normalizedLabel = normalizeTrackToken(label)
        return currentEmbeddedTracks.firstOrNull { track ->
            listOfNotNull(
                track.label,
                track.language,
                resolveLanguageName(track.language),
                track.abbreviate
            ).map { normalizeTrackToken(it) }.any { it == normalizedLabel }
        }
    }

    private fun resolvePlaybackUrl(config: ApiTrackConfig): String? {
        val direct = config.playbackUrl?.trim().takeIf { !it.isNullOrBlank() }
        if (direct != null) return direct

        val fromVideo = config.videos
            .firstNotNullOfOrNull { it.playbackUrl?.trim().takeIf { url -> !url.isNullOrBlank() } }

        if (fromVideo == null && config.existIndividualVideo) {
            Log.w(TAG, "[PlayerLog] No playback URL found in config for individual video track ${config.languageName}")
        }

        return fromVideo
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

    private fun normalizeTrackToken(value: String?): String {
        return value
            ?.trim()
            ?.lowercase(Locale.ENGLISH)
            ?.replace(Regex("\\s+"), " ")
            .orEmpty()
    }

    private fun normalizeUrl(url: String?): String {
        return url
            ?.trim()
            ?.substringBefore('?')
            ?.substringBefore('#')
            .orEmpty()
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
