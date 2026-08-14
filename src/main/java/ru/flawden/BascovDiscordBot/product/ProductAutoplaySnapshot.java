package ru.flawden.BascovDiscordBot.product;

import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.recommendation.RecommendationCandidate;

import java.util.Objects;

/** One provider-neutral continuation decision for external Baskov clients. */
public record ProductAutoplaySnapshot(
        long guildId,
        long userId,
        TrackIdentity seed,
        RecommendationCandidate next,
        boolean available,
        boolean fallback,
        String provider,
        String reason) {

    public ProductAutoplaySnapshot {
        if (guildId <= 0L || userId <= 0L) {
            throw new IllegalArgumentException("guildId and userId must be positive");
        }
        seed = Objects.requireNonNull(seed, "seed");
        provider = provider == null || provider.isBlank() ? "none" : provider.trim();
        reason = reason == null || reason.isBlank() ? "No continuation candidate" : reason.trim();
        if (!available) {
            next = null;
        }
    }
}
