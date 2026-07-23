package com.example.videoplayer.player;

import android.net.Uri;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility to "virtualize" an HLS manifest.
 * Converts a simple Media Playlist into a Master Playlist with multiple audio tracks.
 */
public class PlaylistProxy {

    private static final String TAG = "PlaylistProxy";

    public static final String SCHEME_MANIFEST = "proxym";
    public static final String SCHEME_AUDIO = "proxya";
    public static final String PARAM_URL = "url";
    public static final String PARAM_PID = "pid";
    public static final String PARAM_TARGET_PID = "targetPid";
    public static final String PARAM_BASE_PID = "basePid";
    public static final String PARAM_AUDIO_PIDS = "audioPids";

    private static final Map<String, String> LANG_MAP = new HashMap<>();
    static {
        LANG_MAP.put("hin", "Hindi"); LANG_MAP.put("hi", "Hindi");
        LANG_MAP.put("tam", "Tamil"); LANG_MAP.put("ta", "Tamil");
        LANG_MAP.put("tel", "Telugu"); LANG_MAP.put("te", "Telugu");
        LANG_MAP.put("eng", "English"); LANG_MAP.put("en", "English");
        LANG_MAP.put("kan", "Kannada"); LANG_MAP.put("kn", "Kannada");
        LANG_MAP.put("mal", "Malayalam"); LANG_MAP.put("ml", "Malayalam");
        LANG_MAP.put("mar", "Marathi"); LANG_MAP.put("mr", "Marathi");
        LANG_MAP.put("ben", "Bengali"); LANG_MAP.put("bn", "Bengali");
        LANG_MAP.put("jpn", "Japanese"); LANG_MAP.put("ja", "Japanese");
    }

    /**
     * Build a virtual Master Playlist that exposes discovered PIDs as renditions.
     */
    public String createMasterPlaylist(String originalUrl, List<TsPmtParser.AudioPid> pids) {
        // Deduplicate by pid
        Set<Integer> seenPids = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        sb.append("#EXT-X-VERSION:4\n\n");

        int audioIndex = 0;
        for (TsPmtParser.AudioPid ap : pids) {
            if (seenPids.contains(ap.getPid())) continue;
            seenPids.add(ap.getPid());

            String isDefault = (audioIndex == 0) ? "YES" : "NO";
            String langKey = ap.getLanguage().toLowerCase();
            String fullName = LANG_MAP.containsKey(langKey) ? LANG_MAP.get(langKey) : "Audio " + (audioIndex + 1);

            String proxyUrl = Uri.parse("")
                    .buildUpon()
                    .scheme(SCHEME_AUDIO)
                    .authority("proxy")
                    .appendQueryParameter(PARAM_URL, originalUrl)
                    .appendQueryParameter(PARAM_PID, String.valueOf(ap.getPid()))
                    .build()
                    .toString();

            sb.append("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"")
                    .append(fullName)
                    .append("\",")
                    .append("LANGUAGE=\"")
                    .append(ap.getLanguage())
                    .append("\",DEFAULT=")
                    .append(isDefault)
                    .append(",AUTOSELECT=YES,URI=\"")
                    .append(proxyUrl)
                    .append("\"\n");

            audioIndex++;
        }

        // Add the Video stream referencing the audio group
        sb.append("\n#EXT-X-STREAM-INF:BANDWIDTH=2000000,AUDIO=\"audio\"\n");
        sb.append(originalUrl).append("\n");

        return sb.toString();
    }

    /**
     * "Virtualizes" a Media Playlist by appending the target PID to every segment URL.
     */
    public String virtualizeMediaPlaylist(
            String originalPlaylist,
            String originalUrl,
            String targetPid,
            int basePid,
            List<Integer> audioPids
    ) {
        String[] lines = originalPlaylist.split("\\n", -1);
        StringBuilder sb = new StringBuilder();
        String audioPidList = "";
        for (int i = 0; i < audioPids.size(); i++) {
            if (i > 0) audioPidList += ",";
            audioPidList += audioPids.get(i);
        }

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("#")) {
                sb.append(trimmed).append("\n");
            } else {
                String fullUrl = resolveSegmentUrl(trimmed, originalUrl);
                Uri rewrittenSegmentUri = Uri.parse(fullUrl)
                        .buildUpon()
                        .appendQueryParameter(PARAM_TARGET_PID, targetPid)
                        .appendQueryParameter(PARAM_BASE_PID, String.valueOf(basePid))
                        .appendQueryParameter(PARAM_AUDIO_PIDS, audioPidList)
                        .build();
                sb.append(rewrittenSegmentUri).append("\n");
            }
        }
        return sb.toString();
    }

    private String resolveSegmentUrl(String segmentPath, String originalUrl) {
        if (segmentPath.startsWith("http")) {
            return segmentPath;
        }

        String query = "";
        int qIdx = originalUrl.indexOf('?');
        if (qIdx >= 0) query = originalUrl.substring(qIdx + 1);

        String baseUrl = (qIdx >= 0 ? originalUrl.substring(0, qIdx) : originalUrl);
        int lastSlash = baseUrl.lastIndexOf('/');
        baseUrl = lastSlash >= 0 ? baseUrl.substring(0, lastSlash) : baseUrl;

        String resolvedUrl = baseUrl + "/" + segmentPath;
        if (!query.isEmpty() && !resolvedUrl.contains("?")) {
            resolvedUrl = resolvedUrl + "?" + query;
        }
        return resolvedUrl;
    }
}
