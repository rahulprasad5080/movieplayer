package com.example.videoplayer.model;

import java.util.Collections;
import java.util.List;

/**
 * Sealed class representing all possible UI states for the player screen.
 * Java version uses an abstract base class with concrete subclasses.
 */
public abstract class PlayerState {

    private PlayerState() {}

    /**
     * Initial state before any playback request.
     */
    public static final class Idle extends PlayerState {
        public static final Idle INSTANCE = new Idle();

        private Idle() {}

        @Override
        public String toString() {
            return "PlayerState.Idle";
        }
    }

    /**
     * Player is buffering or preparing the stream.
     */
    public static final class Buffering extends PlayerState {
        public static final Buffering INSTANCE = new Buffering();

        private Buffering() {}

        @Override
        public String toString() {
            return "PlayerState.Buffering";
        }
    }

    /**
     * Player is actively playing or paused.
     */
    public static final class Playing extends PlayerState {
        private final boolean isPlaying;
        private final List<AudioTrack> audioTracks;
        private final int selectedTrackIndex;

        public Playing(boolean isPlaying, List<AudioTrack> audioTracks, int selectedTrackIndex) {
            this.isPlaying = isPlaying;
            this.audioTracks = audioTracks != null
                    ? Collections.unmodifiableList(audioTracks)
                    : Collections.emptyList();
            this.selectedTrackIndex = selectedTrackIndex;
        }

        public Playing(boolean isPlaying) {
            this(isPlaying, Collections.emptyList(), -1);
        }

        public boolean isPlaying() { return isPlaying; }
        public List<AudioTrack> getAudioTracks() { return audioTracks; }
        public int getSelectedTrackIndex() { return selectedTrackIndex; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Playing playing = (Playing) o;
            return isPlaying == playing.isPlaying &&
                    selectedTrackIndex == playing.selectedTrackIndex &&
                    audioTracks.equals(playing.audioTracks);
        }

        @Override
        public int hashCode() {
            int result = (isPlaying ? 1 : 0);
            result = 31 * result + audioTracks.hashCode();
            result = 31 * result + selectedTrackIndex;
            return result;
        }

        @Override
        public String toString() {
            return "PlayerState.Playing{" +
                    "isPlaying=" + isPlaying +
                    ", audioTracks=" + audioTracks +
                    ", selectedTrackIndex=" + selectedTrackIndex +
                    '}';
        }
    }

    /**
     * An error occurred during playback.
     */
    public static final class Error extends PlayerState {
        private final String message;

        public Error(String message) {
            this.message = message != null ? message : "";
        }

        public String getMessage() { return message; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Error error = (Error) o;
            return message.equals(error.message);
        }

        @Override
        public int hashCode() {
            return message.hashCode();
        }

        @Override
        public String toString() {
            return "PlayerState.Error{message='" + message + "'}";
        }
    }
}
