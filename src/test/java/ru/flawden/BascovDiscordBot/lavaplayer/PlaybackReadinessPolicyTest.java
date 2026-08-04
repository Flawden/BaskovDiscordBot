package ru.flawden.BascovDiscordBot.lavaplayer;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaybackReadinessPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-04T14:00:00Z");
    private static final Instant DEADLINE = NOW.plusSeconds(10);

    @Test
    void confirmsPlaybackOnlyAfterNewFramePolling() {
        assertEquals(PlaybackReadinessPolicy.Decision.READY,
                PlaybackReadinessPolicy.evaluate(
                        true, true, true, 4L, 5L, NOW, DEADLINE));
    }

    @Test
    void rejectsVoiceLeaveBeforeFirstFrame() {
        assertEquals(PlaybackReadinessPolicy.Decision.VOICE_LEFT,
                PlaybackReadinessPolicy.evaluate(
                        true, true, false, 0L, 0L, NOW, DEADLINE));
    }

    @Test
    void waitsWhileHandshakeIsStillInsideDeadline() {
        assertEquals(PlaybackReadinessPolicy.Decision.WAIT,
                PlaybackReadinessPolicy.evaluate(
                        true, true, true, 0L, 0L, NOW, DEADLINE));
    }

    @Test
    void timesOutWhenDiscordNeverPollsFrames() {
        assertEquals(PlaybackReadinessPolicy.Decision.FRAME_TIMEOUT,
                PlaybackReadinessPolicy.evaluate(
                        true, true, true, 0L, 0L, DEADLINE, DEADLINE));
    }

    @Test
    void rejectsClosedOrReplacedPlayback() {
        assertEquals(PlaybackReadinessPolicy.Decision.SESSION_CLOSED,
                PlaybackReadinessPolicy.evaluate(
                        false, false, false, 0L, 0L, NOW, DEADLINE));
        assertEquals(PlaybackReadinessPolicy.Decision.TRACK_REPLACED,
                PlaybackReadinessPolicy.evaluate(
                        true, false, true, 0L, 0L, NOW, DEADLINE));
    }
}
