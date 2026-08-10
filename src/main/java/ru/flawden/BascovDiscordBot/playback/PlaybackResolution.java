package ru.flawden.BascovDiscordBot.playback;

import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ordered provider candidates for one logical track and one playback client.
 */
public record PlaybackResolution(
        TrackIdentity track,
        PlaybackClient client,
        List<PlaybackSourceReference> candidates,
        Duration retryAfter) {

    public PlaybackResolution {
        track = Objects.requireNonNull(track, "track");
        client = Objects.requireNonNullElse(client, PlaybackClient.UNKNOWN);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        retryAfter = retryAfter == null || retryAfter.isNegative() ? Duration.ZERO : retryAfter;
    }

    public PlaybackResolution(
            TrackIdentity track,
            PlaybackClient client,
            List<PlaybackSourceReference> candidates) {
        this(track, client, candidates, Duration.ZERO);
    }

    public Optional<PlaybackSourceReference> primary() {
        return candidates.stream().findFirst();
    }

    public boolean resolved() {
        return !candidates.isEmpty();
    }

    public boolean waitingForProviderRecovery() {
        return candidates.isEmpty() && !retryAfter.isZero();
    }
}
