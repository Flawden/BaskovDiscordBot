package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixDiversityPolicyTest {

    @Test
    void immediateArtistRepeatIsRejectedForCuratedMix() {
        MixDiversityProfile profile = new MixDiversityProfile(
                true,
                "",
                List.of("repeat artist", "other"),
                List.of());
        RecommendationCandidate candidate = new RecommendationCandidate(
                "Repeat Artist", "Another Song", 0.90d, "Last.fm", "repeat");

        MixDiversityPolicy.Decision decision = MixDiversityPolicy.evaluate(candidate, profile);

        assertTrue(decision.rejected());
        assertTrue(decision.artistPenalty() < 0.0d);
    }

    @Test
    void matchingThemeGetsBoundedPositiveContribution() {
        MixDiversityProfile profile = new MixDiversityProfile(
                true,
                "pop punk",
                List.of("other artist"),
                List.of(Set.of("alternative rock")));
        RecommendationCandidate candidate = new RecommendationCandidate(
                "Fresh Artist", "Fresh Song", 0.75d, "Last.fm", "theme", Set.of("pop punk", "rock"));

        MixDiversityPolicy.Decision decision = MixDiversityPolicy.evaluate(candidate, profile);

        assertFalse(decision.rejected());
        assertTrue(decision.themeAffinity() > 0.9d);
        assertTrue(decision.contribution() > 0.0d);
        assertTrue(decision.contribution() <= 0.28d);
    }

    @Test
    void saturatedRecentTagReceivesPenalty() {
        MixDiversityProfile profile = new MixDiversityProfile(
                true,
                "",
                List.of("a", "b", "c", "d", "e"),
                List.of(
                        Set.of("rock"),
                        Set.of("rock"),
                        Set.of("rock"),
                        Set.of("rock"),
                        Set.of("pop")));
        RecommendationCandidate candidate = new RecommendationCandidate(
                "New Artist", "Song", 0.80d, "Last.fm", "tag", Set.of("rock"));

        MixDiversityPolicy.Decision decision = MixDiversityPolicy.evaluate(candidate, profile);

        assertTrue(decision.tagPenalty() < 0.0d);
        assertTrue(decision.contribution() < 0.0d);
    }
}
