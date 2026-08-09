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
        return best(candidates, strategy, context, new FeatureHashRecommendationEmbeddingProvider());
    }

    public static Optional<ScoredCandidate> best(
            List<RecommendationCandidate> candidates,
            RadioStrategy strategy,
            RecommendationContext context,
            RecommendationEmbeddingProvider embeddingProvider) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        RecommendationContext safe = context == null ? RecommendationContext.empty() : context;
        RadioStrategy mode = strategy == null ? RadioStrategy.SIMILAR : strategy;
        RecommendationEmbeddingProvider provider = embeddingProvider == null
                ? new FeatureHashRecommendationEmbeddingProvider()
                : embeddingProvider;
        PersonalTasteVector tasteVector = PersonalTasteVectorModel.build(safe.personalTaste(), provider);

        return candidates.stream()
                .map(candidate -> score(candidate, mode, safe, provider, tasteVector))
                .filter(scored -> !scored.rejected())
                .max(Comparator
                        .comparingDouble(ScoredCandidate::score)
                        .thenComparing(scored -> scored.candidate().identity()));
    }

    private static ScoredCandidate score(
            RecommendationCandidate candidate,
            RadioStrategy strategy,
            RecommendationContext context,
            RecommendationEmbeddingProvider embeddingProvider,
            PersonalTasteVector tasteVector) {
        String identity = candidate.identity();
        String artist = RecommendationIdentity.normalizeArtist(candidate.artist());
        boolean known = context.knownTrackIdentities().contains(identity);
        boolean recent = context.recentTrackIdentities().contains(identity);
        boolean artistRecent = context.recentArtists().contains(artist);

        if (recent || (strategy.hardNovelty() && known)) {
            return rejected(candidate, known, artistRecent, context.personalTaste(), strategy, embeddingProvider, tasteVector);
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
        double vectorSimilarity = tasteVector.available()
                ? RecommendationVectorMath.cosine(tasteVector.vector(), embeddingProvider.embed(candidate))
                : 0.0d;
        double vectorWeight = switch (strategy) {
            case FAMILIAR -> 0.06d;
            case SIMILAR -> 0.14d;
            case DISCOVERY -> 0.18d;
        } * tasteVector.confidence();
        double vectorContribution = vectorSimilarity * vectorWeight;
        double collaborativeAffinity = context.collaborativeSignals().affinity(candidate.artist());
        double collaborativeWeight = switch (strategy) {
            case FAMILIAR -> 0.03d;
            case SIMILAR -> 0.12d;
            case DISCOVERY -> 0.16d;
        };
        double collaborativeContribution = collaborativeAffinity * collaborativeWeight;
        double finalScore = Math.max(-1.0d, Math.min(1.30d,
                baseScore + personalContribution + vectorContribution
                        + collaborativeContribution + taste.explorationBonus()));

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
                taste.explorationBonus(),
                vectorSimilarity,
                vectorContribution,
                tasteVector.confidence(),
                collaborativeAffinity,
                collaborativeContribution,
                context.collaborativeSignals().source(),
                embeddingProvider.name(),
                embeddingProvider.dimensions());
    }

    private static ScoredCandidate rejected(
            RecommendationCandidate candidate,
            boolean known,
            boolean artistRecent,
            PersonalTasteProfile profile,
            RadioStrategy strategy,
            RecommendationEmbeddingProvider embeddingProvider,
            PersonalTasteVector tasteVector) {
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
                0.0d,
                0.0d,
                0.0d,
                tasteVector == null ? 0.0d : tasteVector.confidence(),
                0.0d,
                0.0d,
                "none",
                embeddingProvider == null ? "none" : embeddingProvider.name(),
                embeddingProvider == null ? 0 : embeddingProvider.dimensions());
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
            double explorationBonus,
            double vectorSimilarity,
            double vectorContribution,
            double vectorConfidence,
            double collaborativeAffinity,
            double collaborativeContribution,
            String collaborativeSource,
            String embeddingProvider,
            int embeddingDimensions) {
    }
}
