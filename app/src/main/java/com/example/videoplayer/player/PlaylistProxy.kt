package com.example.videoplayer.player

import android.net.Uri
import android.util.Log

/**
 * Utility to "virtualize" an HLS manifest. 
 * Converts a simple Media Playlist into a Master Playlist with multiple audio tracks.
 */
class PlaylistProxy {

    companion object {
        const val TAG = "PlaylistProxy"
        const val SCHEME_MANIFEST = "proxym"
        const val SCHEME_AUDIO = "proxya"
        const val PARAM_URL = "url"
        const val PARAM_PID = "pid"
        const val PARAM_TARGET_PID = "targetPid"
        const val PARAM_BASE_PID = "basePid"
        const val PARAM_AUDIO_PIDS = "audioPids"
    }

    /**
     * Build a virtual Master Playlist that exposes discovered PIDs as renditions.
     */
    fun createMasterPlaylist(originalUrl: String, pids: List<TsPmtParser.AudioPid>): String {
        val audioPids = pids.distinctBy { it.pid }
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        sb.append("#EXT-X-VERSION:4\n\n")

        val langMap = mapOf(
            "hin" to "Hindi", "tam" to "Tamil", "tel" to "Telugu", "eng" to "English",
            "kan" to "Kannada", "mal" to "Malayalam", "mar" to "Marathi", "ben" to "Bengali",
            "jpn" to "Japanese", "ja" to "Japanese",
            "en" to "English", "hi" to "Hindi"
        )

        // 1. Add Audio Media tags for each discovered PID
        audioPids.forEachIndexed { i, ap ->
            val isDefault = if (i == 0) "YES" else "NO"
            val fullName = langMap[ap.language.lowercase()] ?: "Audio ${i + 1}"
            val proxyUrl = Uri.Builder()
                .scheme(SCHEME_AUDIO)
                .authority("proxy")
                .appendQueryParameter(PARAM_URL, originalUrl)
                .appendQueryParameter(PARAM_PID, ap.pid.toString())
                .build().toString()
            
            sb.append("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"$fullName\",")
            sb.append("LANGUAGE=\"${ap.language}\",DEFAULT=$isDefault,AUTOSELECT=YES,URI=\"$proxyUrl\"\n")
        }

        // 2. Add the Video stream referencing the audio group
        sb.append("\n#EXT-X-STREAM-INF:BANDWIDTH=2000000,AUDIO=\"audio\"\n")
        sb.append(originalUrl).append("\n")

        return sb.toString()
    }

    /**
     * "Virtualizes" a Media Playlist by appending the target PID to every segment URL.
     */
    fun virtualizeMediaPlaylist(
        originalPlaylist: String,
        originalUrl: String,
        targetPid: String,
        basePid: Int,
        audioPids: List<Int>
    ): String {
        val lines = originalPlaylist.lines()
        val sb = StringBuilder()
        val audioPidList = audioPids.joinToString(",")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#")) {
                sb.append(trimmed).append("\n")
            } else {
                val fullUrl = resolveSegmentUrl(trimmed, originalUrl)
                val rewrittenSegmentUri = Uri.parse(fullUrl)
                    .buildUpon()
                    .appendQueryParameter(PARAM_TARGET_PID, targetPid)
                    .appendQueryParameter(PARAM_BASE_PID, basePid.toString())
                    .appendQueryParameter(PARAM_AUDIO_PIDS, audioPidList)
                    .build()
                sb.append(rewrittenSegmentUri).append("\n")
            }
        }
        return sb.toString()
    }

    private fun resolveSegmentUrl(segmentPath: String, originalUrl: String): String {
        if (segmentPath.startsWith("http", ignoreCase = true)) {
            return segmentPath
        }

        val query = originalUrl.substringAfter('?', "")
        val baseUrl = originalUrl.substringBefore('?').substringBeforeLast('/')
        val resolvedUrl = "$baseUrl/$segmentPath"

        return if (query.isNotEmpty() && !resolvedUrl.contains('?')) {
            "$resolvedUrl?$query"
        } else {
            resolvedUrl
        }
    }
}
