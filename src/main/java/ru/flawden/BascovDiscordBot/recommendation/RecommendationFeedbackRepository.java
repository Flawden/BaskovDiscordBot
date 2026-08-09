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

    List<RecommendationFeedbackEntry> history(long guildId, long userId, int limit);
}
