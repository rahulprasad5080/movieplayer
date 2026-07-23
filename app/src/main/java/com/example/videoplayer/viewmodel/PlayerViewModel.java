package com.example.videoplayer.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.videoplayer.model.AudioTrack;
import com.example.videoplayer.model.PlayerState;
import com.example.videoplayer.model.TrackApiModels;
import com.example.videoplayer.model.VideoUrlRepository;
import com.example.videoplayer.player.PlayerManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel that owns the PlayerManager lifecycle and bridges player state
 * to the UI via LiveData.
 */
public class PlayerViewModel extends AndroidViewModel {

    private static final String TAG = "PlayerViewModel";

    /** Single source of truth for all player operations. */
    public final PlayerManager playerManager;

    private VideoUrlRepository videoUrlRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Boolean> _isFetchingVideoUrl = new MutableLiveData<>(false);
    public final LiveData<Boolean> isFetchingVideoUrl = _isFetchingVideoUrl;

    private final MutableLiveData<String> _videoUrlFetchError = new MutableLiveData<>(null);
    public final LiveData<String> videoUrlFetchError = _videoUrlFetchError;

    private final MediatorLiveData<PlayerState> _playerState = new MediatorLiveData<>();
    public final LiveData<PlayerState> playerState = _playerState;

    public final LiveData<List<AudioTrack>> audioTracks;

    public PlayerViewModel(@NonNull Application application) {
        super(application);
        playerManager = new PlayerManager(application);
        audioTracks = playerManager.audioTracks;

        // Combine multiple LiveData sources into a single PlayerState LiveData
        _playerState.addSource(playerManager.isBuffering, isBuffering -> updatePlayerState());
        _playerState.addSource(playerManager.isPlaying, isPlaying -> updatePlayerState());
        _playerState.addSource(playerManager.audioTracks, tracks -> updatePlayerState());
        _playerState.addSource(playerManager.selectedAudioIndex, idx -> updatePlayerState());
        _playerState.addSource(playerManager.playerError, error -> updatePlayerState());
    }

    private void updatePlayerState() {
        String error = playerManager.playerError.getValue();
        Boolean isBuffering = playerManager.isBuffering.getValue();
        Boolean isPlaying = playerManager.isPlaying.getValue();
        List<AudioTrack> tracks = playerManager.audioTracks.getValue();
        Integer selectedIdx = playerManager.selectedAudioIndex.getValue();

        if (error != null && !error.isEmpty()) {
            _playerState.setValue(new PlayerState.Error(error));
        } else if (isBuffering != null && isBuffering) {
            _playerState.setValue(PlayerState.Buffering.INSTANCE);
        } else {
            boolean playing = isPlaying != null && isPlaying;
            List<AudioTrack> safeTracks = tracks != null ? tracks : java.util.Collections.emptyList();
            int idx = selectedIdx != null ? selectedIdx : -1;
            _playerState.setValue(new PlayerState.Playing(playing, safeTracks, idx));
        }
    }

    // ── Initialization ────────────────────────────────────────────────────────

    public void initVideoUrlApi(String apiEndpoint) {
        if (apiEndpoint != null && !apiEndpoint.trim().isEmpty()) {
            videoUrlRepository = new VideoUrlRepository(apiEndpoint);
            Log.i(TAG, "[PlayerLog] initVideoUrlApi() -> VideoUrlRepository initialized with endpoint: '" + apiEndpoint + "'");
        } else {
            Log.w(TAG, "[PlayerLog] initVideoUrlApi() -> Received blank apiEndpoint");
        }
    }

    // ── Delegated Actions ─────────────────────────────────────────────────────

    public void loadAndPlay(String url, String tracksPayload) {
        Log.i(TAG, "[PlayerLog] loadAndPlay() called with url: '" + url + "'");
        List<TrackApiModels.ApiTrackConfig> apiTracks =
                TrackApiModels.TrackApiParser.parseTracksPayload(tracksPayload != null ? tracksPayload : "");
        if (tracksPayload == null || tracksPayload.trim().isEmpty()) {
            Log.i(TAG, "  -> No tracksPayload provided; using embedded-only manifest parsing");
        } else {
            Log.i(TAG, "  -> Parsed " + apiTracks.size() + " track configs from JSON payload");
            for (int i = 0; i < apiTracks.size(); i++) {
                TrackApiModels.ApiTrackConfig config = apiTracks.get(i);
                Log.i(TAG, "     [Track " + i + "] name='" + config.getLanguageName() +
                        "', id=" + config.getLanguageId() +
                        ", existIndividualVideo=" + config.isExistIndividualVideo() +
                        ", playbackUrl=" + config.getPlaybackUrl());
            }
        }
        playerManager.loadAndPlay(url, apiTracks, null, null, false);
    }

