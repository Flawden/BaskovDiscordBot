package ru.flawden.BascovDiscordBot.lavaplayer;

import java.time.Instant;
import java.util.Objects;

/**
 * Pure state policy used by the asynchronous playback confirmation probe.
 */
final class PlaybackReadinessPolicy {

    private PlaybackReadinessPolicy() {
    }

    static Decision evaluate(
            boolean sessionActive,
            boolean expectedTrackIsCurrent,
            boolean selfInVoiceChannel,
            long baselineFrameRequests,
            long currentFrameRequests,
            Instant now,
            Instant deadline) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(deadline, "deadline");

        if (!sessionActive) {
            return Decision.SESSION_CLOSED;
        }
        if (!expectedTrackIsCurrent) {
            return Decision.TRACK_REPLACED;
        }
        if (!selfInVoiceChannel) {
            return Decision.VOICE_LEFT;
        }
        if (currentFrameRequests > baselineFrameRequests) {
            return Decision.READY;
        }
        if (!now.isBefore(deadline)) {
            return Decision.FRAME_TIMEOUT;
        }
        return Decision.WAIT;
    }

    enum Decision {
        WAIT,
        READY,
        VOICE_LEFT,
        FRAME_TIMEOUT,
        SESSION_CLOSED,
        TRACK_REPLACED
    }
}
