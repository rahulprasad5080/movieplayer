package com.example.videoplayer.model;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Repository responsible for fetching a fresh, signed video URL from the backend.
 */
public class VideoUrlRepository {

    private static final String TAG = "VideoUrlRepository";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private final String videoUrlApiEndpoint;

    public VideoUrlRepository(String videoUrlApiEndpoint) {
        this.videoUrlApiEndpoint = videoUrlApiEndpoint != null ? videoUrlApiEndpoint.trim() : "";
    }

    /**
     * Fetches the video URL for the given language ID.
     * This is a blocking call — should be run on a background thread.
     */
    public Result<String> fetchVideoUrl(int languageId) {
        try {
            String endpoint = videoUrlApiEndpoint.trim();
            Log.i(TAG, "[PlayerLog] VideoUrlRepository.fetchVideoUrl() called:");
            Log.i(TAG, "  -> Base endpoint: '" + endpoint + "'");
            Log.i(TAG, "  -> Target languageId: " + languageId);

            // If the provided endpoint is already a direct .m3u8 video URL, return it directly
            if (endpoint.toLowerCase().contains(".m3u8")) {
                Log.i(TAG, "[PlayerLog] Endpoint is a direct m3u8 stream URL. Returning directly: " + endpoint);
                return Result.success(endpoint);
            }

            // Append languageId query parameter
            String separator = endpoint.contains("?") ? "&" : "?";
            String urlWithParam = endpoint + separator + "languageId=" + languageId;

            Log.i(TAG, "[PlayerLog] Sending HTTP GET to: '" + urlWithParam + "'");

            URL url = new URL(urlWithParam);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                String responseBody;
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    responseBody = sb.toString();
                } catch (Exception e) {
                    String errStream = "";
                    try {
                        BufferedReader errReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = errReader.readLine()) != null) {
                            sb.append(line);
                        }
                        errStream = sb.toString();
                    } catch (Exception ignored) {}
                    Log.e(TAG, "[PlayerLog] HTTP error reading stream [" + responseCode + "]: " + errStream, e);
                    throw e;
                }

                Log.i(TAG, "[PlayerLog] HTTP Response Code: " + responseCode);
                Log.i(TAG, "[PlayerLog] HTTP Response Body: " + responseBody);

                if (responseCode != 200) {
                    Log.e(TAG, "[PlayerLog] HTTP Error response (" + responseCode + ") for languageId=" + languageId);
                    return Result.failure(
                            new Exception("API returned HTTP " + responseCode + " for languageId=" + languageId)
                    );
                }

                JSONObject json = new JSONObject(responseBody);
                int code = json.optInt("code", -1);
                Log.i(TAG, "[PlayerLog] Parsed JSON 'code' field: " + code);

                if (code != 200) {
                    String msg = json.optString("msg", "Unknown error");
                    Log.e(TAG, "[PlayerLog] API returned error code=" + code + ", msg='" + msg + "'");
                    return Result.failure(new Exception("API returned code=" + code + " msg='" + msg + "'"));
                }

                JSONObject data = json.optJSONObject("data");
                if (data == null) {
                    Log.e(TAG, "[PlayerLog] API response missing 'data' object!");
                    return Result.failure(new Exception("API response missing 'data' field"));
                }

                String videoUrl = data.optString("videoUrl", "").trim();
                Log.i(TAG, "[PlayerLog] Extracted 'videoUrl' from JSON data: '" + videoUrl + "'");

                if (videoUrl.isEmpty()) {
                    Log.e(TAG, "[PlayerLog] API returned blank videoUrl!");
                    return Result.failure(new Exception("API returned blank videoUrl"));
                }

                Log.i(TAG, "[PlayerLog] SUCCESS: Fresh video URL fetched for languageId=" + languageId + " -> '" + videoUrl + "'");
                return Result.success(videoUrl);

            } finally {
                conn.disconnect();
            }

        } catch (Exception e) {
            Log.e(TAG, "[PlayerLog] Exception in fetchVideoUrl for languageId=" + languageId + ": " + e.getMessage(), e);
            return Result.failure(e);
        }
    }

    /**
     * Simple Result class to replace Kotlin's Result type.
     */
    public static class Result<T> {
        private final T value;
        private final Throwable error;
        private final boolean isSuccess;

        private Result(T value, Throwable error, boolean isSuccess) {
            this.value = value;
            this.error = error;
            this.isSuccess = isSuccess;
        }

        public static <T> Result<T> success(T value) {
            return new Result<>(value, null, true);
        }

        public static <T> Result<T> failure(Throwable error) {
            return new Result<>(null, error, false);
        }

        public boolean isSuccess() { return isSuccess; }
        public T getValue() { return value; }
        public Throwable getError() { return error; }

        public void fold(java.util.function.Consumer<T> onSuccess,
                         java.util.function.Consumer<Throwable> onFailure) {
            if (isSuccess) {
                onSuccess.accept(value);
            } else {
                onFailure.accept(error);
            }
        }
    }
}
