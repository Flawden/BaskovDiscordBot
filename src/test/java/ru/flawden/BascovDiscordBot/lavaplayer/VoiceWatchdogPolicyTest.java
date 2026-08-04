package ru.flawden.BascovDiscordBot.lavaplayer;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoiceWatchdogPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-04T09:00:00Z");

    @Test
    void startupGracePreventsImmediateDisconnectAfterSuccessfulJoin() {
        assertEquals(
                VoiceWatchdogPolicy.Decision.HEALTHY,
                VoiceWatchdogPolicy.evaluate(
                        NOW,
                        NOW.plusSeconds(15),
                        null,
                        true,
                        false,
                        Duration.ofSeconds(5)));
    }

    @Test
    void recentFrameDemandKeepsPlaybackHealthyAfterStartupGrace() {
        assertEquals(
                VoiceWatchdogPolicy.Decision.HEALTHY,
                VoiceWatchdogPolicy.evaluate(
                        NOW,
                        NOW.minusSeconds(1),
                        NOW.minusSeconds(20),
                        true,
                        true,
                        Duration.ofSeconds(5)));
    }

    @Test
    void missingFrameDemandUsesGraceBeforeFailing() {
        assertEquals(
                VoiceWatchdogPolicy.Decision.START_GRACE,
                VoiceWatchdogPolicy.evaluate(
                        NOW, NOW.minusSeconds(1), null, true, false, Duration.ofSeconds(5)));
        assertEquals(
                VoiceWatchdogPolicy.Decision.WAIT,
                VoiceWatchdogPolicy.evaluate(
                        NOW, NOW.minusSeconds(10), NOW.minusSeconds(4), true, false, Duration.ofSeconds(5)));
        assertEquals(
                VoiceWatchdogPolicy.Decision.FAIL,
                VoiceWatchdogPolicy.evaluate(
                        NOW, NOW.minusSeconds(10), NOW.minusSeconds(5), true, false, Duration.ofSeconds(5)));
    }
}
