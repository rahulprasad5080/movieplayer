package com.example.videoplayer.player;

import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the PAT/PMT of the first MPEG-TS segment in an HLS media playlist
 * to discover ALL embedded audio PIDs and their ISO 639-2 language codes.
 *
 * ExoPlayer's HLS source ignores multi-audio PIDs when there are no
 * #EXT-X-MEDIA declarations. This parser bypasses that limitation.
 */
public class TsPmtParser {

    private static final String TAG = "TsPmtParser";
    private static final int PKT = 188;
    private static final java.util.Set<Integer> AUDIO_TYPES = new java.util.HashSet<>();
    static {
        AUDIO_TYPES.add(0x0F);
        AUDIO_TYPES.add(0x11);
        AUDIO_TYPES.add(0x03);
        AUDIO_TYPES.add(0x04);
        AUDIO_TYPES.add(0x81);
        AUDIO_TYPES.add(0x82);
    }

    public static class AudioPid {
        private final int pid;
        private final String language;

        public AudioPid(int pid, String language) {
            this.pid = pid;
            this.language = language != null ? language : "";
        }

        public int getPid() { return pid; }
        public String getLanguage() { return language; }

        @Override
        public String toString() {
            return "AudioPid{pid=" + pid + ", language='" + language + "'}";
        }
    }

    /**
     * Extracts audio PIDs from the first TS segment of an HLS playlist.
     * This is a blocking call — should be run on a background thread.
     */
    public List<AudioPid> extractAudioPids(String playlistUrl) {
        try {
            String segUrl = firstSegmentUrl(playlistUrl);
            if (segUrl == null) return java.util.Collections.emptyList();
            Log.d(TAG, "Parsing segment: " + segUrl);
            // Scan 512KB of the first segment
            byte[] data = download(segUrl, 512 * 1024);
            return parse(data);
        } catch (Exception e) {
            Log.e(TAG, "TS parse error", e);
            return java.util.Collections.emptyList();
        }
    }

