package com.example.videoplayer.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Repository responsible for fetching a fresh, signed video URL from the backend.
 */
class VideoUrlRepository(private val videoUrlApiEndpoint: String) {

    companion object {
        private const val TAG = "VideoUrlRepository"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }

    suspend fun fetchVideoUrl(languageId: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val endpoint = videoUrlApiEndpoint.trim()
            Log.i(TAG, "[PlayerLog] VideoUrlRepository.fetchVideoUrl() called:")
            Log.i(TAG, "  -> Base endpoint: '$endpoint'")
            Log.i(TAG, "  -> Target languageId: $languageId")

            // If the provided endpoint is ALREADY a direct .m3u8 video URL (e.g. pasted for testing),
            // return it directly without trying to query it as a JSON API.
            if (endpoint.contains(".m3u8", ignoreCase = true)) {
                Log.i(TAG, "[PlayerLog] Endpoint is a direct m3u8 stream URL. Returning directly: $endpoint")
                return@withContext Result.success(endpoint)
            }

            // Append languageId query parameter to the base endpoint URL
            val separator = if (endpoint.contains('?')) "&" else "?"
            val urlWithParam = "$endpoint${separator}languageId=$languageId"

            Log.i(TAG, "[PlayerLog] Sending HTTP GET to: '$urlWithParam'")

            val conn = URL(urlWithParam).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.requestMethod = "GET"

            val responseCode = conn.responseCode
            val responseBody = try {
                conn.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                val errStream = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                Log.e(TAG, "[PlayerLog] HTTP error reading stream [$responseCode]: $errStream", e)
                throw e
            } finally {
                conn.disconnect()
            }

            Log.i(TAG, "[PlayerLog] HTTP Response Code: $responseCode")
            Log.i(TAG, "[PlayerLog] HTTP Response Body: $responseBody")

            if (responseCode != 200) {
                Log.e(TAG, "[PlayerLog] HTTP Error response ($responseCode) for languageId=$languageId")
                return@withContext Result.failure(
                    Exception("API returned HTTP $responseCode for languageId=$languageId")
                )
            }

            val json = JSONObject(responseBody)
            val code = json.optInt("code", -1)
            Log.i(TAG, "[PlayerLog] Parsed JSON 'code' field: $code")

            if (code != 200) {
                val msg = json.optString("msg", "Unknown error")
                Log.e(TAG, "[PlayerLog] API returned error code=$code, msg='$msg'")
                return@withContext Result.failure(
                    Exception("API returned code=$code msg='$msg'")
                )
            }

            val data = json.optJSONObject("data")
            if (data == null) {
                Log.e(TAG, "[PlayerLog] API response missing 'data' object!")
                return@withContext Result.failure(Exception("API response missing 'data' field"))
            }

            val videoUrl = data.optString("videoUrl", "").trim()
            Log.i(TAG, "[PlayerLog] Extracted 'videoUrl' from JSON data: '$videoUrl'")

            if (videoUrl.isBlank()) {
                Log.e(TAG, "[PlayerLog] API returned blank videoUrl!")
                return@withContext Result.failure(Exception("API returned blank videoUrl"))
            }

            Log.i(TAG, "[PlayerLog] SUCCESS: Fresh video URL fetched for languageId=$languageId -> '$videoUrl'")
            Result.success(videoUrl)

        } catch (e: Exception) {
            Log.e(TAG, "[PlayerLog] Exception in fetchVideoUrl for languageId=$languageId: ${e.message}", e)
            Result.failure(e)
        }
    }
}
