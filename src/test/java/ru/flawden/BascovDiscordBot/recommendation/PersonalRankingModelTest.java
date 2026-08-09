package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalRankingModelTest {

    @Test
    void buildsTrackArtistAndTagAffinityFromFeedback() {
        RecommendationFeedbackEntry liked = entry(
                "1", "Artist A", "Song A", Set.of("alternative", "rock"), 6.0d, 2, 0);
        RecommendationFeedbackEntry disliked = entry(
                "2", "Artist B", "Song B", Set.of("metal"), -3.0d, 0, 1);

        PersonalTasteProfile profile = PersonalRankingModel.build(List.of(liked, disliked));

        assertTrue(profile.artistScore("Artist A") > 0.0d);
        assertTrue(profile.artistScore("Artist B") < 0.0d);
        assertTrue(profile.tagScore(Set.of("rock")) > 0.0d);
        assertTrue(profile.tagScore(Set.of("metal")) < 0.0d);
        assertEquals(3, profile.evidenceSignals());
    }

    @Test
    void positiveArtistAffinityCanBeatSmallSimilarityGap() {
        PersonalTasteProfile profile = PersonalRankingModel.build(List.of(
                entry("1", "Loved Artist", "Old Song", Set.of("rock"), 9.0d, 3, 0)));
        RecommendationCandidate rawSimilarityWinner = new RecommendationCandidate(
                "Unknown Artist", "Track One", 0.84d, "Last.fm", "raw");
        RecommendationCandidate personalWinner = new RecommendationCandidate(
                "Loved Artist", "Track Two", 0.80d, "Last.fm", "personal", Set.of("rock"));
        RecommendationContext context = new RecommendationContext(Set.of(), Set.of(), Set.of(), profile);

        assertEquals(personalWinner.identity(), RecommendationRanker.best(
                        List.of(rawSimilarityWinner, personalWinner),
                        RadioStrategy.SIMILAR,
                        context)
                .orElseThrow()
                .candidate()
                .identity());
    }

    @Test
    void negativeExactTrackAffinityDemotesCandidate() {
        RecommendationFeedbackEntry disliked = entry(
                "1", "Artist", "Bad Song", Set.of(), -8.0d, 0, 3);
        PersonalTasteProfile profile = PersonalRankingModel.build(List.of(disliked));
        RecommendationCandidate bad = new RecommendationCandidate("Artist", "Bad Song", 0.96d, "Last.fm", "bad");
        RecommendationCandidate fresh = new RecommendationCandidate("Other", "Fresh", 0.86d, "Last.fm", "fresh");

        assertEquals(fresh.identity(), RecommendationRanker.best(
                        List.of(bad, fresh),
                        RadioStrategy.SIMILAR,
                        new RecommendationContext(Set.of(), Set.of(), Set.of(), profile))
                .orElseThrow()
                .candidate()
                .identity());
    }

    @Test
    void explorationRateFallsAsUsefulEvidenceAccumulates() {
        PersonalTasteProfile empty = PersonalTasteProfile.empty();
        PersonalTasteProfile learned = PersonalRankingModel.build(List.of(
                entry("1", "A", "One", Set.of(), 9.0d, 12, 0),
                entry("2", "B", "Two", Set.of(), 6.0d, 8, 0)));

        assertTrue(PersonalRankingModel.explorationRate(learned, RadioStrategy.DISCOVERY)
                < PersonalRankingModel.explorationRate(empty, RadioStrategy.DISCOVERY));
    }

    private static RecommendationFeedbackEntry entry(
            String id,
            String artist,
            String title,
            Set<String> tags,
            double score,
            int positive,
            int negative) {
        return new RecommendationFeedbackEntry(
                id,
                42L,
                7L,
                "Seed",
                "Seed Song",
                artist,
                title,
                RecommendationIdentity.of(artist, title),
                tags,
                RadioStrategy.DISCOVERY,
                "Last.fm",
                0.8d,
                1_700_000_000_000L,
                score > 0 ? RecommendationOutcome.FAVORITED : RecommendationOutcome.UNFAVORITED,
                1_700_000_001_000L,
                positive,
                negative,
                score,
                1.0d);
    }
}
