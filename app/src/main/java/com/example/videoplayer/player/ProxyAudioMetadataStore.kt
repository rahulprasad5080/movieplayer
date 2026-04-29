package com.example.videoplayer.player

/**
 * Keeps the most recently discovered audio metadata for a stream URL so the UI can
 * display stable language names even when Media3 reports repeated track labels.
 */
object ProxyAudioMetadataStore {

    private val audioPidsByUrl = linkedMapOf<String, List<TsPmtParser.AudioPid>>()

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
}
