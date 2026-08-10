package ru.flawden.BascovDiscordBot.recommendation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bounded context for novelty/diversity + long-term personal, short-term session
 * and collaborative models. Does not contain Discord secrets or provider tokens.
 */
public record RecommendationContext(
        Set<String> knownTrackIdentities,
        Set<String> recentTrackIdentities,
        Set<String> recentArtists,
        PersonalTasteProfile personalTaste,
        CollaborativeArtistSignals collaborativeSignals,
        SessionTasteProfile sessionTaste,
        ContextualBanditProfile banditProfile) {

    public RecommendationContext(
            Set<String> knownTrackIdentities,
            Set<String> recentTrackIdentities,
            Set<String> recentArtists) {
        this(knownTrackIdentities, recentTrackIdentities, recentArtists,
                PersonalTasteProfile.empty(), CollaborativeArtistSignals.empty(), SessionTasteProfile.empty(0L),
                ContextualBanditProfile.empty());
    }

    public RecommendationContext(
            Set<String> knownTrackIdentities,
            Set<String> recentTrackIdentities,
            Set<String> recentArtists,
            PersonalTasteProfile personalTaste) {
        this(knownTrackIdentities, recentTrackIdentities, recentArtists,
                personalTaste, CollaborativeArtistSignals.empty(), SessionTasteProfile.empty(0L),
                ContextualBanditProfile.empty());
    }

    public RecommendationContext(
            Set<String> knownTrackIdentities,
            Set<String> recentTrackIdentities,
            Set<String> recentArtists,
            PersonalTasteProfile personalTaste,
            CollaborativeArtistSignals collaborativeSignals) {
        this(knownTrackIdentities, recentTrackIdentities, recentArtists,
                personalTaste, collaborativeSignals, SessionTasteProfile.empty(0L),
                ContextualBanditProfile.empty());
    }

    public RecommendationContext(
            Set<String> knownTrackIdentities,
            Set<String> recentTrackIdentities,
            Set<String> recentArtists,
            PersonalTasteProfile personalTaste,
            CollaborativeArtistSignals collaborativeSignals,
            SessionTasteProfile sessionTaste) {
        this(knownTrackIdentities, recentTrackIdentities, recentArtists,
                personalTaste, collaborativeSignals, sessionTaste, ContextualBanditProfile.empty());
    }

    public RecommendationContext {
        knownTrackIdentities = immutable(knownTrackIdentities);
        recentTrackIdentities = immutable(recentTrackIdentities);
        recentArtists = immutable(recentArtists);
        personalTaste = personalTaste == null ? PersonalTasteProfile.empty() : personalTaste;
        collaborativeSignals = collaborativeSignals == null
                ? CollaborativeArtistSignals.empty()
                : collaborativeSignals;
        sessionTaste = sessionTaste == null ? SessionTasteProfile.empty(0L) : sessionTaste;
        banditProfile = banditProfile == null ? ContextualBanditProfile.empty() : banditProfile;
    }

    public RecommendationContext withCollaborativeSignals(CollaborativeArtistSignals signals) {
        return new RecommendationContext(
                knownTrackIdentities,
                recentTrackIdentities,
                recentArtists,
                personalTaste,
                signals,
                sessionTaste,
                banditProfile);
    }

    public RecommendationContext withSessionTaste(SessionTasteProfile profile) {
        return new RecommendationContext(
                knownTrackIdentities,
                recentTrackIdentities,
                recentArtists,
                personalTaste,
                collaborativeSignals,
                profile,
                banditProfile);
    }

    public RecommendationContext withBanditProfile(ContextualBanditProfile profile) {
        return new RecommendationContext(
                knownTrackIdentities,
                recentTrackIdentities,
                recentArtists,
                personalTaste,
                collaborativeSignals,
                sessionTaste,
                profile);
    }

    public static RecommendationContext empty() {
        return new RecommendationContext(
                Set.of(), Set.of(), Set.of(),
                PersonalTasteProfile.empty(), CollaborativeArtistSignals.empty(), SessionTasteProfile.empty(0L),
                ContextualBanditProfile.empty());
    }

    private static Set<String> immutable(Set<String> input) {
        return input == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(input));
    }
}