    public void selectAudioTrack(AudioTrack track) {
        Log.i(TAG, "[PlayerLog] selectAudioTrack() invoked:\n" +
                "  label='" + track.getLabel() + "'\n" +
                "  languageId=" + track.getLanguageId() + "\n" +
                "  existIndividualVideo=" + track.isExistIndividualVideo() + "\n" +
                "  playbackUrl=" + track.getPlaybackUrl() + "\n" +
                "  groupIndex=" + track.getGroupIndex() + ", index=" + track.getIndex());

        if (!track.isExistIndividualVideo()) {
            // Embedded track — no API call needed, switch immediately
            Log.i(TAG, "[PlayerLog] Track '" + track.getLabel() + "' has existIndividualVideo=false. Delegating to PlayerManager for embedded track switch.");
            playerManager.selectAudioTrack(track);
            return;
        }

        // Individual video track — fetch fresh URL from API or use track.playbackUrl fallback
        Log.i(TAG, "[PlayerLog] Track '" + track.getLabel() + "' has existIndividualVideo=true. Initiating individual video switch sequence.");

        final String fallbackUrl = track.getPlaybackUrl() != null
                ? track.getPlaybackUrl().trim()
                : null;
        final String effectiveFallbackUrl = (fallbackUrl != null && !fallbackUrl.isEmpty()) ? fallbackUrl : null;

        if (videoUrlRepository == null) {
            if (effectiveFallbackUrl != null) {
                Log.i(TAG, "[PlayerLog] No API repository configured, but fallback playbackUrl found: '" + effectiveFallbackUrl + "'. Loading directly with preservePosition=true.");
                playerManager.loadAndPlay(
                        effectiveFallbackUrl,
                        playerManager.apiTrackConfigs,
                        track.getLanguageId(),
                        track.getLabel(),
                        true
                );
                return;
            }
            Log.e(TAG, "[PlayerLog] ERROR: VideoUrlRepository not initialized and no fallback playbackUrl found for '" + track.getLabel() + "'");
            _videoUrlFetchError.setValue("Cannot switch to " + track.getLabel() + ": API not configured");
            return;
        }

        final Integer languageId = track.getLanguageId();
        if (languageId == null && effectiveFallbackUrl == null) {
            Log.e(TAG, "[PlayerLog] ERROR: Track '" + track.getLabel() + "' has existIndividualVideo=true but languageId is null and no playbackUrl");
            _videoUrlFetchError.setValue("Cannot switch to " + track.getLabel() + ": missing language ID");
            return;
        }

        executor.execute(() -> {
            _isFetchingVideoUrl.postValue(true);
            _videoUrlFetchError.postValue(null);

            VideoUrlRepository.Result<String> result;
            if (languageId != null) {
                Log.i(TAG, "[PlayerLog] Sending API request to fetch fresh video URL for languageId=" + languageId + " (" + track.getLabel() + ")...");
                result = videoUrlRepository.fetchVideoUrl(languageId);
            } else {
                result = VideoUrlRepository.Result.failure(new Exception("Missing language ID"));
            }

            _isFetchingVideoUrl.postValue(false);

            result.fold(
                    freshUrl -> {
                        Log.i(TAG, "[PlayerLog] SUCCESS: Got fresh URL for '" + track.getLabel() + "': '" + freshUrl + "'. Triggering loadAndPlay(preservePosition=true).");
                        playerManager.loadAndPlay(
                                freshUrl,
                                playerManager.apiTrackConfigs,
                                track.getLanguageId(),
                                track.getLabel(),
                                true
                        );
                    },
                    error -> {
                        if (effectiveFallbackUrl != null) {
                            Log.w(TAG, "[PlayerLog] API fetch failed (" + error.getMessage() + "); falling back to track.playbackUrl: '" + effectiveFallbackUrl + "'");
                            playerManager.loadAndPlay(
                                    effectiveFallbackUrl,
                                    playerManager.apiTrackConfigs,
                                    track.getLanguageId(),
                                    track.getLabel(),
                                    true
                            );
                        } else {
                            Log.e(TAG, "[PlayerLog] ERROR: Failed to fetch video URL for '" + track.getLabel() + "'", error);
                            _videoUrlFetchError.postValue("Failed to switch to " + track.getLabel() + ": " + error.getMessage());
                        }
                    }
            );
        });
    }

    public void pause() { playerManager.pause(); }
    public void play() { playerManager.play(); }
    public void togglePlayPause() { playerManager.togglePlayPause(); }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
        playerManager.release();
    }
}
