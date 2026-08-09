package ru.flawden.BascovDiscordBot.recommendation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bounded контекст novelty/diversity. Не содержит Discord identity или секреты.
 */
public record RecommendationContext(
        Set<String> knownTrackIdentities,
        Set<String> recentTrackIdentities,
        Set<String> recentArtists) {

    public RecommendationContext {
        knownTrackIdentities = immutable(knownTrackIdentities);
        recentTrackIdentities = immutable(recentTrackIdentities);
        recentArtists = immutable(recentArtists);
    }

    public static RecommendationContext empty() {
        return new RecommendationContext(Set.of(), Set.of(), Set.of());
    }

    private static Set<String> immutable(Set<String> input) {
        return input == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(input));
    }
}
