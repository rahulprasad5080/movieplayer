package com.example.videoplayer.player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the most recently discovered audio metadata for a stream URL so the UI can
 * display stable language names even when Media3 reports repeated track labels.
 */
public class ProxyAudioMetadataStore {

    private static final int MAX_CAPACITY = 20;

    private static final ProxyAudioMetadataStore INSTANCE = new ProxyAudioMetadataStore();

    private final LinkedHashMap<String, List<TsPmtParser.AudioPid>> audioPidsByUrl;

    private ProxyAudioMetadataStore() {
        audioPidsByUrl = new LinkedHashMap<String, List<TsPmtParser.AudioPid>>(MAX_CAPACITY, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<TsPmtParser.AudioPid>> eldest) {
                return size() > MAX_CAPACITY;
            }
        };
    }

    public static ProxyAudioMetadataStore getInstance() {
        return INSTANCE;
    }

    public synchronized void save(String url, List<TsPmtParser.AudioPid> audioPids) {
        if (url == null) return;
        // deduplicate by pid
        java.util.LinkedHashSet<TsPmtParser.AudioPid> deduped = new java.util.LinkedHashSet<>();
        for (TsPmtParser.AudioPid ap : audioPids) {
            boolean alreadyHas = false;
            for (TsPmtParser.AudioPid existing : deduped) {
                if (existing.getPid() == ap.getPid()) {
                    alreadyHas = true;
                    break;
                }
            }
            if (!alreadyHas) {
                deduped.add(ap);
            }
        }
        audioPidsByUrl.put(url, new java.util.ArrayList<>(deduped));
    }

    public synchronized List<TsPmtParser.AudioPid> get(String url) {
        if (url == null || url.trim().isEmpty()) return java.util.Collections.emptyList();
        List<TsPmtParser.AudioPid> result = audioPidsByUrl.get(url);
        return result != null ? result : java.util.Collections.emptyList();
    }

    public synchronized void clear(String url) {
        if (url == null || url.trim().isEmpty()) return;
        audioPidsByUrl.remove(url);
    }

    public synchronized void clearAll() {
        audioPidsByUrl.clear();
    }
}
