package ru.flawden.BascovDiscordBot.recommendation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.DiscoveryProperties;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Provider-neutral orchestration: candidate generation -> novelty/diversity ranking -> fallback.
 */
@Slf4j
@Component
public class SmartDiscoveryEngine {

    private final RecommendationProvider provider;
    private final DiscoveryProperties properties;
    private final RecommendationEmbeddingProvider embeddingProvider;
    private final CollaborativeSignalProvider collaborativeProvider;

    @Autowired
    public SmartDiscoveryEngine(
            LastFmRecommendationProvider provider,
            DiscoveryProperties properties,
            FeatureHashRecommendationEmbeddingProvider embeddingProvider,
            ListenBrainzCollaborativeProvider collaborativeProvider) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
        this.collaborativeProvider = Objects.requireNonNull(collaborativeProvider, "collaborativeProvider");
    }

    SmartDiscoveryEngine(LastFmRecommendationProvider provider, DiscoveryProperties properties) {
        this(provider, properties, new FeatureHashRecommendationEmbeddingProvider(), disabledCollaborativeProvider());
    }

    SmartDiscoveryEngine(
            LastFmRecommendationProvider provider,
            DiscoveryProperties properties,
            RecommendationEmbeddingProvider embeddingProvider,
            CollaborativeSignalProvider collaborativeProvider) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
        this.collaborativeProvider = Objects.requireNonNull(collaborativeProvider, "collaborativeProvider");
    }

    public boolean externalAvailable() {
        return provider.available();
    }

    public String providerName() {
        return provider.name();
    }

    public boolean collaborativeAvailable() {
        return collaborativeProvider.available();
    }

    public String collaborativeProviderName() {
        return collaborativeProvider.name();
    }

    public CompletableFuture<RecommendationPlan> recommend(
            StoredTrack seed,
            RadioStrategy strategy,
            RecommendationContext context) {
        RadioStrategy mode = strategy == null ? RadioStrategy.FAMILIAR : strategy;
        if (mode == RadioStrategy.FAMILIAR) {
            return CompletableFuture.completedFuture(RecommendationPlan.familiar(seed));
        }
        if (!provider.available()) {
            return CompletableFuture.completedFuture(RecommendationPlan.fallback(
                    seed,
                    "Внешний similarity-provider не настроен; локальный fallback из seed"));
        }

        CompletableFuture<List<RecommendationCandidate>> candidatesFuture =
                provider.similarTracks(seed, properties.getCandidateLimit());
        CompletableFuture<CollaborativeArtistSignals> collaborativeFuture = collaborativeProvider.available()
                ? collaborativeProvider.signalsFor(seed)
                : CompletableFuture.completedFuture(CollaborativeArtistSignals.empty());

        return candidatesFuture.thenCombine(
                        collaborativeFuture.exceptionally(exception -> CollaborativeArtistSignals.empty()),
                        (candidates, signals) -> select(
                                seed,
                                mode,
                                (context == null ? RecommendationContext.empty() : context)
                                        .withCollaborativeSignals(signals),
                                candidates))
                .thenCompose(this::enrichSelected);
    }

    private CompletableFuture<RecommendationPlan> enrichSelected(RecommendationPlan plan) {
        if (plan == null || !plan.external() || plan.candidate() == null) {
            return CompletableFuture.completedFuture(plan);
        }
        return provider.enrich(plan.candidate())
                .thenApply(enriched -> enriched == null
                        ? plan
                        : new RecommendationPlan(enriched, plan.external(), plan.fallback()))
                .exceptionally(exception -> plan);
    }

    RecommendationPlan select(
            StoredTrack seed,
            RadioStrategy strategy,
            RecommendationContext context,
            List<RecommendationCandidate> candidates) {
        return RecommendationRanker.best(candidates, strategy, context, embeddingProvider)
                .map(scored -> {
                    RecommendationCandidate candidate = scored.candidate();
                    String reason = buildReason(candidate, strategy, scored);
                    return new RecommendationPlan(new RecommendationCandidate(
                            candidate.artist(),
                            candidate.title(),
                            candidate.similarity(),
                            candidate.source(),
                            reason,
                            candidate.tags()), true, false);
                })
                .orElseGet(() -> RecommendationPlan.fallback(
                        seed,
                        strategy == RadioStrategy.DISCOVERY
                                ? "Provider не нашёл нового кандидата после novelty/diversity filter"
                                : "Provider не нашёл подходящего похожего кандидата; локальный fallback"));
    }

    private static String buildReason(
            RecommendationCandidate candidate,
            RadioStrategy strategy,
            RecommendationRanker.ScoredCandidate scored) {
        StringBuilder reason = new StringBuilder();
        reason.append(candidate.source())
                .append(" similarity ")
                .append(Math.round(candidate.similarity() * 100.0d))
                .append("%");
        if (!scored.known()) {
            reason.append(" • трек новый для памяти Баскова");
        } else if (strategy == RadioStrategy.SIMILAR) {
            reason.append(" • знакомый трек получил repetition penalty");
        }
        if (!scored.artistRecent()) {
            reason.append(" • artist cooldown чист");
        }
        if (Math.abs(scored.personalTaste()) >= 0.01d) {
            reason.append(" • personal ")
                    .append(signedPercent(scored.personalTaste()));
        }
        if (Math.abs(scored.artistAffinity()) >= 0.01d) {
            reason.append(" • artist ").append(signedPercent(scored.artistAffinity()));
        }
        if (Math.abs(scored.tagAffinity()) >= 0.01d) {
            reason.append(" • tags ").append(signedPercent(scored.tagAffinity()));
        }
        if (Math.abs(scored.vectorContribution()) >= 0.005d) {
            reason.append(" • vector ")
                    .append(signedPercent(scored.vectorSimilarity()))
                    .append(" @ ")
                    .append(Math.round(scored.vectorConfidence() * 100.0d))
                    .append("% confidence");
        }
        if (scored.collaborativeContribution() > 0.005d) {
            reason.append(" • collaborative ")
                    .append(signedPercent(scored.collaborativeAffinity()))
                    .append(" via ")
                    .append(scored.collaborativeSource());
        }
        if (scored.explorationBonus() > 0.0d) {
            reason.append(" • exploration +")
                    .append(Math.round(scored.explorationBonus() * 100.0d))
                    .append("%");
        }
        reason.append(" • final score ").append(Math.round(scored.score() * 100.0d)).append("%");
        return reason.toString();
    }

    private static CollaborativeSignalProvider disabledCollaborativeProvider() {
        return new CollaborativeSignalProvider() {
            @Override
            public String name() {
                return "none";
            }

            @Override
            public boolean available() {
                return false;
            }

            @Override
            public CompletableFuture<CollaborativeArtistSignals> signalsFor(StoredTrack seed) {
                return CompletableFuture.completedFuture(CollaborativeArtistSignals.empty());
            }
        };
    }

    private static String signedPercent(double value) {
        long rounded = Math.round(value * 100.0d);
        return (rounded >= 0 ? "+" : "") + rounded + "%";
    }
}
