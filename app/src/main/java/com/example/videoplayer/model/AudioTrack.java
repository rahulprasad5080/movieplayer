package com.example.videoplayer.model;

import java.util.Objects;

/**
 * Data class representing an audio track available in the HLS stream.
 */
public class AudioTrack {

    private final int index;
    private final int groupIndex;
    private final String language;
    private final String label;
    private final boolean isSelected;
    private final boolean existIndividualVideo;
    private final String playbackUrl;
    private final Integer languageId;
    private final String abbreviate;
    private final Integer order;

    public AudioTrack(
            int index,
            int groupIndex,
            String language,
            String label,
            boolean isSelected,
            boolean existIndividualVideo,
            String playbackUrl,
            Integer languageId,
            String abbreviate,
            Integer order
    ) {
        this.index = index;
        this.groupIndex = groupIndex;
        this.language = language;
        this.label = label;
        this.isSelected = isSelected;
        this.existIndividualVideo = existIndividualVideo;
        this.playbackUrl = playbackUrl;
        this.languageId = languageId;
        this.abbreviate = abbreviate;
        this.order = order;
    }

    // Convenience constructor for default values
    public AudioTrack(int index, int groupIndex, String language, String label, boolean isSelected) {
        this(index, groupIndex, language, label, isSelected, false, null, null, null, null);
    }

    public int getIndex() { return index; }
    public int getGroupIndex() { return groupIndex; }
    public String getLanguage() { return language; }
    public String getLabel() { return label; }
    public boolean isSelected() { return isSelected; }
    public boolean isExistIndividualVideo() { return existIndividualVideo; }
    public String getPlaybackUrl() { return playbackUrl; }
    public Integer getLanguageId() { return languageId; }
    public String getAbbreviate() { return abbreviate; }
    public Integer getOrder() { return order; }

    public AudioTrack withSelected(boolean selected) {
        return new AudioTrack(
                index, groupIndex, language, label, selected,
                existIndividualVideo, playbackUrl, languageId, abbreviate, order
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioTrack that = (AudioTrack) o;
        return index == that.index &&
                groupIndex == that.groupIndex &&
                isSelected == that.isSelected &&
                existIndividualVideo == that.existIndividualVideo &&
                Objects.equals(language, that.language) &&
                Objects.equals(label, that.label) &&
                Objects.equals(playbackUrl, that.playbackUrl) &&
                Objects.equals(languageId, that.languageId) &&
                Objects.equals(abbreviate, that.abbreviate) &&
                Objects.equals(order, that.order);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, groupIndex, language, label, isSelected,
                existIndividualVideo, playbackUrl, languageId, abbreviate, order);
    }

    @Override
    public String toString() {
        return "AudioTrack{" +
                "index=" + index +
                ", groupIndex=" + groupIndex +
                ", language='" + language + '\'' +
                ", label='" + label + '\'' +
                ", isSelected=" + isSelected +
                ", existIndividualVideo=" + existIndividualVideo +
                ", playbackUrl='" + playbackUrl + '\'' +
                ", languageId=" + languageId +
                ", abbreviate='" + abbreviate + '\'' +
                ", order=" + order +
                '}';
    }
}
