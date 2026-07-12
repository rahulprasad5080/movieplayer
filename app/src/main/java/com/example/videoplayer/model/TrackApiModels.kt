package com.example.videoplayer.model

import org.json.JSONArray
import org.json.JSONObject

data class ApiTrackVideo(
    val resolution: Int? = null,
    val resolutionDescription: String? = null,
    val size: Long? = null,
    val premiumProPermission: Boolean = false,
    val playbackUrl: String? = null
)

data class ApiTrackConfig(
    val languageId: Int? = null,
    val languageName: String,
    val abbreviate: String? = null,
    val isDefault: Boolean = false,
    val existIndividualVideo: Boolean = false,
    val order: Int? = null,
    val index: Int? = null,
    val videos: List<ApiTrackVideo> = emptyList(),
    val playbackUrl: String? = null
)

object TrackApiParser {
    fun parseTracksPayload(rawPayload: String): List<ApiTrackConfig> {
        val trimmed = rawPayload.trim()
        if (trimmed.isBlank()) return emptyList()

        val array = parseAsArray(trimmed) ?: return emptyList()
        val result = ArrayList<ApiTrackConfig>(array.length())

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result += parseTrack(item)
        }

        return result
    }

    private fun parseAsArray(payload: String): JSONArray? {
        return when {
            payload.startsWith("[") -> runCatching { JSONArray(payload) }.getOrNull()
            payload.startsWith("{") -> {
                val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
                when {
                    root.optJSONArray("tracks") != null -> root.optJSONArray("tracks")
                    root.optJSONArray("data") != null -> root.optJSONArray("data")
                    root.optJSONArray("results") != null -> root.optJSONArray("results")
                    else -> JSONArray().apply { put(root) }
                }
            }
            else -> null
        }
    }

    private fun parseTrack(item: JSONObject): ApiTrackConfig {
        val videos = parseVideos(item.optJSONArray("videos"))
        val directUrl = firstNonBlank(
            item.optStringOrNull("playbackUrl"),
            item.optStringOrNull("videoUrl"),
            item.optStringOrNull("streamUrl"),
            item.optStringOrNull("url")
        ) ?: videos.firstNotNullOfOrNull { it.playbackUrl }

        return ApiTrackConfig(
            languageId = item.optIntOrNull("languageId"),
            languageName = item.optString("languageName", item.optString("name", "Unknown")),
            abbreviate = item.optStringOrNull("abbreviate"),
            isDefault = item.optBoolean("isDefault", false),
            existIndividualVideo = item.optBoolean("existIndividualVideo", false),
            order = item.optIntOrNull("order"),
            index = item.optIntOrNull("index"),
            videos = videos,
            playbackUrl = directUrl
        )
    }

    private fun parseVideos(array: JSONArray?): List<ApiTrackVideo> {
        if (array == null) return emptyList()
        val result = ArrayList<ApiTrackVideo>(array.length())

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val url = firstNonBlank(
                item.optStringOrNull("playbackUrl"),
                item.optStringOrNull("videoUrl"),
                item.optStringOrNull("streamUrl"),
                item.optStringOrNull("url")
            )
            result += ApiTrackVideo(
                resolution = item.optIntOrNull("resolution"),
                resolutionDescription = item.optStringOrNull("resolutionDescription"),
                size = item.optLongOrNull("size"),
                premiumProPermission = item.optBoolean("premiumProPermission", false),
                playbackUrl = url
            )
        }

        return result
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        return optString(name, "").trim().takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optIntOrNull(name: String): Int? {
        return if (has(name) && !isNull(name)) optInt(name) else null
    }

    private fun JSONObject.optLongOrNull(name: String): Long? {
        return if (has(name) && !isNull(name)) optLong(name) else null
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }
}
