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

}
