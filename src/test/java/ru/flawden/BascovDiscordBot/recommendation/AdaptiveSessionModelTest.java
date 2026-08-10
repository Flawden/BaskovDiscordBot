package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveSessionModelTest {

    private static final long START = 1_800_000_000_000L;

    @Test
    void ignoresFeedbackFromBeforeCurrentRadioSession() {
        SessionTasteProfile profile = AdaptiveSessionModel.build(List.of(
                entry("old", "Old Artist", "Old Song", Set.of("rock"), START - 1_000L, 9.0d, 3, 0),
                entry("new", "New Artist", "New Song", Set.of("punk"), START + 1_000L, 3.0d, 1, 0)), START);

        assertEquals(1, profile.recommendations());
        assertTrue(profile.artistScore("New Artist") > 0.0d);
        assertEquals(0.0d, profile.artistScore("Old Artist"), 1.0e-9d);
    }

    @Test
    void recentPositiveSessionFeedbackBuildsPositiveMomentum() {
        SessionTasteProfile profile = AdaptiveSessionModel.build(List.of(
                entry("1", "Loved Artist", "One", Set.of("pop punk"), START + 2_000L, 6.0d, 2, 0),
                entry("2", "Loved Artist", "Two", Set.of("rock"), START + 1_000L, 3.0d, 1, 0)), START);

        assertTrue(profile.momentum() > 0.9d);
        assertTrue(profile.artistScore("Loved Artist") > 0.0d);
        assertTrue(profile.tagScore(Set.of("pop punk")) > 0.0d);
        assertEquals(3, profile.evidenceSignals());
    }

    @Test
    void negativeSessionFeedbackProducesNegativeMomentumAndExplorationPressure() {
        RecommendationFeedbackEntry disliked = entry(
                "1", "Bad Artist", "Bad Song", Set.of("metal"), START + 1_000L, -6.0d, 0, 3);
        SessionTasteProfile profile = AdaptiveSessionModel.build(List.of(disliked), START);
        RecommendationCandidate fresh = new RecommendationCandidate(
                "Fresh Artist", "Fresh Song", 0.7d, "Last.fm", "fresh");

        assertTrue(profile.momentum() < -0.9d);
        assertTrue(profile.artistScore("Bad Artist") < 0.0d);
        assertTrue(AdaptiveSessionModel.explorationAdjustment(profile, fresh) > 0.0d);
    }

    @Test
    void emptySessionIsNeutral() {
        SessionTasteProfile profile = AdaptiveSessionModel.build(List.of(), START);

        assertEquals(0, profile.evidenceSignals());
        assertEquals(0.0d, profile.confidence(), 1.0e-9d);
        assertEquals(0.0d, profile.momentum(), 1.0e-9d);
    }

    @Test
    void newerFeedbackHasMoreWeightThanOlderFeedbackInSameSession() {
        SessionTasteProfile profile = AdaptiveSessionModel.build(List.of(
                entry("new", "New Mood", "One", Set.of(), START + 10_000L, 3.0d, 1, 0),
                entry("old", "Old Mood", "Two", Set.of(), START + 1_000L, 3.0d, 1, 0)), START);

        assertTrue(profile.artistScore("New Mood") > profile.artistScore("Old Mood"));
    }

    private static RecommendationFeedbackEntry entry(
            String id,
            String artist,
            String title,
            Set<String> tags,
            long recommendedAt,
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
                recommendedAt,
                score > 0.0d ? RecommendationOutcome.FAVORITED : RecommendationOutcome.UNFAVORITED,
                recommendedAt + 100L,
                positive,
                negative,
                score,
                1.0d);
    }
}
