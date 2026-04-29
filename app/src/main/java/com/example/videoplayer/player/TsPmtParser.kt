package com.example.videoplayer.player

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Parses the PAT/PMT of the first MPEG-TS segment in an HLS media playlist
 * to discover ALL embedded audio PIDs and their ISO 639-2 language codes.
 *
 * ExoPlayer's HLS source ignores multi-audio PIDs when there are no
 * #EXT-X-MEDIA declarations. This parser bypasses that limitation.
 */
class TsPmtParser {

    companion object {
        private const val TAG = "TsPmtParser"
        private const val PKT = 188
        private val AUDIO_TYPES = setOf(0x0F, 0x11, 0x03, 0x04, 0x81, 0x82)
    }

    data class AudioPid(val pid: Int, val language: String)

    suspend fun extractAudioPids(playlistUrl: String): List<AudioPid> =
        withContext(Dispatchers.IO) {
            try {
                val segUrl = firstSegmentUrl(playlistUrl) ?: return@withContext emptyList()
                Log.d(TAG, "Parsing segment: $segUrl")
                // Scan 512KB of the first segment
                val data = download(segUrl, 512 * 1024) 
                parse(data)
            } catch (e: Exception) {
                Log.e(TAG, "TS parse error", e)
                emptyList()
            }
        }

    private fun firstSegmentUrl(playlistUrl: String): String? {
        val conn = URL(playlistUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 10_000
        val lines = try {
            conn.inputStream.bufferedReader().readLines()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read playlist lines from $playlistUrl", e)
            return null
        } finally {
            conn.disconnect()
        }
        
        val segmentLine = lines.firstOrNull { it.isNotBlank() && !it.startsWith('#') } ?: return null
        if (segmentLine.startsWith("http")) return segmentLine
        
        // Preserve query parameters for the segment request
        val query = playlistUrl.substringAfter('?', "")
        val baseUrl = playlistUrl.substringBefore('?').substringBeforeLast('/')
        val finalUrl = "$baseUrl/$segmentLine"
        return if (query.isNotEmpty() && !finalUrl.contains('?')) "$finalUrl?$query" else finalUrl
    }

    private fun download(url: String, maxBytes: Int): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 15_000
        conn.setRequestProperty("Range", "bytes=0-${maxBytes - 1}")
        val bytes = conn.inputStream.readBytes().take(maxBytes).toByteArray()
        conn.disconnect()
        return bytes
    }

    private fun parse(data: ByteArray): List<AudioPid> {
        val n = data.size / PKT
        val pmtPids = mutableSetOf<Int>()
        val result = mutableListOf<AudioPid>()

        // Pass 1: PAT → find ALL PMT PIDs
        for (i in 0 until n) {
            val o = i * PKT
            if (b(data, o) != 0x47) continue
            val pid = pid(data, o)
            if (pid != 0) continue
            val po = payloadOffset(data, o) + 1
            if (po >= data.size || b(data, po) != 0x00) continue
            val secLen = secLen(data, po)
            var pos = po + 8
            val end = po + 3 + secLen - 4
            while (pos + 4 <= Math.min(end, data.size - 4)) {
                val prog = (b(data, pos) shl 8) or b(data, pos + 1)
                val pp = ((data[pos + 2].toInt() and 0x1F) shl 8) or b(data, pos + 3)
                if (prog != 0) {
                    pmtPids.add(pp)
                    Log.d(TAG, "PAT: Program $prog -> PMT PID $pp")
                }
                pos += 4
            }
        }

        // Pass 2: PMT → find ALL audio PIDs
        for (i in 0 until n) {
            val o = i * PKT
            if (b(data, o) != 0x47) continue
            val pid = pid(data, o)
            if (pid !in pmtPids) continue
            if ((data[o + 1].toInt() and 0x40) == 0) continue
            val po = payloadOffset(data, o) + 1
            if (po >= data.size || b(data, po) != 0x02) continue
            val secLen = secLen(data, po)
            val pcrPid = ((data[po + 8].toInt() and 0x1F) shl 8) or b(data, po + 9)
            Log.d(TAG, "PMT PID $pid: Master PCR PID is $pcrPid")

            val progInfoLen = ((data[po + 10].toInt() and 0x0F) shl 8) or b(data, po + 11)
            var pos = po + 12 + progInfoLen
            val end = po + 3 + secLen - 4
            while (pos + 5 <= Math.min(end, data.size - 5)) {
                val type = b(data, pos)
                val esPid = ((data[pos + 1].toInt() and 0x1F) shl 8) or b(data, pos + 2)
                val esInfoLen = ((data[pos + 3].toInt() and 0x0F) shl 8) or b(data, pos + 4)
                
                if (type in AUDIO_TYPES) {
                    val lang = readLang(data, pos + 5, pos + 5 + esInfoLen)
                    Log.d(TAG, "PMT Stream: Type=0x${type.toString(16)} PID=$esPid Lang=$lang")
                    result.add(AudioPid(esPid, lang))
                } else if (type == 0x1b || type == 0x24 || type == 0x02) {
                    Log.d(TAG, "PMT Stream: Video Type=0x${type.toString(16)} PID=$esPid")
                }
                pos += 5 + esInfoLen
            }
        }
        return result
    }

    private fun readLang(data: ByteArray, from: Int, to: Int): String {
        var dp = from
        while (dp + 2 <= to) {
            val tag = b(data, dp); val len = b(data, dp + 1)
            if (tag == 0x0A && len >= 4) return String(data, dp + 2, 3, Charsets.US_ASCII).trim()
            dp += 2 + len
        }
        return ""
    }

    private fun b(data: ByteArray, i: Int) = data[i].toInt() and 0xFF
    private fun pid(data: ByteArray, o: Int) = ((data[o + 1].toInt() and 0x1F) shl 8) or b(data, o + 2)
    private fun secLen(data: ByteArray, po: Int) = ((data[po + 1].toInt() and 0x0F) shl 8) or b(data, po + 2)
    private fun payloadOffset(data: ByteArray, o: Int): Int {
        var po = o + 4
        if ((data[o + 3].toInt() and 0x20) != 0) po += b(data, po) + 1
        return po
    }
}
