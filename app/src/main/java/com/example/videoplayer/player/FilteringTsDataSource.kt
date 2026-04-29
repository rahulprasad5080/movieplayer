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
    private val ccMap: IntArray
) : DataSource {

    companion object {
        private const val PKT = 188
    }

    class Factory(private val upstreamFactory: DataSource.Factory) : DataSource.Factory {
        private val sharedCcMap = IntArray(8192) { -1 }
        override fun createDataSource() = FilteringTsDataSource(upstreamFactory.createDataSource(), sharedCcMap)
    }

    private var targetPid: Int = -1
    private var basePid: Int = -1
    private var audioPids: Set<Int> = emptySet()

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
        // Removed CC map reset to maintain continuity across segments and seamless track switches
        return upstream.open(dataSpec)
    }

    private var remainder = ByteArray(PKT)
    private var remainderLen = 0

    private fun processPacket(outBuf: ByteArray, i: Int) {
        val pid = ((outBuf[i + 1].toInt() and 0x1F) shl 8) or (outBuf[i + 2].toInt() and 0xFF)
        val afc = (outBuf[i + 3].toInt() and 0x30) shr 4
        val hasPayload = (afc == 1 || afc == 3)

        // PCR/Sync Preservation
        var hasPcr = false
        if (afc >= 2 && i + 5 < outBuf.size) {
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
                if (hasPayload && afc == 3) {
                    // Strip audio payload so it doesn't mix with targetPid audio
                    val oldAfLen = outBuf[i + 4].toInt() and 0xFF
                    outBuf[i + 3] = (outBuf[i + 3].toInt() and 0xCF or 0x20).toByte()
                    outBuf[i + 4] = 183.toByte()
                    for (p in (i + 5 + oldAfLen) until (i + PKT)) {
                        outBuf[p] = 0xFF.toByte()
                    }
                }
                updateCc(outBuf, i, basePid, false)
            }
        } else if (pid in audioPids && pid != targetPid) {
            // Mute all other audio tracks
            if (!hasPcr) {
                outBuf[i + 1] = (outBuf[i + 1].toInt() or 0x1F).toByte()
                outBuf[i + 2] = 0xFF.toByte()
            }
        }
        // Video and other PIDs pass through naturally
    }

    override fun read(outBuf: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (targetPid == -1) return upstream.read(outBuf, offset, length)

        if (remainderLen > 0 && length <= remainderLen) {
            System.arraycopy(remainder, 0, outBuf, offset, length)
            remainderLen -= length
            System.arraycopy(remainder, length, remainder, 0, remainderLen)
            return length
        }

        System.arraycopy(remainder, 0, outBuf, offset, remainderLen)
        val currentOffset = offset + remainderLen
        val remainingLength = length - remainderLen

        val bytesRead = upstream.read(outBuf, currentOffset, remainingLength)
        
        val totalBytes = if (bytesRead > 0) remainderLen + bytesRead else remainderLen
        if (totalBytes == 0) return bytesRead

        var i = offset
        val end = offset + totalBytes
        var lastValidPacketEnd = offset
        
        while (i + PKT <= end) {
            if (outBuf[i] == 0x47.toByte()) {
                processPacket(outBuf, i)
                i += PKT
                lastValidPacketEnd = i
            } else {
                i++
                lastValidPacketEnd = i
            }
        }
        
        val leftover = end - lastValidPacketEnd
        if (leftover > 0) {
            if (remainder.size < leftover) {
                remainder = ByteArray(leftover + PKT)
            }
            System.arraycopy(outBuf, lastValidPacketEnd, remainder, 0, leftover)
            remainderLen = leftover
        } else {
            remainderLen = 0
        }
        
        val validBytesToReturn = lastValidPacketEnd - offset
        if (validBytesToReturn > 0) {
            return validBytesToReturn
        } else if (bytesRead == androidx.media3.common.C.RESULT_END_OF_INPUT) {
            remainderLen = 0
            return leftover
        } else {
            return read(outBuf, offset, length)
        }
    }

    private fun updateCc(buf: ByteArray, i: Int, pid: Int, hasPayload: Boolean) {
        var cc = ccMap[pid]
        if (hasPayload) {
            cc = if (cc == -1) 0 else (cc + 1) and 0x0F
            ccMap[pid] = cc
        } else {
            if (cc == -1) cc = 0
        }
        buf[i + 3] = (buf[i + 3].toInt() and 0xF0 or cc).toByte()
    }

    override fun getUri(): Uri? = upstream.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders
    
    @Throws(IOException::class)
    override fun close() = upstream.close()
}
