package ru.flawden.BascovDiscordBot.lavaplayer;

/**
 * Confirms that Discord has started requesting media frames for a loaded track.
 */
public record PlaybackReadinessResult(Status status, String details) {

    public boolean ready() {
        return status == Status.READY;
    }

    public enum Status {
        READY,
        VOICE_LEFT,
        FRAME_TIMEOUT,
        SESSION_CLOSED,
        TRACK_REPLACED
    }

    public static PlaybackReadinessResult readyResult() {
        return new PlaybackReadinessResult(Status.READY, "Discord media transport is polling audio frames.");
    }

    public static PlaybackReadinessResult failure(Status status, String details) {
        if (status == Status.READY) {
            throw new IllegalArgumentException("READY must use readyResult()");
        }
        return new PlaybackReadinessResult(status, details);
    }
}
