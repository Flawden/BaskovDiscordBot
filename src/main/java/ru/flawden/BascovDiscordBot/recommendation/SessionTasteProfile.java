package ru.flawden.BascovDiscordBot.recommendation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ephemeral short-term preference profile derived only from feedback recorded
 * after the current smart-radio session started.
 */
public record SessionTasteProfile(
        long startedAtEpochMillis,
        int recommendations,
        int positiveSignals,
        int negativeSignals,
        double momentum,
        Map<String, Double> trackAffinity,
        Map<String, Double> artistAffinity,
        Map<String, Double> tagAffinity) {

    public SessionTasteProfile {
        startedAtEpochMillis = Math.max(0L, startedAtEpochMillis);
        recommendations = Math.max(0, recommendations);
        positiveSignals = Math.max(0, positiveSignals);
        negativeSignals = Math.max(0, negativeSignals);
        momentum = clamp(momentum, -1.0d, 1.0d);
        trackAffinity = immutable(trackAffinity);
        artistAffinity = immutable(artistAffinity);
        tagAffinity = immutable(tagAffinity);
    }

    public static SessionTasteProfile empty(long startedAtEpochMillis) {
        return new SessionTasteProfile(startedAtEpochMillis, 0, 0, 0, 0.0d, Map.of(), Map.of(), Map.of());
    }

    public int evidenceSignals() {
        return positiveSignals + negativeSignals;
    }

    /** Short-term confidence intentionally ramps faster than the durable model. */
    public double confidence() {
        return Math.min(1.0d, evidenceSignals() / 4.0d);
    }

    public double trackScore(String identity) {
        return normalizeAffinity(trackAffinity.getOrDefault(normalize(identity), 0.0d));
    }

    public double artistScore(String artist) {
        return normalizeAffinity(artistAffinity.getOrDefault(RecommendationIdentity.normalizeArtist(artist), 0.0d));
    }

    public double tagScore(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return 0.0d;
        }
        double total = 0.0d;
        int matched = 0;
        for (String tag : tags) {
            Double raw = tagAffinity.get(normalize(tag));
            if (raw != null) {
                total += normalizeAffinity(raw);
                matched++;
            }
        }
        return matched == 0 ? 0.0d : total / matched;
    }

    public List<Map.Entry<String, Double>> topArtists(int limit) {
        return top(artistAffinity, limit);
    }

    public List<Map.Entry<String, Double>> topTags(int limit) {
        return top(tagAffinity, limit);
    }

    private static List<Map.Entry<String, Double>> top(Map<String, Double> values, int limit) {
        int bounded = Math.max(0, Math.min(limit, 10));
        return values.entrySet().stream()
                .sorted((left, right) -> {
                    int byAbs = Double.compare(Math.abs(right.getValue()), Math.abs(left.getValue()));
                    return byAbs != 0 ? byAbs : left.getKey().compareTo(right.getKey());
                })
                .limit(bounded)
                .toList();
    }

    private static Map<String, Double> immutable(Map<String, Double> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(input));
    }

    private static double normalizeAffinity(double raw) {
        if (!Double.isFinite(raw) || raw == 0.0d) {
            return 0.0d;
        }
        return Math.tanh(raw / 2.5d);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }
        return Math.max(min, Math.min(max, value));
    }
}
