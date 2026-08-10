package ru.flawden.BascovDiscordBot.playback;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.config.PlaybackResilienceProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local provider health registry with a bounded consecutive-failure circuit breaker.
 *
 * <p>No state is persisted: restart intentionally resets transport health while durable music
 * identity, feedback and library state stay untouched.</p>
 */
@Component
public class PlaybackProviderHealthRegistry {

    private final PlaybackResilienceProperties properties;
    private final Clock clock;
    private final Map<MediaProvider, MutableHealth> health = new ConcurrentHashMap<>();

    @Autowired
    public PlaybackProviderHealthRegistry(PlaybackResilienceProperties properties) {
        this(properties, Clock.systemUTC());
    }

    PlaybackProviderHealthRegistry(PlaybackResilienceProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean isAvailable(MediaProvider provider) {
        return snapshot(provider).available();
    }

    /**
     * Small penalty for a provider with a recent failure. COOLDOWN providers are filtered before
     * ranking; PROBE intentionally keeps normal priority so the primary provider can recover.
     */
    public int rankingPenalty(MediaProvider provider) {
        return switch (snapshot(provider).status()) {
            case HEALTHY, PROBE -> 0;
            case DEGRADED -> 25;
            case COOLDOWN -> 10_000;
        };
    }

    public void recordSuccess(MediaProvider provider) {
        if (provider == null || provider == MediaProvider.UNKNOWN) {
            return;
        }
        state(provider).success(clock.instant());
    }

    /** Technical provider/load failure; counts toward the circuit-breaker threshold. */
    public void recordFailure(MediaProvider provider, String reason) {
        if (provider == null || provider == MediaProvider.UNKNOWN) {
            return;
        }
        state(provider).failure(
                clock.instant(),
                properties.getFailureThreshold(),
                properties.getCooldown(),
                safeReason(reason));
    }

    /** A valid provider response with no track match is track-specific and must not poison health. */
    public void recordMiss(MediaProvider provider) {
        if (provider == null || provider == MediaProvider.UNKNOWN) {
            return;
        }
        state(provider).miss();
    }

    public void recordFallback(MediaProvider from, MediaProvider to, String reason) {
        if (from == null || from == MediaProvider.UNKNOWN) {
            return;
        }
        state(from).fallback(to, safeReason(reason));
    }

    public PlaybackProviderHealthSnapshot snapshot(MediaProvider provider) {
        MediaProvider safe = provider == null ? MediaProvider.UNKNOWN : provider;
        MutableHealth value = health.get(safe);
        return value == null
                ? emptySnapshot(safe)
                : value.snapshot(safe, clock.instant(), properties.getFailureThreshold());
    }

    public List<PlaybackProviderHealthSnapshot> snapshots() {
        List<MediaProvider> providers = new ArrayList<>(health.keySet());
        for (MediaProvider provider : List.of(MediaProvider.YOUTUBE, MediaProvider.SOUNDCLOUD)) {
            if (!providers.contains(provider)) {
                providers.add(provider);
            }
        }
        return providers.stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(this::snapshot)
                .toList();
    }

    public Duration retryAfter(Collection<MediaProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            return Duration.ZERO;
        }
        return providers.stream()
                .filter(Objects::nonNull)
                .map(this::snapshot)
                .filter(snapshot -> snapshot.status() == PlaybackProviderStatus.COOLDOWN)
                .map(PlaybackProviderHealthSnapshot::retryAfter)
                .filter(delay -> !delay.isZero() && !delay.isNegative())
                .min(Duration::compareTo)
                .orElse(Duration.ZERO);
    }

    private MutableHealth state(MediaProvider provider) {
        return health.computeIfAbsent(provider, ignored -> new MutableHealth());
    }

    private PlaybackProviderHealthSnapshot emptySnapshot(MediaProvider provider) {
        return new PlaybackProviderHealthSnapshot(
                provider,
                PlaybackProviderStatus.HEALTHY,
                0L,
                0L,
                0L,
                0L,
                0,
                null,
                Duration.ZERO,
                "none");
    }

    private static String safeReason(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String oneLine = value.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() <= 220 ? oneLine : oneLine.substring(0, 220).trim();
    }

    private static final class MutableHealth {
        private long successes;
        private long failures;
        private long misses;
        private long fallbacks;
        private int consecutiveFailures;
        private Instant cooldownUntil;
        private String lastFailure = "none";

        synchronized void success(Instant now) {
            successes++;
            consecutiveFailures = 0;
            cooldownUntil = null;
        }

        synchronized void failure(
                Instant now,
                int failureThreshold,
                Duration cooldown,
                String reason) {
            failures++;
            consecutiveFailures++;
            lastFailure = reason;
            if (consecutiveFailures >= failureThreshold) {
                cooldownUntil = now.plus(cooldown);
            }
        }

        synchronized void miss() {
            misses++;
            // A normal "no matches" response proves the provider transport answered.
            // It is track-specific, not an outage, and therefore also closes a probe/degraded state.
            consecutiveFailures = 0;
            cooldownUntil = null;
        }

        synchronized void fallback(MediaProvider to, String reason) {
            fallbacks++;
            if (to != null && to != MediaProvider.UNKNOWN) {
                lastFailure = reason + " -> fallback " + to.name();
            }
        }

        synchronized PlaybackProviderHealthSnapshot snapshot(
                MediaProvider provider,
                Instant now,
                int failureThreshold) {
            PlaybackProviderStatus status;
            Duration retryAfter = Duration.ZERO;
            if (cooldownUntil != null && now.isBefore(cooldownUntil)) {
                status = PlaybackProviderStatus.COOLDOWN;
                retryAfter = Duration.between(now, cooldownUntil);
            } else if (consecutiveFailures >= failureThreshold) {
                status = PlaybackProviderStatus.PROBE;
            } else if (consecutiveFailures > 0) {
                status = PlaybackProviderStatus.DEGRADED;
            } else {
                status = PlaybackProviderStatus.HEALTHY;
            }
            return new PlaybackProviderHealthSnapshot(
                    provider,
                    status,
                    successes,
                    failures,
                    misses,
                    fallbacks,
                    consecutiveFailures,
                    cooldownUntil,
                    retryAfter,
                    lastFailure);
        }
    }
}
