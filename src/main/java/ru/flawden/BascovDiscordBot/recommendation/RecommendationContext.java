package ru.flawden.BascovDiscordBot.recommendation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bounded контекст novelty/diversity + персональная модель. Не содержит Discord secrets.
 */
public record RecommendationContext(
        Set<String> knownTrackIdentities,
        Set<String> recentTrackIdentities,
        Set<String> recentArtists,
        PersonalTasteProfile personalTaste) {

    public RecommendationContext(Set<String> knownTrackIdentities, Set<String> recentTrackIdentities, Set<String> recentArtists) {
        this(knownTrackIdentities, recentTrackIdentities, recentArtists, PersonalTasteProfile.empty());
    }

    public RecommendationContext {
        knownTrackIdentities = immutable(knownTrackIdentities);
        recentTrackIdentities = immutable(recentTrackIdentities);
        recentArtists = immutable(recentArtists);
        personalTaste = personalTaste == null ? PersonalTasteProfile.empty() : personalTaste;
    }

    public static RecommendationContext empty() {
        return new RecommendationContext(Set.of(), Set.of(), Set.of(), PersonalTasteProfile.empty());
    }

    private static Set<String> immutable(Set<String> input) {
        return input == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(input));
    }
}
