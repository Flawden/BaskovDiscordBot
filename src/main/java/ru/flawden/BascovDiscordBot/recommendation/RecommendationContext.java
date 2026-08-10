package ru.flawden.BascovDiscordBot.recommendation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bounded context for novelty/diversity + long-term personal, short-term session,
 * collaborative and curated-mix models. Does not contain Discord secrets or provider tokens.
 */
public record RecommendationContext(
        Set<String> knownTrackIdentities,
        Set<String> recentTrackIdentities,
        Set<String> recentArtists,
        PersonalTasteProfile personalTaste,
        CollaborativeArtistSignals collaborativeSignals,
        SessionTasteProfile sessionTaste,
        ContextualBanditProfile banditProfile,
        MixDiversityProfile mixDiversity) {

    public RecommendationContext(
            Set<String> knownTrackIdentities,
            Set<String> recentTrackIdentities,
            Set<String> recentArtists) {
        this(knownTrackIdentities, recentTrackIdentities, recentArtists,
                PersonalTasteProfile.empty(), CollaborativeArtistSignals.empty(), SessionTasteProfile.empty(0L),
                ContextualBanditProfile.empty(), MixDiversityProfile.disabled());
    }

    public RecommendationContext(
            Set<String> knownTrackIdentities,
            Set<String> recentTrackIdentities,
            Set<String> recentArtists,
            PersonalTasteProfile personalTaste) {
        this(knownTrackIdentities, recentTrackIdentities, recentArtists,
                personalTaste, CollaborativeArtistSignals.empty(), SessionTasteProfile.empty(0L),
                ContextualBanditProfile.empty(), MixDiversityProfile.disabled());
    }

    public RecommendationContext(
            Set<String> knownTrackIdentities,
            Set<String> recentTrackIdentities,
            Set<String> recentArtists,
            PersonalTasteProfile personalTaste,
            CollaborativeArtistSignals collaborativeSignals) {
        this(knownTrackIdentities, recentTrackIdentities, recentArtists,
                personalTaste, collaborativeSignals, SessionTasteProfile.empty(0L),
                ContextualBanditProfile.empty(), MixDiversityProfile.disabled());
    }

    public RecommendationContext(
            Set<String> knownTrackIdentities,
            Set<String> recentTrackIdentities,
            Set<String> recentArtists,
            PersonalTasteProfile personalTaste,
            CollaborativeArtistSignals collaborativeSignals,
            SessionTasteProfile sessionTaste) {
        this(knownTrackIdentities, recentTrackIdentities, recentArtists,
                personalTaste, collaborativeSignals, sessionTaste, ContextualBanditProfile.empty(),
                MixDiversityProfile.disabled());
    }

    public RecommendationContext(
            Set<String> knownTrackIdentities,
            Set<String> recentTrackIdentities,
            Set<String> recentArtists,
            PersonalTasteProfile personalTaste,
            CollaborativeArtistSignals collaborativeSignals,
            SessionTasteProfile sessionTaste,
            ContextualBanditProfile banditProfile) {
        this(knownTrackIdentities, recentTrackIdentities, recentArtists,
                personalTaste, collaborativeSignals, sessionTaste, banditProfile,
                MixDiversityProfile.disabled());
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
        mixDiversity = mixDiversity == null ? MixDiversityProfile.disabled() : mixDiversity;
    }

    public RecommendationContext withCollaborativeSignals(CollaborativeArtistSignals signals) {
        return new RecommendationContext(
                knownTrackIdentities,
                recentTrackIdentities,
                recentArtists,
                personalTaste,
                signals,
                sessionTaste,
                banditProfile,
                mixDiversity);
    }

    public RecommendationContext withSessionTaste(SessionTasteProfile profile) {
        return new RecommendationContext(
                knownTrackIdentities,
                recentTrackIdentities,
                recentArtists,
                personalTaste,
                collaborativeSignals,
                profile,
                banditProfile,
                mixDiversity);
    }

    public RecommendationContext withBanditProfile(ContextualBanditProfile profile) {
        return new RecommendationContext(
                knownTrackIdentities,
                recentTrackIdentities,
                recentArtists,
                personalTaste,
                collaborativeSignals,
                sessionTaste,
                profile,
                mixDiversity);
    }

    public RecommendationContext withMixDiversity(MixDiversityProfile profile) {
        return new RecommendationContext(
                knownTrackIdentities,
                recentTrackIdentities,
                recentArtists,
                personalTaste,
                collaborativeSignals,
                sessionTaste,
                banditProfile,
                profile);
    }

    public static RecommendationContext empty() {
        return new RecommendationContext(
                Set.of(), Set.of(), Set.of(),
                PersonalTasteProfile.empty(), CollaborativeArtistSignals.empty(), SessionTasteProfile.empty(0L),
                ContextualBanditProfile.empty(), MixDiversityProfile.disabled());
    }

    private static Set<String> immutable(Set<String> input) {
        return input == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(input));
    }
}
