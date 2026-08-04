package ru.flawden.BascovDiscordBot.lavaplayer;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Чистая policy-функция watchdog без зависимости от JDA threads. */
final class VoiceWatchdogPolicy {

    private VoiceWatchdogPolicy() {
    }

    static Decision evaluate(
            Instant now,
            Instant notBefore,
            Instant missingSince,
            boolean playbackExpected,
            boolean recentFrameRequest,
            Duration disconnectGrace) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(notBefore, "notBefore");
        Objects.requireNonNull(disconnectGrace, "disconnectGrace");

        if (!playbackExpected || now.isBefore(notBefore) || recentFrameRequest) {
            return Decision.HEALTHY;
        }
        if (missingSince == null) {
            return Decision.START_GRACE;
        }
        if (Duration.between(missingSince, now).compareTo(disconnectGrace) < 0) {
            return Decision.WAIT;
        }
        return Decision.FAIL;
    }

    enum Decision {
        HEALTHY,
        START_GRACE,
        WAIT,
        FAIL
    }
}
