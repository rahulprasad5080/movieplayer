package com.example.videoplayer.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TrackApiModels {

    private TrackApiModels() {}

    public static class ApiTrackVideo {
        private final Integer resolution;
        private final String resolutionDescription;
        private final Long size;
        private final boolean premiumProPermission;
        private final String playbackUrl;

        public ApiTrackVideo(Integer resolution, String resolutionDescription,
                             Long size, boolean premiumProPermission, String playbackUrl) {
            this.resolution = resolution;
            this.resolutionDescription = resolutionDescription;
            this.size = size;
            this.premiumProPermission = premiumProPermission;
            this.playbackUrl = playbackUrl;
        }

        public Integer getResolution() { return resolution; }
        public String getResolutionDescription() { return resolutionDescription; }
        public Long getSize() { return size; }
        public boolean isPremiumProPermission() { return premiumProPermission; }
        public String getPlaybackUrl() { return playbackUrl; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ApiTrackVideo that = (ApiTrackVideo) o;
            return premiumProPermission == that.premiumProPermission &&
                    Objects.equals(resolution, that.resolution) &&
                    Objects.equals(resolutionDescription, that.resolutionDescription) &&
                    Objects.equals(size, that.size) &&
                    Objects.equals(playbackUrl, that.playbackUrl);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resolution, resolutionDescription, size, premiumProPermission, playbackUrl);
        }

        @Override
        public String toString() {
            return "ApiTrackVideo{resolution=" + resolution +
                    ", resolutionDescription='" + resolutionDescription + '\'' +
                    ", size=" + size +
                    ", premiumProPermission=" + premiumProPermission +
                    ", playbackUrl='" + playbackUrl + '\'' + '}';
        }
    }

    public static class ApiTrackConfig {
        private final Integer languageId;
        private final String languageName;
        private final String abbreviate;
        private final boolean isDefault;
        private final boolean existIndividualVideo;
        private final Integer order;
        private final Integer index;
        private final List<ApiTrackVideo> videos;
        private final String playbackUrl;

        public ApiTrackConfig(Integer languageId, String languageName, String abbreviate,
                              boolean isDefault, boolean existIndividualVideo,
                              Integer order, Integer index,
                              List<ApiTrackVideo> videos, String playbackUrl) {
            this.languageId = languageId;
            this.languageName = languageName != null ? languageName : "Unknown";
            this.abbreviate = abbreviate;
            this.isDefault = isDefault;
            this.existIndividualVideo = existIndividualVideo;
            this.order = order;
            this.index = index;
            this.videos = videos != null ? Collections.unmodifiableList(videos) : Collections.emptyList();
            this.playbackUrl = playbackUrl;
        }

        public Integer getLanguageId() { return languageId; }
        public String getLanguageName() { return languageName; }
        public String getAbbreviate() { return abbreviate; }
        public boolean isDefault() { return isDefault; }
        public boolean isExistIndividualVideo() { return existIndividualVideo; }
        public Integer getOrder() { return order; }
        public Integer getIndex() { return index; }
        public List<ApiTrackVideo> getVideos() { return videos; }
        public String getPlaybackUrl() { return playbackUrl; }
    }

    /**
     * Parser for tracks JSON payload.
     */
    public static class TrackApiParser {

        private TrackApiParser() {}

        public static List<ApiTrackConfig> parseTracksPayload(String rawPayload) {
            if (rawPayload == null) return Collections.emptyList();
            String trimmed = rawPayload.trim();
            if (trimmed.isEmpty()) return Collections.emptyList();

            JSONArray array = parseAsArray(trimmed);
            if (array == null) return Collections.emptyList();

            List<ApiTrackConfig> result = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    result.add(parseTrack(item));
                }
            }
            return result;
        }

        private static JSONArray parseAsArray(String payload) {
            if (payload.startsWith("[")) {
                try {
                    return new JSONArray(payload);
                } catch (Exception e) {
                    return null;
                }
            } else if (payload.startsWith("{")) {
                try {
                    JSONObject root = new JSONObject(payload);
                    JSONArray tracks = root.optJSONArray("tracks");
                    if (tracks != null) return tracks;
                    JSONArray data = root.optJSONArray("data");
                    if (data != null) return data;
                    JSONArray results = root.optJSONArray("results");
                    if (results != null) return results;
                    return new JSONArray().put(root);
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        }

        private static ApiTrackConfig parseTrack(JSONObject item) {
            List<ApiTrackVideo> videos = parseVideos(item.optJSONArray("videos"));

            String directUrl = firstNonBlank(
                    optStringOrNull(item, "playbackUrl"),
                    optStringOrNull(item, "videoUrl"),
                    optStringOrNull(item, "streamUrl"),
                    optStringOrNull(item, "url")
            );
            if (directUrl == null) {
                for (ApiTrackVideo v : videos) {
                    if (v.getPlaybackUrl() != null) {
                        directUrl = v.getPlaybackUrl();
                        break;
                    }
                }
            }

            return new ApiTrackConfig(
                    optIntOrNull(item, "languageId"),
                    optStringOrDefault(item, "languageName",
                            optStringOrDefault(item, "name", "Unknown")),
                    optStringOrNull(item, "abbreviate"),
                    item.optBoolean("isDefault", false),
                    item.optBoolean("existIndividualVideo", false),
                    optIntOrNull(item, "order"),
                    optIntOrNull(item, "index"),
                    videos,
                    directUrl
            );
        }

        private static List<ApiTrackVideo> parseVideos(JSONArray array) {
            if (array == null) return Collections.emptyList();
            List<ApiTrackVideo> result = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String url = firstNonBlank(
                        optStringOrNull(item, "playbackUrl"),
                        optStringOrNull(item, "videoUrl"),
                        optStringOrNull(item, "streamUrl"),
                        optStringOrNull(item, "url")
                );
                result.add(new ApiTrackVideo(
                        optIntOrNull(item, "resolution"),
                        optStringOrNull(item, "resolutionDescription"),
                        optLongOrNull(item, "size"),
                        item.optBoolean("premiumProPermission", false),
                        url
                ));
            }
            return result;
        }

        // -- JSON helper methods --

        private static String optStringOrNull(JSONObject obj, String name) {
            String val = obj.optString(name, "").trim();
            return val.isEmpty() ? null : val;
        }

        private static String optStringOrDefault(JSONObject obj, String name, String defaultVal) {
            String val = obj.optString(name, "").trim();
            return val.isEmpty() ? defaultVal : val;
        }

        private static Integer optIntOrNull(JSONObject obj, String name) {
            if (obj.has(name) && !obj.isNull(name)) {
                return obj.optInt(name);
            }
            return null;
        }

        private static Long optLongOrNull(JSONObject obj, String name) {
            if (obj.has(name) && !obj.isNull(name)) {
                return obj.optLong(name);
            }
            return null;
        }

        private static String firstNonBlank(String... values) {
            for (String v : values) {
                if (v != null && !v.trim().isEmpty()) {
                    return v.trim();
                }
            }
            return null;
        }
    }
}
