package ru.flawden.BascovDiscordBot.session;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredMusicSessionTest {

    @Test
    void clampsResumePositionBeforeAdvertisedTrackEnd() {
        StoredSessionTrack track = new StoredSessionTrack(track(5_000L), 9_000L);
        assertEquals(4_000L, track.safeResumePositionMillis());
    }

    @Test
    void validatesContentAndExpiry() {
        long capturedAt = Instant.parse("2026-08-06T00:00:00Z").toEpochMilli();
        StoredMusicSession session = new StoredMusicSession(
                10L,
                20L,
                capturedAt,
                false,
                100,
                RepeatMode.QUEUE,
                new StoredSessionTrack(track(180_000L), 30_000L),
                List.of(new StoredSessionTrack(track(200_000L), 0L)),
                List.of(new StoredSessionTrack(track(210_000L), 0L)));

        assertEquals(2, session.trackCount());
        assertEquals(3, session.recoveryTrackCount());
        assertFalse(session.expired(
                Instant.parse("2026-08-06T05:59:59Z"),
                Duration.ofHours(6)));
        assertTrue(session.expired(
                Instant.parse("2026-08-06T06:00:01Z"),
                Duration.ofHours(6)));
        assertThrows(IllegalArgumentException.class, () -> new StoredMusicSession(
                10L, 20L, capturedAt, false, 100, RepeatMode.OFF, null, List.of(), List.of()));
    }

    private static StoredTrack track(long duration) {
        return new StoredTrack(
                "Track",
                "Artist",
                "https://www.youtube.com/watch?v=checkpoint",
                "checkpoint",
                MediaProvider.YOUTUBE,
                duration,
                42L,
                "Requester",
                1_700_000_000_000L);
    }
}
