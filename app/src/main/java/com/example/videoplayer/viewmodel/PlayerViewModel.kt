package com.example.videoplayer.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videoplayer.model.AudioTrack
import com.example.videoplayer.model.TrackApiParser
import com.example.videoplayer.model.PlayerState
import com.example.videoplayer.model.VideoUrlRepository
import com.example.videoplayer.player.PlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel that owns the [PlayerManager] lifecycle and bridges player state
 * to the UI via [StateFlow]s.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    /** Single source of truth for all player operations. */
    val playerManager = PlayerManager(application)

    private var videoUrlRepository: VideoUrlRepository? = null

    private val _isFetchingVideoUrl = MutableStateFlow(false)
    val isFetchingVideoUrl: StateFlow<Boolean> = _isFetchingVideoUrl.asStateFlow()

    private val _videoUrlFetchError = MutableStateFlow<String?>(null)
    val videoUrlFetchError: StateFlow<String?> = _videoUrlFetchError.asStateFlow()

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

    val audioTracks: StateFlow<List<AudioTrack>> = playerManager.audioTracks

    // ── Initialization ────────────────────────────────────────────────────────

    fun initVideoUrlApi(apiEndpoint: String) {
        if (apiEndpoint.isNotBlank()) {
            videoUrlRepository = VideoUrlRepository(apiEndpoint)
            Log.i(TAG, "[PlayerLog] initVideoUrlApi() -> VideoUrlRepository initialized with endpoint: '$apiEndpoint'")
        } else {
            Log.w(TAG, "[PlayerLog] initVideoUrlApi() -> Received blank apiEndpoint")
        }
    }

    // ── Delegated Actions ─────────────────────────────────────────────────────

    fun loadAndPlay(url: String, tracksPayload: String? = null) {
        Log.i(TAG, "[PlayerLog] loadAndPlay() called with url: '$url'")
        val apiTracks = TrackApiParser.parseTracksPayload(tracksPayload.orEmpty())
        if (tracksPayload.isNullOrBlank()) {
            Log.i(TAG, "  -> No tracksPayload provided; using embedded-only manifest parsing")
        } else {
            Log.i(TAG, "  -> Parsed ${apiTracks.size} track configs from JSON payload")
            apiTracks.forEachIndexed { i, config ->
                Log.i(TAG, "     [Track $i] name='${config.languageName}', id=${config.languageId}, existIndividualVideo=${config.existIndividualVideo}, playbackUrl=${config.playbackUrl}")
            }
        }
        playerManager.loadAndPlay(url, apiTracks, preservePosition = false)
    }

    fun selectAudioTrack(track: AudioTrack) {
        Log.i(
            TAG,
            "[PlayerLog] selectAudioTrack() invoked:" +
                "\n  label='${track.label}'" +
                "\n  languageId=${track.languageId}" +
                "\n  existIndividualVideo=${track.existIndividualVideo}" +
                "\n  playbackUrl=${track.playbackUrl}" +
                "\n  groupIndex=${track.groupIndex}, index=${track.index}"
        )

        if (!track.existIndividualVideo) {
            // Embedded track — no API call needed, switch immediately
            Log.i(TAG, "[PlayerLog] Track '${track.label}' has existIndividualVideo=false. Delegating to PlayerManager for embedded track switch.")
            playerManager.selectAudioTrack(track)
            return
        }

        // Individual video track — fetch fresh URL from API or use track.playbackUrl fallback
        Log.i(TAG, "[PlayerLog] Track '${track.label}' has existIndividualVideo=true. Initiating individual video switch sequence.")

        val repository = videoUrlRepository
        val fallbackUrl = track.playbackUrl?.trim()?.takeIf { it.isNotBlank() }

        if (repository == null) {
            if (fallbackUrl != null) {
                Log.i(TAG, "[PlayerLog] No API repository configured, but fallback playbackUrl found: '$fallbackUrl'. Loading directly with preservePosition=true.")
                playerManager.loadAndPlay(
                    url = fallbackUrl,
                    apiTracks = playerManager.apiTrackConfigs,
                    selectedLanguageId = track.languageId,
                    selectedTrackLabel = track.label,
                    preservePosition = true
                )
                return
            }
            Log.e(TAG, "[PlayerLog] ERROR: VideoUrlRepository not initialized and no fallback playbackUrl found for '${track.label}'")
            _videoUrlFetchError.value = "Cannot switch to ${track.label}: API not configured"
            return
        }

        val languageId = track.languageId
        if (languageId == null && fallbackUrl == null) {
            Log.e(TAG, "[PlayerLog] ERROR: Track '${track.label}' has existIndividualVideo=true but languageId is null and no playbackUrl")
            _videoUrlFetchError.value = "Cannot switch to ${track.label}: missing language ID"
            return
        }

        viewModelScope.launch {
            _isFetchingVideoUrl.value = true
            _videoUrlFetchError.value = null

            val result = if (languageId != null) {
                Log.i(TAG, "[PlayerLog] Sending API request to fetch fresh video URL for languageId=$languageId (${track.label})...")
                repository.fetchVideoUrl(languageId)
            } else {
                Result.failure(Exception("Missing language ID"))
            }

            _isFetchingVideoUrl.value = false

            result.fold(
                onSuccess = { freshUrl ->
                    Log.i(TAG, "[PlayerLog] SUCCESS: Got fresh URL for '${track.label}': '$freshUrl'. Triggering loadAndPlay(preservePosition=true).")
                    playerManager.loadAndPlay(
                        url = freshUrl,
                        apiTracks = playerManager.apiTrackConfigs,
                        selectedLanguageId = track.languageId,
                        selectedTrackLabel = track.label,
                        preservePosition = true
                    )
                },
                onFailure = { error ->
                    if (fallbackUrl != null) {
                        Log.w(TAG, "[PlayerLog] API fetch failed (${error.message}); falling back to track.playbackUrl: '$fallbackUrl'")
                        playerManager.loadAndPlay(
                            url = fallbackUrl,
                            apiTracks = playerManager.apiTrackConfigs,
                            selectedLanguageId = track.languageId,
                            selectedTrackLabel = track.label,
                            preservePosition = true
                        )
                    } else {
                        Log.e(TAG, "[PlayerLog] ERROR: Failed to fetch video URL for '${track.label}'", error)
                        _videoUrlFetchError.value = "Failed to switch to ${track.label}: ${error.message}"
                    }
                }
            )
        }
    }

    fun pause() = playerManager.pause()

    fun play() = playerManager.play()

    fun togglePlayPause() = playerManager.togglePlayPause()

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
