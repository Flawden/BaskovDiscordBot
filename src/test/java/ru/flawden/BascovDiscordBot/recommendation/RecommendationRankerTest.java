package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationRankerTest {

    @Test
    void discoveryRejectsKnownTrackAndPicksNovelCandidate() {
        RecommendationCandidate known = new RecommendationCandidate("Known Artist", "Known Song", 0.99, "Last.fm", "known");
        RecommendationCandidate fresh = new RecommendationCandidate("Fresh Artist", "Fresh Song", 0.73, "Last.fm", "fresh");
        RecommendationContext context = new RecommendationContext(
                Set.of(known.identity()),
                Set.of(),
                Set.of());

        RecommendationRanker.ScoredCandidate selected = RecommendationRanker.best(
                        List.of(known, fresh),
                        RadioStrategy.DISCOVERY,
                        context)
                .orElseThrow();

        assertEquals(fresh.identity(), selected.candidate().identity());
        assertTrue(selected.score() > 0.0d);
    }

    @Test
    void similarCanReuseKnownTrackButAppliesPenalty() {
        RecommendationCandidate known = new RecommendationCandidate("Artist A", "Song A", 0.90, "Last.fm", "known");
        RecommendationCandidate fresh = new RecommendationCandidate("Artist B", "Song B", 0.84, "Last.fm", "fresh");
        RecommendationContext context = new RecommendationContext(Set.of(known.identity()), Set.of(), Set.of());

        RecommendationRanker.ScoredCandidate selected = RecommendationRanker.best(
                        List.of(known, fresh),
                        RadioStrategy.SIMILAR,
                        context)
                .orElseThrow();

        assertEquals(fresh.identity(), selected.candidate().identity());
    }

    @Test
    void recentTrackIsRejectedInEveryStrategy() {
        RecommendationCandidate recent = new RecommendationCandidate("Artist", "Recent", 1.0, "Last.fm", "recent");
        RecommendationCandidate other = new RecommendationCandidate("Other", "Track", 0.5, "Last.fm", "other");
        RecommendationContext context = new RecommendationContext(Set.of(), Set.of(recent.identity()), Set.of());

        assertEquals(other.identity(), RecommendationRanker.best(
                        List.of(recent, other),
                        RadioStrategy.SIMILAR,
                        context)
                .orElseThrow()
                .candidate()
                .identity());
    }

    @Test
    void recentArtistGetsDiversityPenalty() {
        RecommendationCandidate repeatedArtist = new RecommendationCandidate("Repeat Artist", "New Song", 0.80, "Last.fm", "repeat");
        RecommendationCandidate differentArtist = new RecommendationCandidate("Different Artist", "New Song", 0.77, "Last.fm", "different");
        RecommendationContext context = new RecommendationContext(
                Set.of(),
                Set.of(),
                Set.of(RecommendationIdentity.normalizeArtist("Repeat Artist")));

        assertEquals(differentArtist.identity(), RecommendationRanker.best(
                        List.of(repeatedArtist, differentArtist),
                        RadioStrategy.DISCOVERY,
                        context)
                .orElseThrow()
                .candidate()
                .identity());
    }
    @Test
    void vectorSimilarityCanBreakNearTieWithoutExactArtistOrTagAffinity() {
        String likedIdentity = RecommendationIdentity.of("Liked Artist", "Neon Dreams");
        PersonalTasteProfile profile = new PersonalTasteProfile(
                1,
                8,
                0,
                Map.of(likedIdentity, 8.0d),
                Map.of(),
                Map.of());
        RecommendationCandidate rawWinner = new RecommendationCandidate(
                "Unknown Artist", "Stone Cold", 0.800d, "Last.fm", "raw");
        RecommendationCandidate vectorWinner = new RecommendationCandidate(
                "Other Artist", "Neon Nights", 0.795d, "Last.fm", "vector");

        RecommendationRanker.ScoredCandidate selected = RecommendationRanker.best(
                        List.of(rawWinner, vectorWinner),
                        RadioStrategy.SIMILAR,
                        new RecommendationContext(Set.of(), Set.of(), Set.of(), profile))
                .orElseThrow();

        assertEquals(vectorWinner.identity(), selected.candidate().identity());
        assertTrue(selected.vectorSimilarity() > 0.0d);
        assertTrue(selected.vectorContribution() > 0.0d);
    }

    @Test
    void vectorScoreCannotResurrectKnownTrackInDiscovery() {
        RecommendationCandidate known = new RecommendationCandidate(
                "Loved Artist", "Loved Song", 1.0d, "Last.fm", "known", Set.of("rock"));
        RecommendationCandidate fresh = new RecommendationCandidate(
                "Other Artist", "Fresh Song", 0.40d, "Last.fm", "fresh", Set.of("jazz"));
        PersonalTasteProfile profile = new PersonalTasteProfile(
                1,
                8,
                0,
                Map.of(known.identity(), 12.0d),
                Map.of(RecommendationIdentity.normalizeArtist(known.artist()), 12.0d),
                Map.of("rock", 12.0d));

        RecommendationRanker.ScoredCandidate selected = RecommendationRanker.best(
                        List.of(known, fresh),
                        RadioStrategy.DISCOVERY,
                        new RecommendationContext(Set.of(known.identity()), Set.of(), Set.of(), profile))
                .orElseThrow();

        assertEquals(fresh.identity(), selected.candidate().identity());
    }

    @Test
    void collaborativeArtistSignalCanBreakNearTie() {
        RecommendationCandidate rawWinner = new RecommendationCandidate(
                "Unknown Artist", "Track A", 0.820d, "Last.fm", "raw");
        RecommendationCandidate collaborativeWinner = new RecommendationCandidate(
                "Jimmy Eat World", "Track B", 0.805d, "Last.fm", "collab");
        RecommendationContext context = new RecommendationContext(
                Set.of(), Set.of(), Set.of(), PersonalTasteProfile.empty(),
                new CollaborativeArtistSignals("ListenBrainz", Map.of("Jimmy Eat World", 1.0d)));

        RecommendationRanker.ScoredCandidate selected = RecommendationRanker.best(
                        List.of(rawWinner, collaborativeWinner),
                        RadioStrategy.SIMILAR,
                        context)
                .orElseThrow();

        assertEquals(collaborativeWinner.identity(), selected.candidate().identity());
        assertTrue(selected.collaborativeContribution() > 0.0d);
        assertEquals("ListenBrainz", selected.collaborativeSource());
    }

    @Test
    void collaborativeSignalCannotResurrectKnownTrackInDiscovery() {
        RecommendationCandidate known = new RecommendationCandidate(
                "Loved Artist", "Known", 1.0d, "Last.fm", "known");
        RecommendationCandidate fresh = new RecommendationCandidate(
                "Fresh Artist", "Fresh", 0.40d, "Last.fm", "fresh");
        RecommendationContext context = new RecommendationContext(
                Set.of(known.identity()), Set.of(), Set.of(), PersonalTasteProfile.empty(),
                new CollaborativeArtistSignals("ListenBrainz", Map.of("Loved Artist", 1.0d)));

        RecommendationRanker.ScoredCandidate selected = RecommendationRanker.best(
                        List.of(known, fresh), RadioStrategy.DISCOVERY, context)
                .orElseThrow();

        assertEquals(fresh.identity(), selected.candidate().identity());
    }

    @Test
    void positiveSessionAffinityCanBreakNearTieAgainstLongTermNeutralCandidate() {
        RecommendationCandidate rawWinner = new RecommendationCandidate(
                "Other Artist", "Track A", 0.825d, "Last.fm", "raw", Set.of("other"));
        RecommendationCandidate sessionWinner = new RecommendationCandidate(
                "Today Artist", "Track B", 0.805d, "Last.fm", "session", Set.of("pop punk"));
        SessionTasteProfile session = new SessionTasteProfile(
                1_800_000_000_000L,
                2,
                4,
                0,
                1.0d,
                Map.of(),
                Map.of(RecommendationIdentity.normalizeArtist("Today Artist"), 8.0d),
                Map.of("pop punk", 6.0d));
        RecommendationContext context = new RecommendationContext(
                Set.of(), Set.of(), Set.of(), PersonalTasteProfile.empty(),
                CollaborativeArtistSignals.empty(), session);

        RecommendationRanker.ScoredCandidate selected = RecommendationRanker.best(
                        List.of(rawWinner, sessionWinner), RadioStrategy.SIMILAR, context)
                .orElseThrow();

        assertEquals(sessionWinner.identity(), selected.candidate().identity());
        assertTrue(selected.sessionContribution() > 0.0d);
    }

    @Test
    void negativeSessionAffinitySuppressesCandidateThatWouldOtherwiseWin() {
        RecommendationCandidate sessionDisliked = new RecommendationCandidate(
                "Bad Tonight", "Track A", 0.92d, "Last.fm", "bad");
        RecommendationCandidate fresh = new RecommendationCandidate(
                "Fresh Artist", "Track B", 0.86d, "Last.fm", "fresh");
        SessionTasteProfile session = new SessionTasteProfile(
                1_800_000_000_000L,
                2,
                0,
                4,
                -1.0d,
                Map.of(sessionDisliked.identity(), -10.0d),
                Map.of(RecommendationIdentity.normalizeArtist("Bad Tonight"), -8.0d),
                Map.of());
        RecommendationContext context = new RecommendationContext(
                Set.of(), Set.of(), Set.of(), PersonalTasteProfile.empty(),
                CollaborativeArtistSignals.empty(), session);

        assertEquals(fresh.identity(), RecommendationRanker.best(
                        List.of(sessionDisliked, fresh), RadioStrategy.SIMILAR, context)
                .orElseThrow()
                .candidate()
                .identity());
    }

    @Test
    void sessionAffinityCannotResurrectKnownTrackInDiscovery() {
        RecommendationCandidate known = new RecommendationCandidate(
                "Loved Tonight", "Known", 1.0d, "Last.fm", "known");
        RecommendationCandidate fresh = new RecommendationCandidate(
                "Fresh Artist", "Fresh", 0.40d, "Last.fm", "fresh");
        SessionTasteProfile session = new SessionTasteProfile(
                1_800_000_000_000L,
                3,
                6,
                0,
                1.0d,
                Map.of(known.identity(), 20.0d),
                Map.of(RecommendationIdentity.normalizeArtist(known.artist()), 20.0d),
                Map.of());
        RecommendationContext context = new RecommendationContext(
                Set.of(known.identity()), Set.of(), Set.of(), PersonalTasteProfile.empty(),
                CollaborativeArtistSignals.empty(), session);

        RecommendationRanker.ScoredCandidate selected = RecommendationRanker.best(
                        List.of(known, fresh), RadioStrategy.DISCOVERY, context)
                .orElseThrow();

        assertEquals(fresh.identity(), selected.candidate().identity());
    }

    @Test
    void learnedBanditCanBreakNearTieTowardSuccessfulArm() {
        RecommendationCandidate safe = new RecommendationCandidate(
                "Safe Artist", "Track A", 0.825d, "Last.fm", "safe");
        RecommendationCandidate balanced = new RecommendationCandidate(
                "Balanced Artist", "Track B", 0.815d, "Last.fm", "balanced");
        java.util.List<RecommendationFeedbackEntry> history = new java.util.ArrayList<>();
        for (int index = 0; index < 8; index++) {
            history.add(FileRecommendationFeedbackRepository.pending(42L, 77L, "Seed", "Seed",
                    "Balanced", "Good", RecommendationIdentity.of("Balanced", "Good"),
                    RadioStrategy.SIMILAR, "Last.fm", 0.72d)
                    .withOutcome(RecommendationOutcome.FAVORITED, System.currentTimeMillis(), 1.0d));
            history.add(FileRecommendationFeedbackRepository.pending(42L, 77L, "Seed", "Seed",
                    "Safe", "Bad", RecommendationIdentity.of("Safe", "Bad"),
                    RadioStrategy.SIMILAR, "Last.fm", 0.90d)
                    .withOutcome(RecommendationOutcome.QUICK_SKIPPED, System.currentTimeMillis(), 0.05d));
        }
        RecommendationContext context = new RecommendationContext(
                Set.of(), Set.of(), Set.of(), PersonalTasteProfile.empty(),
                CollaborativeArtistSignals.empty(), SessionTasteProfile.empty(0L),
                ContextualBanditModel.build(history));

        RecommendationRanker.ScoredCandidate selected = RecommendationRanker.best(
                        List.of(safe, balanced), RadioStrategy.SIMILAR, context)
                .orElseThrow();

        assertEquals(balanced.identity(), selected.candidate().identity());
        assertTrue(selected.banditContribution() > 0.0d);
    }

    @Test
    void banditCannotResurrectKnownTrackInDiscovery() {
        RecommendationCandidate known = new RecommendationCandidate(
                "Known Artist", "Known", 0.45d, "Last.fm", "known");
        RecommendationCandidate fresh = new RecommendationCandidate(
                "Fresh Artist", "Fresh", 0.35d, "Last.fm", "fresh");
        java.util.List<RecommendationFeedbackEntry> history = new java.util.ArrayList<>();
        for (int index = 0; index < 12; index++) {
            history.add(FileRecommendationFeedbackRepository.pending(42L, 77L, "Seed", "Seed",
                    "Bold", "Good", RecommendationIdentity.of("Bold", "Good"),
                    RadioStrategy.DISCOVERY, "Last.fm", 0.45d)
                    .withOutcome(RecommendationOutcome.FAVORITED, System.currentTimeMillis(), 1.0d));
        }
        RecommendationContext context = new RecommendationContext(
                Set.of(known.identity()), Set.of(), Set.of(), PersonalTasteProfile.empty(),
                CollaborativeArtistSignals.empty(), SessionTasteProfile.empty(0L),
                ContextualBanditModel.build(history));

        assertEquals(fresh.identity(), RecommendationRanker.best(
                        List.of(known, fresh), RadioStrategy.DISCOVERY, context)
                .orElseThrow()
                .candidate()
                .identity());
    }


    @Test
    void curatedMixRejectsImmediateArtistRepeatBeforeHybridScoring() {
        RecommendationCandidate repeated = new RecommendationCandidate(
                "Repeat Artist", "Track A", 0.99d, "Last.fm", "repeat", Set.of("rock"));
        RecommendationCandidate fresh = new RecommendationCandidate(
                "Fresh Artist", "Track B", 0.70d, "Last.fm", "fresh", Set.of("rock"));
        MixDiversityProfile diversity = new MixDiversityProfile(
                true,
                "",
                List.of("repeat artist", "other artist"),
                List.of(Set.of("rock")));
        RecommendationContext context = new RecommendationContext(
                Set.of(), Set.of(), Set.of(), PersonalTasteProfile.empty(),
                CollaborativeArtistSignals.empty(), SessionTasteProfile.empty(0L),
                ContextualBanditProfile.empty(), diversity);

        RecommendationRanker.ScoredCandidate selected = RecommendationRanker.best(
                        List.of(repeated, fresh), RadioStrategy.SIMILAR, context)
                .orElseThrow();

        assertEquals(fresh.identity(), selected.candidate().identity());
    }

    @Test
    void themeFocusCanBreakNearTieWithoutOverridingSafety() {
        RecommendationCandidate raw = new RecommendationCandidate(
                "Artist A", "Track A", 0.82d, "Last.fm", "raw", Set.of("alternative rock"));
        RecommendationCandidate themed = new RecommendationCandidate(
                "Artist B", "Track B", 0.79d, "Last.fm", "theme", Set.of("pop punk"));
        MixDiversityProfile diversity = new MixDiversityProfile(
                true,
                "pop punk",
                List.of("artist c"),
                List.of(Set.of("alternative rock")));
        RecommendationContext context = new RecommendationContext(
                Set.of(), Set.of(), Set.of(), PersonalTasteProfile.empty(),
                CollaborativeArtistSignals.empty(), SessionTasteProfile.empty(0L),
                ContextualBanditProfile.empty(), diversity);

        RecommendationRanker.ScoredCandidate selected = RecommendationRanker.best(
                        List.of(raw, themed), RadioStrategy.SIMILAR, context)
                .orElseThrow();

        assertEquals(themed.identity(), selected.candidate().identity());
        assertTrue(selected.themeAffinity() > 0.0d);
        assertTrue(selected.mixDiversityContribution() > 0.0d);
    }

    @Test
    void themeAndDiversityCannotResurrectKnownTrackInDiscovery() {
        RecommendationCandidate knownThemed = new RecommendationCandidate(
                "Loved Artist", "Known", 1.0d, "Last.fm", "known", Set.of("pop punk"));
        RecommendationCandidate fresh = new RecommendationCandidate(
                "Fresh Artist", "Fresh", 0.40d, "Last.fm", "fresh", Set.of("jazz"));
        MixDiversityProfile diversity = new MixDiversityProfile(
                true,
                "pop punk",
                List.of(),
                List.of());
        RecommendationContext context = new RecommendationContext(
                Set.of(knownThemed.identity()), Set.of(), Set.of(), PersonalTasteProfile.empty(),
                CollaborativeArtistSignals.empty(), SessionTasteProfile.empty(0L),
                ContextualBanditProfile.empty(), diversity);

        RecommendationRanker.ScoredCandidate selected = RecommendationRanker.best(
                        List.of(knownThemed, fresh), RadioStrategy.DISCOVERY, context)
                .orElseThrow();

        assertEquals(fresh.identity(), selected.candidate().identity());
    }

}
