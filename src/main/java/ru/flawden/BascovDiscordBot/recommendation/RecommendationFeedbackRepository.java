package ru.flawden.BascovDiscordBot.recommendation;

import java.util.List;
import java.util.Optional;

public interface RecommendationFeedbackRepository {

    int MAX_ENTRIES_PER_USER = 200;

    RecommendationFeedbackEntry recordRecommendation(RecommendationFeedbackEntry entry);

    Optional<RecommendationFeedbackEntry> recordLatestOutcome(
            long guildId,
            String trackIdentity,
            RecommendationOutcome outcome,
            double completionRatio);

    Optional<RecommendationFeedbackEntry> recordUserOutcome(
            long guildId,
            long userId,
            String trackIdentity,
            RecommendationOutcome outcome,
            double completionRatio);

    /**
     * Records behavior for a track even when it was never emitted by Smart Radio.
     * Implementations may override this to make lookup/create/update atomic.
     */
    default RecommendationFeedbackEntry recordObservedOutcome(
            RecommendationFeedbackEntry observed,
            RecommendationOutcome outcome,
            double completionRatio) {
        if (observed == null) {
            throw new IllegalArgumentException("observed entry cannot be null");
        }
        return recordUserOutcome(
                observed.guildId(),
                observed.userId(),
                observed.trackIdentity(),
                outcome,
                completionRatio).orElseGet(() -> recordRecommendation(
                        observed.withOutcome(outcome, System.currentTimeMillis(), completionRatio)));
    }

    List<RecommendationFeedbackEntry> history(long guildId, long userId, int limit);
}
