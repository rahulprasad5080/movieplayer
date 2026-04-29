package com.example.videoplayer.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * A simplified TS filter that selects a PID based on URL parameters.
 */
@OptIn(UnstableApi::class)
class FilteringTsDataSource(
    private val upstream: DataSource,
) : DataSource {

    companion object {
        private const val PKT = 188
    }

    class Factory(private val upstreamFactory: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource() = FilteringTsDataSource(upstreamFactory.createDataSource())
    }

    private var targetPid: Int = -1
    private var basePid: Int = -1
    private var audioPids: Set<Int> = emptySet()
    private val ccMap = IntArray(8192) { -1 }

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        targetPid = dataSpec.uri.getQueryParameter(PlaylistProxy.PARAM_TARGET_PID)?.toIntOrNull() ?: -1
        basePid = dataSpec.uri.getQueryParameter(PlaylistProxy.PARAM_BASE_PID)?.toIntOrNull() ?: targetPid
        audioPids = dataSpec.uri.getQueryParameter(PlaylistProxy.PARAM_AUDIO_PIDS)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            .orEmpty()
            .ifEmpty {
                buildSet {
                    if (basePid != -1) add(basePid)
                    if (targetPid != -1) add(targetPid)
                }
            }

        android.util.Log.d(
            "FilteringTs",
            "Opening segment with targetPid=$targetPid, basePid=$basePid, audioPids=$audioPids (URI: ${dataSpec.uri})"
        )
        // Reset CC map on each new segment to avoid artifacts
        for (i in ccMap.indices) ccMap[i] = -1
        return upstream.open(dataSpec)
    }

    override fun read(outBuf: ByteArray, offset: Int, length: Int): Int {
        val bytesRead = upstream.read(outBuf, offset, length)
        if (bytesRead <= 0 || targetPid == -1) return bytesRead

        var i = offset
        val end = offset + bytesRead
        while (i + PKT <= end) {
            if (outBuf[i] == 0x47.toByte()) {
                val pid = ((outBuf[i + 1].toInt() and 0x1F) shl 8) or (outBuf[i + 2].toInt() and 0xFF)
                val afc = (outBuf[i + 3].toInt() and 0x30) shr 4
                val hasPayload = (afc == 1 || afc == 3)

                // PCR/Sync Preservation
                var hasPcr = false
                if (afc >= 2 && i + 5 < end) {
                    val afLen = outBuf[i + 4].toInt() and 0xFF
                    val afFlags = outBuf[i + 5].toInt() and 0xFF
                    if (afLen > 0 && (afFlags and 0x10) != 0) hasPcr = true
                }

                if (pid == targetPid && targetPid != basePid && basePid != -1) {
                    // Remap selected track to base PID
                    outBuf[i + 1] = (outBuf[i + 1].toInt() and 0xE0 or (basePid shr 8)).toByte()
                    outBuf[i + 2] = (basePid and 0xFF).toByte()
                    updateCc(outBuf, i, basePid, hasPayload)
                } else if (pid == basePid && targetPid != basePid) {
                    // Mute original primary unless it has PCR
                    if (!hasPcr) {
                        outBuf[i + 1] = (outBuf[i + 1].toInt() or 0x1F).toByte()
                        outBuf[i + 2] = 0xFF.toByte()
                    } else {
                        updateCc(outBuf, i, basePid, hasPayload)
                    }
                } else if (pid in audioPids && pid != targetPid) {
                    // Mute all other audio tracks
                    if (!hasPcr) {
                        outBuf[i + 1] = (outBuf[i + 1].toInt() or 0x1F).toByte()
                        outBuf[i + 2] = 0xFF.toByte()
                    }
                }
                // Video and other PIDs pass through naturally
                i += PKT
            } else {
                i++
            }
        }
        return bytesRead
    }

    private fun updateCc(buf: ByteArray, i: Int, pid: Int, hasPayload: Boolean) {
        if (!hasPayload) return
        val nextCC = (ccMap[pid] + 1) and 0x0F
        ccMap[pid] = nextCC
        buf[i + 3] = (buf[i + 3].toInt() and 0xF0 or nextCC).toByte()
    }

    override fun getUri(): Uri? = upstream.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders
    
    @Throws(IOException::class)
    override fun close() = upstream.close()
}
