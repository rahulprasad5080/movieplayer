package com.example.videoplayer.player;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A DataSource that intercepts custom proxy schemes and performs
 * on-the-fly M3U8 transformation.
 */
@OptIn(markerClass = UnstableApi.class)
public class ProxyDataSource implements DataSource {

    private static final String TAG = "ProxyDS";

    private final DataSource httpDataSource;
    private final TsPmtParser pmtParser;
    private final PlaylistProxy playlistProxy;

    private InputStream currentInputStream;
    private Uri currentUri;

    public ProxyDataSource(DataSource httpDataSource, TsPmtParser pmtParser, PlaylistProxy playlistProxy) {
        this.httpDataSource = httpDataSource;
        this.pmtParser = pmtParser;
        this.playlistProxy = playlistProxy;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        httpDataSource.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws java.io.IOException {
        Uri uri = dataSpec.uri;
        currentUri = uri;

        String scheme = uri.getScheme();
        if (PlaylistProxy.SCHEME_MANIFEST.equals(scheme)) {
            String originalUrl = uri.getQueryParameter(PlaylistProxy.PARAM_URL);
            if (originalUrl == null) return -1;
            Log.d(TAG, "Requested Master Playlist: " + originalUrl);

            // Scan TS for PIDs (blocking)
            java.util.List<TsPmtParser.AudioPid> pids = pmtParser.extractAudioPids(originalUrl);
            // Deduplicate by pid
            java.util.LinkedHashSet<TsPmtParser.AudioPid> deduped = new java.util.LinkedHashSet<>();
            for (TsPmtParser.AudioPid ap : pids) {
                boolean alreadyHas = false;
                for (TsPmtParser.AudioPid existing : deduped) {
                    if (existing.getPid() == ap.getPid()) {
                        alreadyHas = true;
                        break;
                    }
                }
                if (!alreadyHas) deduped.add(ap);
            }
            java.util.List<TsPmtParser.AudioPid> distinctPids = new java.util.ArrayList<>(deduped);

            ProxyAudioMetadataStore.getInstance().save(originalUrl, distinctPids);
            Log.d(TAG, "Found PIDs: " + distinctPids);

            String master = playlistProxy.createMasterPlaylist(originalUrl, distinctPids);
            Log.d(TAG, "Generated Master:\n" + master);

            byte[] bytes = master.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            currentInputStream = new ByteArrayInputStream(bytes);
            return bytes.length;

        } else if (PlaylistProxy.SCHEME_AUDIO.equals(scheme)) {
            String originalUrl = uri.getQueryParameter(PlaylistProxy.PARAM_URL);
            if (originalUrl == null) return -1;
            String targetPid = uri.getQueryParameter(PlaylistProxy.PARAM_PID);
            if (targetPid == null) targetPid = "video_only";
            Log.d(TAG, "Requested Media Playlist: " + originalUrl + " (Target PID: " + targetPid + ")");

            java.util.List<TsPmtParser.AudioPid> discoveredPids = ProxyAudioMetadataStore.getInstance().get(originalUrl);
            if (discoveredPids.isEmpty()) {
                discoveredPids = pmtParser.extractAudioPids(originalUrl);
                // Deduplicate
                java.util.LinkedHashSet<TsPmtParser.AudioPid> deduped = new java.util.LinkedHashSet<>();
                for (TsPmtParser.AudioPid ap : discoveredPids) {
                    boolean alreadyHas = false;
                    for (TsPmtParser.AudioPid existing : deduped) {
                        if (existing.getPid() == ap.getPid()) {
                            alreadyHas = true;
                            break;
                        }
                    }
                    if (!alreadyHas) deduped.add(ap);
                }
                discoveredPids = new java.util.ArrayList<>(deduped);
                ProxyAudioMetadataStore.getInstance().save(originalUrl, discoveredPids);
            }

            int basePid = discoveredPids.isEmpty()
                    ? (targetPid.contains("video_only") ? -1 : Integer.parseInt(targetPid))
                    : discoveredPids.get(0).getPid();

            // 1. Fetch original media playlist
            String originalContent = fetchUrl(originalUrl);
            // 2. Virtualize it (rewrite segment URLs)
            java.util.List<Integer> audioPidInts = new java.util.ArrayList<>();
            for (TsPmtParser.AudioPid ap : discoveredPids) {
                audioPidInts.add(ap.getPid());
            }
            String virtualized = playlistProxy.virtualizeMediaPlaylist(
                    originalContent, originalUrl, targetPid, basePid, audioPidInts
            );

            byte[] bytes = virtualized.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            currentInputStream = new ByteArrayInputStream(bytes);
            return bytes.length;

        } else {
            return httpDataSource.open(dataSpec);
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws java.io.IOException {
        if (currentInputStream != null) {
            try {
                int read = currentInputStream.read(buffer, offset, length);
                return read == -1 ? androidx.media3.common.C.RESULT_END_OF_INPUT : read;
            } catch (java.io.IOException e) {
                return androidx.media3.common.C.RESULT_END_OF_INPUT;
            }
        }
        return httpDataSource.read(buffer, offset, length);
    }

    @Override
    public Uri getUri() {
        return currentUri != null ? currentUri : httpDataSource.getUri();
    }

    @Override
    public void close() throws java.io.IOException {
        try {
            if (currentInputStream != null) {
                currentInputStream.close();
                currentInputStream = null;
            }
        } catch (java.io.IOException ignored) {}
        currentUri = null;
        httpDataSource.close();
    }

    @Override
    public java.util.Map<String, java.util.List<String>> getResponseHeaders() {
        return httpDataSource.getResponseHeaders();
    }

    private String fetchUrl(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            try {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream())
                );
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchUrl error: " + url, e);
            return "";
        }
    }
}
