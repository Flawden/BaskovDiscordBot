package ru.flawden.BascovDiscordBot.playback;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.config.PlaybackResilienceProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackResolverHealthTest {

    @Test
    void coolingYoutubeMakesSoundCloudPrimaryForNewResolution() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-10T12:00:00Z"));
        PlaybackProviderHealthRegistry health = health(clock, 1, Duration.ofSeconds(90));
        PlaybackResolver resolver = resolver(health);
        health.recordFailure(MediaProvider.YOUTUBE, "youtube transport down");

        PlaybackResolution resolution = resolver.resolve(
                TrackIdentity.of("Sum 41", "Fat Lip"),
                PlaybackClientCapabilities.discord());

        assertEquals(MediaProvider.SOUNDCLOUD, resolution.primary().orElseThrow().provider());
        assertEquals(1, resolution.candidates().size());
    }

    @Test
    void providerReentersAsPrimaryProbeAfterCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-10T12:00:00Z"));
        PlaybackProviderHealthRegistry health = health(clock, 1, Duration.ofSeconds(30));
        PlaybackResolver resolver = resolver(health);
        health.recordFailure(MediaProvider.YOUTUBE, "youtube transport down");
        clock.advance(Duration.ofSeconds(31));

        PlaybackResolution resolution = resolver.resolve(
                TrackIdentity.of("Sum 41", "Fat Lip"),
                PlaybackClientCapabilities.discord());

        assertEquals(MediaProvider.YOUTUBE, resolution.primary().orElseThrow().provider());
        assertEquals(PlaybackProviderStatus.PROBE, health.snapshot(MediaProvider.YOUTUBE).status());
    }

    @Test
    void allProvidersCoolingReturnsRetryDelayInsteadOfInventingTransport() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-10T12:00:00Z"));
        PlaybackProviderHealthRegistry health = health(clock, 1, Duration.ofSeconds(45));
        PlaybackResolver resolver = resolver(health);
        health.recordFailure(MediaProvider.YOUTUBE, "youtube down");
        health.recordFailure(MediaProvider.SOUNDCLOUD, "soundcloud down");

        PlaybackResolution resolution = resolver.resolve(
                TrackIdentity.of("Sum 41", "Fat Lip"),
                PlaybackClientCapabilities.discord());

        assertTrue(resolution.candidates().isEmpty());
        assertTrue(resolution.waitingForProviderRecovery());
        assertEquals(Duration.ofSeconds(45), resolution.retryAfter());
    }

    @Test
    void successfulProbeRestoresYoutubePrimary() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-10T12:00:00Z"));
        PlaybackProviderHealthRegistry health = health(clock, 1, Duration.ofSeconds(10));
        PlaybackResolver resolver = resolver(health);
        health.recordFailure(MediaProvider.YOUTUBE, "youtube down");
        clock.advance(Duration.ofSeconds(11));
        PlaybackSourceReference probe = resolver.resolve(
                TrackIdentity.of("Sum 41", "Fat Lip"),
                PlaybackClientCapabilities.discord()).primary().orElseThrow();

        resolver.recordSuccess(probe);

        assertEquals(PlaybackProviderStatus.HEALTHY, health.snapshot(MediaProvider.YOUTUBE).status());
        assertEquals(MediaProvider.YOUTUBE, resolver.resolve(
                TrackIdentity.of("Green Day", "Holiday"),
                PlaybackClientCapabilities.discord()).primary().orElseThrow().provider());
    }

    private static PlaybackResolver resolver(PlaybackProviderHealthRegistry health) {
        return new PlaybackResolver(
                List.of(new SoundCloudPlaybackSourceProvider(), new YoutubePlaybackSourceProvider()),
                health);
    }

    private static PlaybackProviderHealthRegistry health(Clock clock, int threshold, Duration cooldown) {
        PlaybackResilienceProperties properties = new PlaybackResilienceProperties();
        properties.setFailureThreshold(threshold);
        properties.setCooldown(cooldown);
        return new PlaybackProviderHealthRegistry(properties, clock);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
