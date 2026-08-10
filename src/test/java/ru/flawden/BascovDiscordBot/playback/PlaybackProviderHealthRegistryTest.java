package ru.flawden.BascovDiscordBot.playback;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.config.PlaybackResilienceProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackProviderHealthRegistryTest {

    @Test
    void technicalFailuresOpenCooldownAtConfiguredThreshold() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-10T12:00:00Z"));
        PlaybackProviderHealthRegistry registry = registry(clock, 3, Duration.ofSeconds(90));

        registry.recordFailure(MediaProvider.YOUTUBE, "network-1");
        registry.recordFailure(MediaProvider.YOUTUBE, "network-2");
        assertEquals(PlaybackProviderStatus.DEGRADED, registry.snapshot(MediaProvider.YOUTUBE).status());

        registry.recordFailure(MediaProvider.YOUTUBE, "network-3");
        PlaybackProviderHealthSnapshot snapshot = registry.snapshot(MediaProvider.YOUTUBE);
        assertEquals(PlaybackProviderStatus.COOLDOWN, snapshot.status());
        assertEquals(3, snapshot.consecutiveFailures());
        assertFalse(snapshot.available());
        assertEquals(Duration.ofSeconds(90), snapshot.retryAfter());
    }

    @Test
    void providerBecomesProbeAfterCooldownAndSuccessClosesCircuit() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-10T12:00:00Z"));
        PlaybackProviderHealthRegistry registry = registry(clock, 2, Duration.ofSeconds(30));

        registry.recordFailure(MediaProvider.YOUTUBE, "one");
        registry.recordFailure(MediaProvider.YOUTUBE, "two");
        clock.advance(Duration.ofSeconds(31));

        assertEquals(PlaybackProviderStatus.PROBE, registry.snapshot(MediaProvider.YOUTUBE).status());
        assertTrue(registry.isAvailable(MediaProvider.YOUTUBE));

        registry.recordSuccess(MediaProvider.YOUTUBE);
        PlaybackProviderHealthSnapshot recovered = registry.snapshot(MediaProvider.YOUTUBE);
        assertEquals(PlaybackProviderStatus.HEALTHY, recovered.status());
        assertEquals(0, recovered.consecutiveFailures());
        assertEquals(1L, recovered.successes());
    }

    @Test
    void noMatchDoesNotPoisonProviderHealth() {
        PlaybackProviderHealthRegistry registry = registry(
                new MutableClock(Instant.parse("2026-08-10T12:00:00Z")),
                2,
                Duration.ofSeconds(30));

        registry.recordMiss(MediaProvider.YOUTUBE);
        registry.recordMiss(MediaProvider.YOUTUBE);
        registry.recordMiss(MediaProvider.YOUTUBE);

        PlaybackProviderHealthSnapshot snapshot = registry.snapshot(MediaProvider.YOUTUBE);
        assertEquals(PlaybackProviderStatus.HEALTHY, snapshot.status());
        assertEquals(3L, snapshot.misses());
        assertEquals(0L, snapshot.failures());
    }

    @Test
    void noMatchClosesProbeBecauseProviderAnsweredNormally() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-10T12:00:00Z"));
        PlaybackProviderHealthRegistry registry = registry(clock, 1, Duration.ofSeconds(10));
        registry.recordFailure(MediaProvider.YOUTUBE, "temporary outage");
        clock.advance(Duration.ofSeconds(11));
        assertEquals(PlaybackProviderStatus.PROBE, registry.snapshot(MediaProvider.YOUTUBE).status());

        registry.recordMiss(MediaProvider.YOUTUBE);

        PlaybackProviderHealthSnapshot snapshot = registry.snapshot(MediaProvider.YOUTUBE);
        assertEquals(PlaybackProviderStatus.HEALTHY, snapshot.status());
        assertEquals(0, snapshot.consecutiveFailures());
        assertEquals(1L, snapshot.misses());
        assertEquals(0L, snapshot.successes());
    }

    @Test
    void fallbackCounterIsObservableWithoutChangingFailureThreshold() {
        PlaybackProviderHealthRegistry registry = registry(
                new MutableClock(Instant.parse("2026-08-10T12:00:00Z")),
                3,
                Duration.ofSeconds(90));

        registry.recordFallback(MediaProvider.YOUTUBE, MediaProvider.SOUNDCLOUD, "no matches");

        PlaybackProviderHealthSnapshot snapshot = registry.snapshot(MediaProvider.YOUTUBE);
        assertEquals(1L, snapshot.fallbacks());
        assertEquals(PlaybackProviderStatus.HEALTHY, snapshot.status());
    }

    @Test
    void retryAfterReturnsEarliestCoolingProvider() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-10T12:00:00Z"));
        PlaybackProviderHealthRegistry registry = registry(clock, 1, Duration.ofSeconds(90));

        registry.recordFailure(MediaProvider.YOUTUBE, "yt down");
        clock.advance(Duration.ofSeconds(30));
        registry.recordFailure(MediaProvider.SOUNDCLOUD, "sc down");

        Duration retry = registry.retryAfter(List.of(MediaProvider.YOUTUBE, MediaProvider.SOUNDCLOUD));
        assertEquals(Duration.ofSeconds(60), retry);
    }

    private static PlaybackProviderHealthRegistry registry(
            Clock clock,
            int threshold,
            Duration cooldown) {
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
