package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrematureTrackEndPolicyTest {

    @Test
    void flagsThirtySecondPreviewOfThreeMinuteTrack() {
        AudioTrack track = track(Duration.ofMinutes(3), Duration.ofSeconds(30));

        assertTrue(PrematureTrackEndPolicy.isPremature(track, 30_000L));
        assertTrue(PrematureTrackEndPolicy.diagnostic(track, 30_000L).contains("30000ms of 180000ms"));
    }

    @Test
    void acceptsNaturalCompletionNearAdvertisedDuration() {
        AudioTrack track = track(Duration.ofMinutes(3), Duration.ofSeconds(179));

        assertFalse(PrematureTrackEndPolicy.isPremature(track, 30_000L));
    }

    @Test
    void acceptsGenuinelyShortTracks() {
        AudioTrack track = track(Duration.ofSeconds(30), Duration.ofSeconds(30));

        assertFalse(PrematureTrackEndPolicy.isPremature(track, 30_000L));
    }

    private static AudioTrack track(Duration duration, Duration position) {
        AudioTrack track = mock(AudioTrack.class);
        AudioTrackInfo info = mock(AudioTrackInfo.class);
        when(track.getInfo()).thenReturn(info);
        when(track.getDuration()).thenReturn(duration.toMillis());
        when(track.getPosition()).thenReturn(position.toMillis());
        return track;
    }
}
