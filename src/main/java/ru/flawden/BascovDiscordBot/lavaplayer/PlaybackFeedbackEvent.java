package ru.flawden.BascovDiscordBot.lavaplayer;

import java.util.Objects;

/**
 * Minimal playback lifecycle signal for recommendation feedback.
 */
public record PlaybackFeedbackEvent(
        Type type,
        TrackRequest request,
        long elapsedMillis,
        long durationMillis) {

    public PlaybackFeedbackEvent {
        type = Objects.requireNonNull(type, "type");
        request = Objects.requireNonNull(request, "request");
        elapsedMillis = Math.max(0L, elapsedMillis);
        durationMillis = Math.max(1L, durationMillis);
    }

    public double completionRatio() {
        if (type == Type.COMPLETED) {
            return 1.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, elapsedMillis / (double) durationMillis));
    }

    public enum Type {
        COMPLETED,
        SKIPPED
    }
}
