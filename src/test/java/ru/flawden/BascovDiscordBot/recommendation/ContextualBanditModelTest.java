package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextualBanditModelTest {

    @Test
    void coldStartUsesBoundedStrategyPriors() {
        ContextualBanditModel.BanditDecision safe = ContextualBanditModel.decide(
                candidate(0.90d), RadioStrategy.DISCOVERY, ContextualBanditProfile.empty(), SessionTasteProfile.empty(0L));
        ContextualBanditModel.BanditDecision bold = ContextualBanditModel.decide(
                candidate(0.45d), RadioStrategy.DISCOVERY, ContextualBanditProfile.empty(), SessionTasteProfile.empty(0L));

        assertTrue(bold.contribution() > safe.contribution());
        assertTrue(Math.abs(bold.contribution()) <= 0.12d);
    }

    @Test
    void positiveBoldFeedbackMakesBoldArmMoreAttractive() {
        List<RecommendationFeedbackEntry> history = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            history.add(outcome(0.45d, RadioStrategy.DISCOVERY, RecommendationOutcome.FAVORITED));
            history.add(outcome(0.90d, RadioStrategy.DISCOVERY, RecommendationOutcome.QUICK_SKIPPED));
        }
        ContextualBanditProfile profile = ContextualBanditModel.build(history);

        assertEquals(ExplorationArm.BOLD,
                ContextualBanditModel.preferredArm(profile, RadioStrategy.DISCOVERY, SessionTasteProfile.empty(0L)));
        assertTrue(profile.stats(RadioStrategy.DISCOVERY, ExplorationArm.BOLD).meanReward() > 0.0d);
    }

    @Test
    void negativeBoldFeedbackCanPushPolicyTowardBalanced() {
        List<RecommendationFeedbackEntry> history = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            history.add(outcome(0.45d, RadioStrategy.SIMILAR, RecommendationOutcome.QUICK_SKIPPED));
            history.add(outcome(0.72d, RadioStrategy.SIMILAR, RecommendationOutcome.COMPLETED));
        }
        ContextualBanditProfile profile = ContextualBanditModel.build(history);

        assertEquals(ExplorationArm.BALANCED,
                ContextualBanditModel.preferredArm(profile, RadioStrategy.SIMILAR, SessionTasteProfile.empty(0L)));
        assertTrue(profile.stats(RadioStrategy.SIMILAR, ExplorationArm.BOLD).meanReward() < 0.0d);
    }

    @Test
    void feedbackIsLearnedPerRadioStrategy() {
        ContextualBanditProfile profile = ContextualBanditModel.build(List.of(
                outcome(0.45d, RadioStrategy.DISCOVERY, RecommendationOutcome.FAVORITED),
                outcome(0.45d, RadioStrategy.DISCOVERY, RecommendationOutcome.FAVORITED)));

        assertEquals(2, profile.totalSamples(RadioStrategy.DISCOVERY));
        assertEquals(0, profile.totalSamples(RadioStrategy.SIMILAR));
    }

    @Test
    void poorSessionMomentumTemporarilyFavorsBroaderExploration() {
        SessionTasteProfile badSession = new SessionTasteProfile(
                1L, 4, 0, 4, -1.0d, java.util.Map.of(), java.util.Map.of(), java.util.Map.of());
        ContextualBanditModel.BanditDecision safe = ContextualBanditModel.decide(
                candidate(0.90d), RadioStrategy.SIMILAR, ContextualBanditProfile.empty(), badSession);
        ContextualBanditModel.BanditDecision bold = ContextualBanditModel.decide(
                candidate(0.45d), RadioStrategy.SIMILAR, ContextualBanditProfile.empty(), badSession);

        assertTrue(bold.contribution() > safe.contribution());
    }

    @Test
    void rewardNormalizationStaysBounded() {
        assertTrue(ContextualBanditModel.normalizedReward(1000.0d) <= 1.0d);
        assertTrue(ContextualBanditModel.normalizedReward(-1000.0d) >= -1.0d);
    }

    private static RecommendationCandidate candidate(double similarity) {
        return new RecommendationCandidate("Artist", "Track " + similarity, similarity, "Last.fm", "test");
    }

    private static RecommendationFeedbackEntry outcome(
            double similarity,
            RadioStrategy strategy,
            RecommendationOutcome outcome) {
        RecommendationFeedbackEntry pending = FileRecommendationFeedbackRepository.pending(
                42L,
                77L,
                "Seed",
                "Seed Track",
                "Candidate",
                "Track " + similarity,
                RecommendationIdentity.of("Candidate", "Track " + similarity),
                strategy,
                "Last.fm",
                similarity);
        return pending.withOutcome(outcome, System.currentTimeMillis(), outcome.negative() ? 0.05d : 1.0d);
    }
}
