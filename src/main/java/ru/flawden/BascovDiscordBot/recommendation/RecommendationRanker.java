package ru.flawden.BascovDiscordBot.recommendation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic hybrid ranker: provider similarity + novelty/diversity + personal feedback model.
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
            return rejected(candidate, known, artistRecent, context.personalTaste(), strategy);
        }

        double novelty = known ? 0.0d : 1.0d;
        double diversity = artistRecent ? 0.0d : 1.0d;
        double baseScore = switch (strategy) {
            case FAMILIAR -> candidate.similarity();
            case SIMILAR -> candidate.similarity() * 0.78d + novelty * 0.14d + diversity * 0.08d;
            case DISCOVERY -> candidate.similarity() * 0.58d + novelty * 0.30d + diversity * 0.12d;
        };
        if (known && strategy == RadioStrategy.SIMILAR) {
            baseScore -= 0.18d;
        }
        if (artistRecent) {
            baseScore -= strategy == RadioStrategy.DISCOVERY ? 0.25d : 0.10d;
        }

        PersonalRankingModel.TasteScore taste = PersonalRankingModel.score(
                candidate,
                context.personalTaste(),
                strategy);
        double personalWeight = switch (strategy) {
            case FAMILIAR -> 0.12d;
            case SIMILAR -> 0.35d;
            case DISCOVERY -> 0.40d;
        } * context.personalTaste().confidence();
        double personalContribution = taste.personalTaste() * personalWeight;
        double finalScore = Math.max(-1.0d, Math.min(1.25d,
                baseScore + personalContribution + taste.explorationBonus()));

        return new ScoredCandidate(
                candidate,
                finalScore,
                baseScore,
                false,
                known,
                artistRecent,
                taste.trackAffinity(),
                taste.artistAffinity(),
                taste.tagAffinity(),
                taste.personalTaste(),
                taste.explorationRate(),
                taste.explorationBonus());
    }

    private static ScoredCandidate rejected(
            RecommendationCandidate candidate,
            boolean known,
            boolean artistRecent,
            PersonalTasteProfile profile,
            RadioStrategy strategy) {
        PersonalRankingModel.TasteScore taste = PersonalRankingModel.score(candidate, profile, strategy);
        return new ScoredCandidate(
                candidate,
                -1.0d,
                -1.0d,
                true,
                known,
                artistRecent,
                taste.trackAffinity(),
                taste.artistAffinity(),
                taste.tagAffinity(),
                taste.personalTaste(),
                taste.explorationRate(),
                0.0d);
    }

    public record ScoredCandidate(
            RecommendationCandidate candidate,
            double score,
            double baseScore,
            boolean rejected,
            boolean known,
            boolean artistRecent,
            double trackAffinity,
            double artistAffinity,
            double tagAffinity,
            double personalTaste,
            double explorationRate,
            double explorationBonus) {
    }
}
