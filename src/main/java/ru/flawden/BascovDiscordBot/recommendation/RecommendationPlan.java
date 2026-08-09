package ru.flawden.BascovDiscordBot.recommendation;

import ru.flawden.BascovDiscordBot.library.StoredTrack;

/**
 * Итог candidate-generation до transport-поиска в YouTube.
 */
public record RecommendationPlan(
        RecommendationCandidate candidate,
        boolean external,
        boolean fallback) {

    public static RecommendationPlan familiar(StoredTrack seed) {
        return new RecommendationPlan(new RecommendationCandidate(
                seed.author(),
                seed.title(),
                1.0d,
                "local",
                "Знакомое продолжение из твоей/серверной истории"), false, false);
    }

    public static RecommendationPlan fallback(StoredTrack seed, String reason) {
        return new RecommendationPlan(new RecommendationCandidate(
                seed.author(),
                seed.title(),
                0.0d,
                "local-fallback",
                reason), false, true);
    }
}
