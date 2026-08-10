package ru.flawden.BascovDiscordBot.playback;

import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Privacy-safe, process-local provider health snapshot for diagnostics and resolver policy.
 */
public record PlaybackProviderHealthSnapshot(
        MediaProvider provider,
        PlaybackProviderStatus status,
        long successes,
        long failures,
        long misses,
        long fallbacks,
        int consecutiveFailures,
        Instant cooldownUntil,
        Duration retryAfter,
        String lastFailure) {

    public PlaybackProviderHealthSnapshot {
        provider = Objects.requireNonNull(provider, "provider");
        status = Objects.requireNonNull(status, "status");
        successes = Math.max(0L, successes);
        failures = Math.max(0L, failures);
        misses = Math.max(0L, misses);
        fallbacks = Math.max(0L, fallbacks);
        consecutiveFailures = Math.max(0, consecutiveFailures);
        retryAfter = retryAfter == null || retryAfter.isNegative() ? Duration.ZERO : retryAfter;
        lastFailure = lastFailure == null || lastFailure.isBlank() ? "none" : lastFailure.trim();
    }

    public boolean available() {
        return status != PlaybackProviderStatus.COOLDOWN;
    }
}