    private String firstSegmentUrl(String playlistUrl) {
        try {
            URL url = new URL(playlistUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            List<String> lines;
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } finally {
                conn.disconnect();
            }

            String segmentLine = null;
            for (String l : lines) {
                String trimmed = l.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    segmentLine = trimmed;
                    break;
                }
            }
            if (segmentLine == null) return null;

            if (segmentLine.startsWith("http")) return segmentLine;

            // Preserve query parameters for the segment request
            String query = "";
            int qIdx = playlistUrl.indexOf('?');
            if (qIdx >= 0) query = playlistUrl.substring(qIdx + 1);

            String baseUrl = (qIdx >= 0 ? playlistUrl.substring(0, qIdx) : playlistUrl);
            int lastSlash = baseUrl.lastIndexOf('/');
            baseUrl = lastSlash >= 0 ? baseUrl.substring(0, lastSlash) : baseUrl;

            String finalUrl = baseUrl + "/" + segmentLine;
            if (!query.isEmpty() && !finalUrl.contains("?")) {
                finalUrl = finalUrl + "?" + query;
            }
            return finalUrl;

        } catch (Exception e) {
            Log.e(TAG, "Failed to read playlist lines from " + playlistUrl, e);
            return null;
        }
    }

    private byte[] download(String url, int maxBytes) {
        try {
            URL connUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) connUrl.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("Range", "bytes=0-" + (maxBytes - 1));
            try {
                InputStream input = conn.getInputStream();
                byte[] buffer = new byte[maxBytes];
                int totalRead = 0;
                while (totalRead < maxBytes) {
                    int bytesRead = input.read(buffer, totalRead, maxBytes - totalRead);
                    if (bytesRead == -1) break;
                    totalRead += bytesRead;
                }
                byte[] result = new byte[totalRead];
                System.arraycopy(buffer, 0, result, 0, totalRead);
                return result;
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, "Download error", e);
            return new byte[0];
        }
    }

    private List<AudioPid> parse(byte[] data) {
        int n = data.length / PKT;
        java.util.Set<Integer> pmtPids = new java.util.HashSet<>();
        List<AudioPid> result = new ArrayList<>();

        // Pass 1: PAT -> find ALL PMT PIDs
        for (int i = 0; i < n; i++) {
            int o = i * PKT;
            if (b(data, o) != 0x47) continue;
            int pid = pid(data, o);
            if (pid != 0) continue;
            int po = payloadOffset(data, o) + 1;
            if (po >= data.length || b(data, po) != 0x00) continue;
            int secLen = secLen(data, po);
            int pos = po + 8;
            int end = po + 3 + secLen - 4;
            while (pos + 4 <= Math.min(end, data.length - 4)) {
                int prog = (b(data, pos) << 8) | b(data, pos + 1);
                int pp = ((data[pos + 2] & 0x1F) << 8) | b(data, pos + 3);
                if (prog != 0) {
                    pmtPids.add(pp);
                    Log.d(TAG, "PAT: Program " + prog + " -> PMT PID " + pp);
                }
                pos += 4;
            }
        }

        // Pass 2: PMT -> find ALL audio PIDs
        for (int i = 0; i < n; i++) {
            int o = i * PKT;
            if (b(data, o) != 0x47) continue;
            int pid = pid(data, o);
            if (!pmtPids.contains(pid)) continue;
            if ((data[o + 1] & 0x40) == 0) continue;
            int po = payloadOffset(data, o) + 1;
            if (po >= data.length || b(data, po) != 0x02) continue;
            int secLen = secLen(data, po);
            int pcrPid = ((data[po + 8] & 0x1F) << 8) | b(data, po + 9);
            Log.d(TAG, "PMT PID " + pid + ": Master PCR PID is " + pcrPid);

            int progInfoLen = ((data[po + 10] & 0x0F) << 8) | b(data, po + 11);
            int pos = po + 12 + progInfoLen;
            int end = po + 3 + secLen - 4;
            while (pos + 5 <= Math.min(end, data.length - 5)) {
                int type = b(data, pos);
                int esPid = ((data[pos + 1] & 0x1F) << 8) | b(data, pos + 2);
                int esInfoLen = ((data[pos + 3] & 0x0F) << 8) | b(data, pos + 4);

                if (AUDIO_TYPES.contains(type)) {
                    String lang = readLang(data, pos + 5, pos + 5 + esInfoLen);
                    Log.d(TAG, "PMT Stream: Type=0x" + Integer.toString(type, 16) + " PID=" + esPid + " Lang=" + lang);
                    result.add(new AudioPid(esPid, lang));
                } else if (type == 0x1b || type == 0x24 || type == 0x02) {
                    Log.d(TAG, "PMT Stream: Video Type=0x" + Integer.toString(type, 16) + " PID=" + esPid);
                }
                pos += 5 + esInfoLen;
            }
        }
        return result;
    }

    private String readLang(byte[] data, int from, int to) {
        int dp = from;
        while (dp + 2 <= to) {
            int tag = b(data, dp);
            int len = b(data, dp + 1);
            if (tag == 0x0A && len >= 4) {
                try {
                    return new String(data, dp + 2, 3, "US-ASCII").trim();
                } catch (Exception e) {
                    return "";
                }
            }
            dp += 2 + len;
        }
        return "";
    }

    private int b(byte[] data, int i) { return data[i] & 0xFF; }
    private int pid(byte[] data, int o) { return ((data[o + 1] & 0x1F) << 8) | b(data, o + 2); }
    private int secLen(byte[] data, int po) { return ((data[po + 1] & 0x0F) << 8) | b(data, po + 2); }
    private int payloadOffset(byte[] data, int o) {
        int po = o + 4;
        if ((data[o + 3] & 0x20) != 0) po += b(data, po) + 1;
        return po;
    }
}
