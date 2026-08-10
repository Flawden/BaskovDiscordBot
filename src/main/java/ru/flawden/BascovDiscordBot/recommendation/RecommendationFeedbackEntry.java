package ru.flawden.BascovDiscordBot.recommendation;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Durable result of one smart-radio recommendation.
 */
public record RecommendationFeedbackEntry(
        String id,
        long guildId,
        long userId,
        String seedArtist,
        String seedTitle,
        String trackArtist,
        String trackTitle,
        String trackIdentity,
        Set<String> tags,
        RadioStrategy strategy,
        String provider,
        double similarity,
        long recommendedAtEpochMillis,
        RecommendationOutcome lastOutcome,
        long lastOutcomeAtEpochMillis,
        int positiveSignals,
        int negativeSignals,
        double signalScore,
        double lastCompletionRatio) {

    public RecommendationFeedbackEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        if (guildId <= 0L || userId <= 0L) {
            throw new IllegalArgumentException("guildId and userId must be positive");
        }
        seedArtist = safe(seedArtist, "Неизвестно", 120);
        seedTitle = safe(seedTitle, "Неизвестный seed", 180);
        trackArtist = safe(trackArtist, "Неизвестно", 120);
        trackTitle = safe(trackTitle, "Неизвестный трек", 180);
        trackIdentity = safe(trackIdentity, "unknown", 320).toLowerCase(Locale.ROOT);
        tags = safeTags(tags);
        strategy = Objects.requireNonNullElse(strategy, RadioStrategy.FAMILIAR);
        provider = safe(provider, "local", 80);
        similarity = clamp(similarity, 0.0d, 1.0d);
        if (recommendedAtEpochMillis <= 0L) {
            throw new IllegalArgumentException("recommendedAtEpochMillis must be positive");
        }
        lastOutcome = Objects.requireNonNullElse(lastOutcome, RecommendationOutcome.PENDING);
        if (lastOutcomeAtEpochMillis < 0L) {
            throw new IllegalArgumentException("lastOutcomeAtEpochMillis cannot be negative");
        }
        positiveSignals = Math.max(0, positiveSignals);
        negativeSignals = Math.max(0, negativeSignals);
        signalScore = clamp(signalScore, -1000.0d, 1000.0d);
        lastCompletionRatio = clamp(lastCompletionRatio, 0.0d, 1.0d);
    }

    RecommendationFeedbackEntry withRecommendedAtEpochMillis(long epochMillis) {
        long adjustedOutcomeAt = lastOutcomeAtEpochMillis == 0L
                ? 0L
                : Math.max(epochMillis, lastOutcomeAtEpochMillis);
        return new RecommendationFeedbackEntry(
                id,
                guildId,
                userId,
                seedArtist,
                seedTitle,
                trackArtist,
                trackTitle,
                trackIdentity,
                tags,
                strategy,
                provider,
                similarity,
                epochMillis,
                lastOutcome,
                adjustedOutcomeAt,
                positiveSignals,
                negativeSignals,
                signalScore,
                lastCompletionRatio);
    }

    public RecommendationFeedbackEntry withOutcome(
            RecommendationOutcome outcome,
            long occurredAtEpochMillis,
            double completionRatio) {
        RecommendationOutcome safeOutcome = Objects.requireNonNullElse(outcome, RecommendationOutcome.PENDING);
        double weight = safeOutcome.weight();
        return new RecommendationFeedbackEntry(
                id,
                guildId,
                userId,
                seedArtist,
                seedTitle,
                trackArtist,
                trackTitle,
                trackIdentity,
                tags,
                strategy,
                provider,
                similarity,
                recommendedAtEpochMillis,
                safeOutcome,
                Math.max(recommendedAtEpochMillis, occurredAtEpochMillis),
                positiveSignals + (safeOutcome.positive() ? 1 : 0),
                negativeSignals + (safeOutcome.negative() ? 1 : 0),
                signalScore + weight,
                completionRatio);
    }

    private static Set<String> safeTags(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (normalized.length() > 48) {
                normalized = normalized.substring(0, 48).trim();
            }
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
            if (result.size() >= 8) {
                break;
            }
        }
        return Set.copyOf(result);
    }

    private static String safe(String value, String fallback, int maxLength) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength).trim();
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
