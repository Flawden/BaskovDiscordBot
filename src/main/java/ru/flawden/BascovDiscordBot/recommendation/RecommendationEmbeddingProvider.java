package ru.flawden.BascovDiscordBot.recommendation;

import java.util.Set;

/**
 * Pluggable provider for track/taste vector representations.
 * Implementations must be side-effect free from the ranker's point of view.
 */
public interface RecommendationEmbeddingProvider {

    String name();

    int dimensions();

    RecommendationEmbedding embed(String artist, String title, Set<String> tags);

    default RecommendationEmbedding embed(RecommendationCandidate candidate) {
        if (candidate == null) {
            return RecommendationEmbedding.zero(dimensions());
        }
        return embed(candidate.artist(), candidate.title(), candidate.tags());
    }
}
