package com.example.videoplayer.player;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;

import com.example.videoplayer.model.AudioTrack;
import com.example.videoplayer.model.TrackApiModels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.LiveData;

/**
 * Manages ExoPlayer using the HLS Manifest Proxy strategy.
 * Java version using LiveData instead of StateFlow.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerManager {

    private static final String TAG = "PlayerManager";

    private final Context context;
    private final DefaultTrackSelector trackSelector;
    private final ExoPlayer player;

    private String currentStreamUrl;
    private Integer activeLanguageId;
    private String activeTrackLabel;

    private final MutableLiveData<Boolean> _isPlaying = new MutableLiveData<>(false);
    public final LiveData<Boolean> isPlaying = _isPlaying;

    private final MutableLiveData<Boolean> _isBuffering = new MutableLiveData<>(false);
    public final LiveData<Boolean> isBuffering = _isBuffering;

    private final MutableLiveData<String> _playerError = new MutableLiveData<>(null);
    public final LiveData<String> playerError = _playerError;

    private final MutableLiveData<List<AudioTrack>> _audioTracks = new MutableLiveData<>(Collections.emptyList());
    public final LiveData<List<AudioTrack>> audioTracks = _audioTracks;

    private final MutableLiveData<Integer> _selectedAudioIndex = new MutableLiveData<>(-1);
    public final LiveData<Integer> selectedAudioIndex = _selectedAudioIndex;

    /** Exposed so PlayerViewModel can re-pass the track config when reloading after an API URL fetch. */
    public List<TrackApiModels.ApiTrackConfig> apiTrackConfigs = Collections.emptyList();
    private List<AudioTrack> currentEmbeddedTracks = Collections.emptyList();

    private static final Map<String, String> LANGUAGE_MAP = new HashMap<>();
    static {
        LANGUAGE_MAP.put("hin", "Hindi"); LANGUAGE_MAP.put("hi", "Hindi");
        LANGUAGE_MAP.put("tam", "Tamil"); LANGUAGE_MAP.put("ta", "Tamil");
        LANGUAGE_MAP.put("tel", "Telugu"); LANGUAGE_MAP.put("te", "Telugu");
        LANGUAGE_MAP.put("eng", "English"); LANGUAGE_MAP.put("en", "English");
        LANGUAGE_MAP.put("kan", "Kannada"); LANGUAGE_MAP.put("kn", "Kannada");
        LANGUAGE_MAP.put("mal", "Malayalam"); LANGUAGE_MAP.put("ml", "Malayalam");
        LANGUAGE_MAP.put("mar", "Marathi"); LANGUAGE_MAP.put("mr", "Marathi");
        LANGUAGE_MAP.put("ben", "Bengali"); LANGUAGE_MAP.put("bn", "Bengali");
        LANGUAGE_MAP.put("jpn", "Japanese"); LANGUAGE_MAP.put("ja", "Japanese");
    }

    public PlayerManager(Context context) {
        this.context = context;

        trackSelector = new DefaultTrackSelector(context);

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context.getApplicationContext())
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true)
                .setEnableAudioTrackPlaybackParams(true);
        renderersFactory.forceEnableMediaCodecAsynchronousQueueing();

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setAllocator(new DefaultAllocator(true, 65536))
                .setBufferDurationsMs(180000, 300000, 1000, 5000)
                .setPrioritizeTimeOverSizeThresholds(false)
                .setTargetBufferBytes(-1)
                .build();

        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter.Builder(context.getApplicationContext()).build();

        player = new ExoPlayer.Builder(context.getApplicationContext(), renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setBandwidthMeter(bandwidthMeter)
                .build();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                String stateName = getStateName(state);
                Log.i(TAG, "[PlayerLog] onPlaybackStateChanged: " + stateName + " (playWhenReady=" + player.getPlayWhenReady() + ")");
                _isBuffering.setValue(state == Player.STATE_BUFFERING);
                _isPlaying.setValue(player.getPlayWhenReady() && state == Player.STATE_READY);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                Log.i(TAG, "[PlayerLog] onIsPlayingChanged: isPlaying=" + isPlaying);
                _isPlaying.setValue(isPlaying);
            }

            @Override
            public void onTracksChanged(Tracks tracks) {
                Log.i(TAG, "[PlayerLog] onTracksChanged: groupsCount=" + tracks.getGroups().size());
                updateTrackList(tracks);
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "[PlayerLog] Playback error: " + error.getMessage(), error);
                _playerError.setValue(error.getMessage() != null ? error.getMessage() : "Playback error");
            }
        });

        player.setSeekParameters(SeekParameters.EXACT);

        androidx.media3.common.AudioAttributes audioAttributes =
                new androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build();
        player.setAudioAttributes(audioAttributes, true);
    }

    public ExoPlayer getPlayer() { return player; }

    public void loadAndPlay(String url, List<TrackApiModels.ApiTrackConfig> apiTracks,
                            Integer selectedLanguageId, String selectedTrackLabel,
                            boolean preservePosition) {
        long savedPosition = (preservePosition && player.getPlaybackState() != Player.STATE_IDLE)
                ? player.getCurrentPosition() : 0L;

        Log.i(TAG, "[PlayerLog] loadAndPlay() requested:");
        Log.i(TAG, "  -> url: " + url);
        Log.i(TAG, "  -> preservePosition: " + preservePosition + " (savedPosition=" + savedPosition + "ms)");
        Log.i(TAG, "  -> apiTracks count: " + (apiTracks != null ? apiTracks.size() : 0));
        Log.i(TAG, "  -> selectedLanguageId: " + selectedLanguageId + ", selectedTrackLabel: " + selectedTrackLabel);

        currentStreamUrl = url;
        activeLanguageId = selectedLanguageId;
        activeTrackLabel = selectedTrackLabel;
        _playerError.setValue(null);
        apiTrackConfigs = apiTracks != null ? apiTracks : Collections.emptyList();
        currentEmbeddedTracks = Collections.emptyList();
        ProxyAudioMetadataStore.getInstance().clear(url);

        // Clear previous track selection overrides
        trackSelector.setParameters(
                trackSelector.getParameters().buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .build()
        );        publishVisibleTracks();
        
        // Build the Proxy URL
        Uri proxyUri = new Uri.Builder()
                .scheme(PlaylistProxy.SCHEME_MANIFEST)
                .authority("proxy")
                .appendQueryParameter(PlaylistProxy.PARAM_URL, url)
                .build();

        Log.i(TAG, "[PlayerLog] Created Manifest Proxy URI: " + proxyUri);

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true);

        // Pipeline: FilteringTsDataSource -> ProxyDataSource -> Http
        DataSource.Factory proxyDsFactory = () ->
                new ProxyDataSource(httpFactory.createDataSource(), new TsPmtParser(), new PlaylistProxy());
        DataSource.Factory dsFactory = new FilteringTsDataSource.Factory(proxyDsFactory);

        HlsMediaSource source = new HlsMediaSource.Factory(dsFactory)
                .setAllowChunklessPreparation(false)
                .createMediaSource(MediaItem.fromUri(proxyUri));

        player.setMediaSource(source);
        if (savedPosition > 0L) {
            player.seekTo(savedPosition);
            Log.i(TAG, "[PlayerLog] Preserved playback position: restored seekTo(" + savedPosition + "ms)");
        }
        player.prepare();
        player.setPlayWhenReady(true);
        Log.i(TAG, "[PlayerLog] MediaSource set to ExoPlayer, preparation initiated.");
    }

    public void loadAndPlay(String url) {
        loadAndPlay(url, Collections.emptyList(), null, null, false);
    }

    public void loadAndPlay(String url, List<TrackApiModels.ApiTrackConfig> apiTracks) {
        loadAndPlay(url, apiTracks, null, null, false);
    }

    /**
     * Switches to an embedded audio track using ExoPlayer's TrackSelectionOverride.
     */
    public void selectAudioTrack(AudioTrack audioTrack) {
        Log.i(TAG, "[PlayerLog] selectAudioTrack() called:\n" +
                "  label=" + audioTrack.getLabel() + "\n" +
                "  languageId=" + audioTrack.getLanguageId() + "\n" +
                "  existIndividualVideo=" + audioTrack.isExistIndividualVideo() + "\n" +
                "  groupIndex=" + audioTrack.getGroupIndex() + "\n" +
                "  index=" + audioTrack.getIndex());

        activeLanguageId = audioTrack.getLanguageId();
        activeTrackLabel = audioTrack.getLabel();

        // Standard ExoPlayer selection for embedded audio renditions
        Tracks tracks = player.getCurrentTracks();
        AudioTrack embeddedTrack = findEmbeddedTrackByLabel(audioTrack.getLabel());

        // If direct label matching failed, try cross-referencing via API config languageId
        if (embeddedTrack == null && audioTrack.getLanguageId() != null && !apiTrackConfigs.isEmpty()) {
            Log.w(TAG, "[PlayerLog] findEmbeddedTrackByLabel returned null; trying API config cross-reference for languageId=" + audioTrack.getLanguageId());
            for (TrackApiModels.ApiTrackConfig config : apiTrackConfigs) {
                if (audioTrack.getLanguageId().equals(config.getLanguageId())) {
                    embeddedTrack = findEmbeddedTrackByConfig(config, currentEmbeddedTracks);
                    if (embeddedTrack != null) {
                        Log.i(TAG, "[PlayerLog] Resolved embedded track via API config cross-reference: groupIndex=" +
                                embeddedTrack.getGroupIndex() + ", index=" + embeddedTrack.getIndex());
                        break;
                    }
                }
            }
        }

        int audioGroupIdx = 0;           // counts only AUDIO track groups (matches embeddedTrack.getGroupIndex())
        boolean overrideApplied = false;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) {
                // Skip non-audio groups WITHOUT incrementing audioGroupIdx
                // (original Kotlin behavior: continue skips the ++ at bottom of loop)
                continue;
            }

            int selectedIndex;
            boolean matches;

            if (embeddedTrack != null) {
                // We have a resolved embedded track — use its exact indices
                selectedIndex = embeddedTrack.getIndex();
                matches = (embeddedTrack.getGroupIndex() == audioGroupIdx);
            } else {
                // No embedded track match — scan actual ExoPlayer formats in this group
                matches = false;
                selectedIndex = 0;
                String normalizedLabel = normalizeTrackToken(audioTrack.getLabel());

                for (int i = 0; i < group.length; i++) {
                    androidx.media3.common.Format format = group.getTrackFormat(i);

                    // Check if format's language resolves to the same display name
                    if (format.language != null) {
                        String normalizedLang = normalizeTrackToken(format.language);
                        String resolvedName = resolveLanguageName(format.language);
                        String normalizedResolved = resolvedName != null
                                ? normalizeTrackToken(resolvedName) : "";

                        if (normalizedLang.equals(normalizedLabel) ||
                                normalizedResolved.equals(normalizedLabel)) {
                            matches = true;
                            selectedIndex = i;
                            break;
                        }
                    }

                    // Check format label directly
                    if (format.label != null) {
                        String normalizedFormatLabel = normalizeTrackToken(format.label);
                        if (normalizedFormatLabel.equals(normalizedLabel)) {
                            matches = true;
                            selectedIndex = i;
                            break;
                        }
                    }
                }

                // If still no match, try matching by position in merged track list
                if (!matches) {
                    List<AudioTrack> currentTracks = _audioTracks.getValue();
                    if (currentTracks != null) {
                        int positionInList = -1;
                        for (int i = 0; i < currentTracks.size(); i++) {
                            if (normalizeTrackToken(currentTracks.get(i).getLabel())
                                    .equals(normalizedLabel)) {
                                positionInList = i;
                                break;
                            }
                        }
                        // Merged tracks are in API config order, matching audio-only group order
                        if (positionInList >= 0 && positionInList == audioGroupIdx) {
                            matches = true;
                            selectedIndex = 0;
                        }
                    }
                }
            }

            if (matches) {
                Log.i(TAG, "[PlayerLog] Applying embedded audio override:" +
                        " group=" + audioGroupIdx + ", trackIndex=" + selectedIndex + ", label=" + audioTrack.getLabel());
                trackSelector.setParameters(
                        trackSelector.getParameters().buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                .addOverride(new TrackSelectionOverride(group.getMediaTrackGroup(), selectedIndex))
                                .build()
                );
                overrideApplied = true;
                break;
            }
            audioGroupIdx++;
        }

        if (!overrideApplied) {
            Log.e(TAG, "[PlayerLog] CRITICAL: No embedded audio group matched for " + audioTrack.getLabel() +
                    ". audioGroupIdx=" + audioGroupIdx + ", languageId=" + audioTrack.getLanguageId());
        }

        publishVisibleTracks();
    }

    private void updateTrackList(Tracks tracks) {
        List<Tracks.Group> audioGroups = new ArrayList<>();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() == C.TRACK_TYPE_AUDIO) {
                audioGroups.add(group);
            }
        }

        List<TsPmtParser.AudioPid> discoveredAudioPids = ProxyAudioMetadataStore.getInstance().get(currentStreamUrl);
        List<AudioTrack> embeddedResult = new ArrayList<>();
        int leadingEmbeddedGroupCount = calculateLeadingEmbeddedGroupCount(
                audioGroups.size(), discoveredAudioPids.size()
        );

        Log.i(TAG, "[PlayerLog] updateTrackList(): totalAudioGroups=" + audioGroups.size() +
                ", discoveredPidsCount=" + discoveredAudioPids.size() +
                ", leadingSkipped=" + leadingEmbeddedGroupCount);

        for (int audioGroupIdx = 0; audioGroupIdx < audioGroups.size(); audioGroupIdx++) {
            if (audioGroupIdx < leadingEmbeddedGroupCount) continue;

            Tracks.Group group = audioGroups.get(audioGroupIdx);
            for (int i = 0; i < group.length; i++) {
                androidx.media3.common.Format format = group.getTrackFormat(i);
                int visibleTrackIndex = embeddedResult.size();
                TsPmtParser.AudioPid discoveredTrack = visibleTrackIndex < discoveredAudioPids.size()
                        ? discoveredAudioPids.get(visibleTrackIndex) : null;

                String resolvedLanguage = (discoveredTrack != null && discoveredTrack.getLanguage() != null)
                        ? discoveredTrack.getLanguage()
                        : (format.language != null ? format.language : "und");

                String resolvedLabel;
                if (discoveredTrack != null && discoveredTrack.getLanguage() != null) {
                    String name = resolveLanguageName(discoveredTrack.getLanguage());
                    resolvedLabel = name != null ? name : resolveTrackLabel(format.label, format.language, visibleTrackIndex);
                } else {
                    resolvedLabel = resolveTrackLabel(format.label, format.language, visibleTrackIndex);
                }

                embeddedResult.add(new AudioTrack(
                        i, audioGroupIdx, resolvedLanguage, resolvedLabel,
                        group.isTrackSelected(i), false, null, null, null, null
                ));
            }
        }

        currentEmbeddedTracks = embeddedResult;
        autoSelectActiveEmbeddedTrack(embeddedResult);
        publishVisibleTracks();
    }

    private void autoSelectActiveEmbeddedTrack(List<AudioTrack> embeddedTracks) {
        if (embeddedTracks.isEmpty()) return;
        if (activeTrackLabel == null) return;

        AudioTrack matchingTrack = findEmbeddedTrackByLabel(activeTrackLabel);
        if (matchingTrack == null) return;

        int audioGroupIdx = 0;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) {
                // Skip non-audio groups WITHOUT incrementing audioGroupIdx
                continue;
            }
            if (audioGroupIdx == matchingTrack.getGroupIndex()) {
                Log.i(TAG, "[PlayerLog] autoSelectActiveEmbeddedTrack: Auto-applying override for targetLabel='" +
                        activeTrackLabel + "' -> group=" + audioGroupIdx + ", trackIndex=" + matchingTrack.getIndex());
                trackSelector.setParameters(
                        trackSelector.getParameters().buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                .addOverride(new TrackSelectionOverride(group.getMediaTrackGroup(), matchingTrack.getIndex()))
                                .build()
                );
                break;
            }
            audioGroupIdx++;
        }
    }

    private void publishVisibleTracks() {
        if (apiTrackConfigs.isEmpty()) {
            _audioTracks.setValue(currentEmbeddedTracks);
            _selectedAudioIndex.setValue(resolveSelectedAudioIndex(currentEmbeddedTracks, 0, Collections.emptyList()));
            Log.i(TAG, "[PlayerLog] Visible audio tracks updated from embedded manifest only: count=" + currentEmbeddedTracks.size());
            return;
        }

        List<AudioTrack> mergedTracks = new ArrayList<>();
        for (int position = 0; position < apiTrackConfigs.size(); position++) {
            TrackApiModels.ApiTrackConfig config = apiTrackConfigs.get(position);
            AudioTrack embeddedMatch = findEmbeddedTrackByConfig(config, currentEmbeddedTracks);
            String playbackUrl = resolvePlaybackUrl(config);

            boolean isSelected;
            if (activeLanguageId != null) {
                isSelected = config.getLanguageId() != null && config.getLanguageId().equals(activeLanguageId);
            } else if (activeTrackLabel != null) {
                isSelected = normalizeTrackToken(config.getLanguageName()).equals(normalizeTrackToken(activeTrackLabel));
            } else if (config.isExistIndividualVideo()) {
                isSelected = playbackUrl != null && normalizeUrl(playbackUrl).equals(normalizeUrl(currentStreamUrl));
            } else {
                isSelected = (embeddedMatch != null && embeddedMatch.isSelected()) || config.isDefault();
            }

            mergedTracks.add(new AudioTrack(
                    embeddedMatch != null ? embeddedMatch.getIndex() : position,
                    embeddedMatch != null ? embeddedMatch.getGroupIndex() : position,
                    config.getAbbreviate() != null ? config.getAbbreviate() : config.getLanguageName(),
                    config.getLanguageName(),
                    isSelected,
                    config.isExistIndividualVideo(),
                    playbackUrl,
                    config.getLanguageId(),
                    config.getAbbreviate(),
                    config.getOrder()
            ));
        }

        _audioTracks.setValue(mergedTracks);
        int selectedIdx = -1;
        for (int i = 0; i < mergedTracks.size(); i++) {
            if (mergedTracks.get(i).isSelected()) {
                selectedIdx = i;
                break;
            }
        }
        _selectedAudioIndex.setValue(selectedIdx);

        Log.i(TAG, "[PlayerLog] Published visible tracks (count=" + mergedTracks.size() + ", selectedIndex=" + selectedIdx + "):");
        for (int i = 0; i < mergedTracks.size(); i++) {
            AudioTrack track = mergedTracks.get(i);
            Log.i(TAG, "  -> Track[" + i + "]: label='" + track.getLabel() + "', languageId=" + track.getLanguageId() +
                    ", individualVideo=" + track.isExistIndividualVideo() + ", isSelected=" + track.isSelected() +
                    ", playbackUrl=" + track.getPlaybackUrl());
        }
    }

    private int calculateLeadingEmbeddedGroupCount(int audioGroupCount, int discoveredAudioCount) {
        if (discoveredAudioCount <= 0 || audioGroupCount <= discoveredAudioCount) {
            return 0;
        }
        return audioGroupCount - discoveredAudioCount;
    }

    private int resolveSelectedAudioIndex(List<AudioTrack> visibleTracks, int leadingEmbeddedGroupCount,
                                           List<Tracks.Group> audioGroups) {
        for (int i = 0; i < visibleTracks.size(); i++) {
            if (visibleTracks.get(i).isSelected()) return i;
        }
        if (leadingEmbeddedGroupCount <= 0 || visibleTracks.isEmpty()) return -1;

        boolean hiddenSelected = false;
        for (int g = 0; g < leadingEmbeddedGroupCount && g < audioGroups.size(); g++) {
            Tracks.Group hiddenGroup = audioGroups.get(g);
            for (int t = 0; t < hiddenGroup.length; t++) {
                if (hiddenGroup.isTrackSelected(t)) {
                    hiddenSelected = true;
                    break;
                }
            }
            if (hiddenSelected) break;
        }
        return hiddenSelected ? 0 : -1;
    }

    private AudioTrack findEmbeddedTrackByConfig(TrackApiModels.ApiTrackConfig config, List<AudioTrack> embeddedTracks) {
        if (embeddedTracks.isEmpty()) return null;

        if (config.getIndex() != null) {
            for (AudioTrack t : embeddedTracks) {
                if (t.getIndex() == config.getIndex()) return t;
            }
        }

        List<String> candidates = new ArrayList<>();
        if (config.getLanguageName() != null) candidates.add(normalizeTrackToken(config.getLanguageName()));
        if (config.getAbbreviate() != null) candidates.add(normalizeTrackToken(config.getAbbreviate()));
        if (config.getLanguageId() != null) candidates.add(config.getLanguageId().toString());

        for (AudioTrack track : embeddedTracks) {
            List<String> trackTokens = new ArrayList<>();
            if (track.getLabel() != null) trackTokens.add(normalizeTrackToken(track.getLabel()));
            if (track.getLanguage() != null) trackTokens.add(normalizeTrackToken(track.getLanguage()));
            String langName = resolveLanguageName(track.getLanguage());
            if (langName != null) trackTokens.add(normalizeTrackToken(langName));
            if (track.getAbbreviate() != null) trackTokens.add(normalizeTrackToken(track.getAbbreviate()));

            for (String candidate : candidates) {
                if (trackTokens.contains(candidate)) return track;
            }
        }

        return embeddedTracks.isEmpty() ? null : embeddedTracks.get(0);
    }

    private AudioTrack findEmbeddedTrackByLabel(String label) {
        String normalizedLabel = normalizeTrackToken(label);
        for (AudioTrack track : currentEmbeddedTracks) {
            List<String> trackTokens = new ArrayList<>();
            if (track.getLabel() != null) trackTokens.add(normalizeTrackToken(track.getLabel()));
            if (track.getLanguage() != null) trackTokens.add(normalizeTrackToken(track.getLanguage()));
            String langName = resolveLanguageName(track.getLanguage());
            if (langName != null) trackTokens.add(normalizeTrackToken(langName));
            if (track.getAbbreviate() != null) trackTokens.add(normalizeTrackToken(track.getAbbreviate()));

            if (trackTokens.contains(normalizedLabel)) return track;
        }
        return null;
    }

    private String resolvePlaybackUrl(TrackApiModels.ApiTrackConfig config) {
        String direct = config.getPlaybackUrl();
        if (direct != null && !direct.trim().isEmpty()) return direct.trim();

        for (TrackApiModels.ApiTrackVideo video : config.getVideos()) {
            String url = video.getPlaybackUrl();
            if (url != null && !url.trim().isEmpty()) return url.trim();
        }

        if (config.isExistIndividualVideo()) {
            Log.w(TAG, "[PlayerLog] No playback URL found in config for individual video track " + config.getLanguageName());
        }
        return null;
    }

    private String resolveTrackLabel(String label, String language, int fallbackIndex) {
        String trimmedLabel = (label != null) ? label.trim() : "";
        String displayLanguage = resolveLanguageName(language);

        if (trimmedLabel.isEmpty()) {
            return displayLanguage != null ? displayLanguage : ("Audio " + (fallbackIndex + 1));
        }
        if (looksLikeLanguageCode(trimmedLabel) && displayLanguage != null) return displayLanguage;
        if (isGenericAudioLabel(trimmedLabel) && displayLanguage != null) return displayLanguage;
        return trimmedLabel;
    }

    private String resolveLanguageName(String language) {
        String normalized = normalizeLanguageCode(language);
        if (normalized == null) return null;

        String mapped = LANGUAGE_MAP.get(normalized);
        if (mapped != null) return mapped;

        try {
            Locale locale = Locale.forLanguageTag(normalized);
            String display = locale.getDisplayLanguage(Locale.ENGLISH);
            if (display != null && !display.isEmpty() && !display.equalsIgnoreCase(normalized)) {
                if (Character.isLowerCase(display.charAt(0))) {
                    display = Character.toUpperCase(display.charAt(0)) + display.substring(1);
                }
                return display;
            }
        } catch (Exception ignored) {}

        return null;
    }

    private String normalizeLanguageCode(String language) {
        if (language == null) return null;
        String normalized = language.trim().toLowerCase(Locale.ENGLISH);
        int dashIdx = normalized.indexOf('-');
        if (dashIdx >= 0) normalized = normalized.substring(0, dashIdx);
        int underscoreIdx = normalized.indexOf('_');
        if (underscoreIdx >= 0) normalized = normalized.substring(0, underscoreIdx);
        if (normalized.isEmpty() || "und".equals(normalized)) return null;
        return normalized;
    }

    private boolean looksLikeLanguageCode(String value) {
        return value.matches("^[a-zA-Z]{2,3}$");
    }

    private boolean isGenericAudioLabel(String value) {
        String normalized = value.trim().toLowerCase(Locale.ENGLISH);
        return normalized.matches("^audio( track)?\\s*\\d*$") ||
                normalized.matches("^track\\s*\\d*$") ||
                normalized.matches("^language\\s*\\d*$") ||
                normalized.matches("^\\d+$");
    }

    private String normalizeTrackToken(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ENGLISH).replaceAll("\\s+", " ");
    }

    private String normalizeUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        int qIdx = trimmed.indexOf('?');
        if (qIdx >= 0) trimmed = trimmed.substring(0, qIdx);
        int hashIdx = trimmed.indexOf('#');
        if (hashIdx >= 0) trimmed = trimmed.substring(0, hashIdx);
        return trimmed;
    }

    private String getStateName(int state) {
        switch (state) {
            case Player.STATE_IDLE: return "STATE_IDLE";
            case Player.STATE_BUFFERING: return "STATE_BUFFERING";
            case Player.STATE_READY: return "STATE_READY";
            case Player.STATE_ENDED: return "STATE_ENDED";
            default: return "UNKNOWN(" + state + ")";
        }
    }

    public void pause() { player.pause(); }
    public void play() { player.play(); }
    public void togglePlayPause() {
        if (player.isPlaying()) player.pause(); else player.play();
    }
    public void release() { player.release(); }
}
