package ru.flawden.BascovDiscordBot.recommendation;

import lombok.extern.slf4j.Slf4j;
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

    public SmartDiscoveryEngine(LastFmRecommendationProvider provider, DiscoveryProperties properties) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public boolean externalAvailable() {
        return provider.available();
    }

    public String providerName() {
        return provider.name();
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

        return provider.similarTracks(seed, properties.getCandidateLimit())
                .thenApply(candidates -> select(seed, mode, context, candidates));
    }

    RecommendationPlan select(
            StoredTrack seed,
            RadioStrategy strategy,
            RecommendationContext context,
            List<RecommendationCandidate> candidates) {
        return RecommendationRanker.best(candidates, strategy, context)
                .map(scored -> {
                    RecommendationCandidate candidate = scored.candidate();
                    String reason = buildReason(candidate, strategy, scored);
                    return new RecommendationPlan(new RecommendationCandidate(
                            candidate.artist(),
                            candidate.title(),
                            candidate.similarity(),
                            candidate.source(),
                            reason), true, false);
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
        reason.append(" • score ").append(Math.round(scored.score() * 100.0d)).append("%");
        return reason.toString();
    }
}
