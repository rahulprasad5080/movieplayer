package com.example.videoplayer.player

/**
 * Keeps the most recently discovered audio metadata for a stream URL so the UI can
 * display stable language names even when Media3 reports repeated track labels.
 */
object ProxyAudioMetadataStore {

    private const val MAX_CAPACITY = 20

    private val audioPidsByUrl = object : LinkedHashMap<String, List<TsPmtParser.AudioPid>>(MAX_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<TsPmtParser.AudioPid>>?): Boolean {
            return size > MAX_CAPACITY
        }
    }

    @Synchronized
    fun save(url: String, audioPids: List<TsPmtParser.AudioPid>) {
        audioPidsByUrl[url] = audioPids.distinctBy { it.pid }
    }

    @Synchronized
    fun get(url: String?): List<TsPmtParser.AudioPid> {
        if (url.isNullOrBlank()) return emptyList()
        return audioPidsByUrl[url].orEmpty()
    }

    @Synchronized
    fun clear(url: String?) {
        if (url.isNullOrBlank()) return
        audioPidsByUrl.remove(url)
    }

    @Synchronized
    fun clearAll() {
        audioPidsByUrl.clear()
    }
}
