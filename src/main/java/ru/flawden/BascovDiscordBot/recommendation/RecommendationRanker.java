package ru.flawden.BascovDiscordBot.recommendation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic ranker поверх provider similarity + novelty/diversity.
 */
public final class RecommendationRanker {

    private RecommendationRanker() {
    }

    public static Optional<ScoredCandidate> best(
            List<RecommendationCandidate> candidates,
            RadioStrategy strategy,
            RecommendationContext context) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        RecommendationContext safe = context == null ? RecommendationContext.empty() : context;
        RadioStrategy mode = strategy == null ? RadioStrategy.SIMILAR : strategy;

        return candidates.stream()
                .map(candidate -> score(candidate, mode, safe))
                .filter(scored -> !scored.rejected())
                .max(Comparator
                        .comparingDouble(ScoredCandidate::score)
                        .thenComparing(scored -> scored.candidate().identity()));
    }

    private static ScoredCandidate score(
            RecommendationCandidate candidate,
            RadioStrategy strategy,
            RecommendationContext context) {
        String identity = candidate.identity();
        String artist = RecommendationIdentity.normalizeArtist(candidate.artist());
        boolean known = context.knownTrackIdentities().contains(identity);
        boolean recent = context.recentTrackIdentities().contains(identity);
        boolean artistRecent = context.recentArtists().contains(artist);

        if (recent || (strategy.hardNovelty() && known)) {
            return new ScoredCandidate(candidate, -1.0d, true, known, artistRecent);
        }

        double novelty = known ? 0.0d : 1.0d;
        double diversity = artistRecent ? 0.0d : 1.0d;
        double score = switch (strategy) {
            case FAMILIAR -> candidate.similarity();
            case SIMILAR -> candidate.similarity() * 0.78d + novelty * 0.14d + diversity * 0.08d;
            case DISCOVERY -> candidate.similarity() * 0.58d + novelty * 0.30d + diversity * 0.12d;
        };
        if (known && strategy == RadioStrategy.SIMILAR) {
            score -= 0.18d;
        }
        if (artistRecent) {
            score -= strategy == RadioStrategy.DISCOVERY ? 0.25d : 0.10d;
        }
        return new ScoredCandidate(candidate, score, false, known, artistRecent);
    }

    public record ScoredCandidate(
            RecommendationCandidate candidate,
            double score,
            boolean rejected,
            boolean known,
            boolean artistRecent) {
    }
}
