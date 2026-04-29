package com.example.videoplayer.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * A DataSource that intercepts custom proxy schemes and performs 
 * on-the-fly M3U8 transformation.
 */
@OptIn(UnstableApi::class)
class ProxyDataSource(
    private val httpDataSource: DataSource,
    private val pmtParser: TsPmtParser,
    private val playlistProxy: PlaylistProxy
) : DataSource {

    private var currentInputStream: InputStream? = null
    private var currentUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        httpDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        currentUri = uri
        
        when (uri.scheme) {
            PlaylistProxy.SCHEME_MANIFEST -> {
                val originalUrl = uri.getQueryParameter(PlaylistProxy.PARAM_URL) ?: return -1
                android.util.Log.d("ProxyDS", "Requested Master Playlist: $originalUrl")
                // 1. Scan TS for PIDs
                val pids = kotlinx.coroutines.runBlocking { pmtParser.extractAudioPids(originalUrl) }
                    .distinctBy { it.pid }
                ProxyAudioMetadataStore.save(originalUrl, pids)
                android.util.Log.d("ProxyDS", "Found PIDs: $pids")
                // 2. Create Master Playlist
                val master = playlistProxy.createMasterPlaylist(originalUrl, pids)
                android.util.Log.d("ProxyDS", "Generated Master:\n$master")
                val bytes = master.toByteArray()
                currentInputStream = ByteArrayInputStream(bytes)
                return bytes.size.toLong()
            }
            PlaylistProxy.SCHEME_AUDIO -> {
                val originalUrl = uri.getQueryParameter(PlaylistProxy.PARAM_URL) ?: return -1
                val targetPid = uri.getQueryParameter(PlaylistProxy.PARAM_PID) ?: "video_only"
                android.util.Log.d("ProxyDS", "Requested Media Playlist: $originalUrl (Target PID: $targetPid)")

                val discoveredPids = ProxyAudioMetadataStore.get(originalUrl).ifEmpty {
                    kotlinx.coroutines.runBlocking { pmtParser.extractAudioPids(originalUrl) }
                        .distinctBy { it.pid }
                        .also { refreshedPids ->
                            ProxyAudioMetadataStore.save(originalUrl, refreshedPids)
                        }
                }
                val basePid = discoveredPids.firstOrNull()?.pid ?: targetPid.toIntOrNull() ?: -1

                // 1. Fetch original media playlist
                val originalContent = fetchUrl(originalUrl)
                // 2. Virtualize it (rewrite segment URLs)
                val virtualized = playlistProxy.virtualizeMediaPlaylist(
                    originalPlaylist = originalContent,
                    originalUrl = originalUrl,
                    targetPid = targetPid,
                    basePid = basePid,
                    audioPids = discoveredPids.map { it.pid }
                )
                val bytes = virtualized.toByteArray()
                currentInputStream = ByteArrayInputStream(bytes)
                return bytes.size.toLong()
            }
            else -> {
                return httpDataSource.open(dataSpec)
            }
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val stream = currentInputStream
        if (stream != null) {
            val read = stream.read(buffer, offset, length)
            return if (read == -1) androidx.media3.common.C.RESULT_END_OF_INPUT else read
        }
        return httpDataSource.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = currentUri ?: httpDataSource.uri

    override fun close() {
        currentInputStream?.close()
        currentInputStream = null
        currentUri = null
        httpDataSource.close()
    }

    override fun getResponseHeaders(): Map<String, List<String>> = httpDataSource.responseHeaders

    private fun fetchUrl(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }
}
