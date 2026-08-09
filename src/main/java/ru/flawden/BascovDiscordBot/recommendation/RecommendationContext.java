package ru.flawden.BascovDiscordBot.recommendation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bounded context for novelty/diversity + personal and collaborative models.
 * Does not contain Discord secrets or provider tokens.
 */
public record RecommendationContext(
        Set<String> knownTrackIdentities,
        Set<String> recentTrackIdentities,
        Set<String> recentArtists,
        PersonalTasteProfile personalTaste,
        CollaborativeArtistSignals collaborativeSignals) {

    public RecommendationContext(
            Set<String> knownTrackIdentities,
            Set<String> recentTrackIdentities,
            Set<String> recentArtists) {
        this(knownTrackIdentities, recentTrackIdentities, recentArtists,
                PersonalTasteProfile.empty(), CollaborativeArtistSignals.empty());
    }

    public RecommendationContext(
            Set<String> knownTrackIdentities,
            Set<String> recentTrackIdentities,
            Set<String> recentArtists,
            PersonalTasteProfile personalTaste) {
        this(knownTrackIdentities, recentTrackIdentities, recentArtists,
                personalTaste, CollaborativeArtistSignals.empty());
    }

    public RecommendationContext {
        knownTrackIdentities = immutable(knownTrackIdentities);
        recentTrackIdentities = immutable(recentTrackIdentities);
        recentArtists = immutable(recentArtists);
        personalTaste = personalTaste == null ? PersonalTasteProfile.empty() : personalTaste;
        collaborativeSignals = collaborativeSignals == null
                ? CollaborativeArtistSignals.empty()
                : collaborativeSignals;
    }

    public RecommendationContext withCollaborativeSignals(CollaborativeArtistSignals signals) {
        return new RecommendationContext(
                knownTrackIdentities,
                recentTrackIdentities,
                recentArtists,
                personalTaste,
                signals);
    }

    public static RecommendationContext empty() {
        return new RecommendationContext(
                Set.of(), Set.of(), Set.of(),
                PersonalTasteProfile.empty(), CollaborativeArtistSignals.empty());
    }

    private static Set<String> immutable(Set<String> input) {
        return input == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(input));
    }
}
