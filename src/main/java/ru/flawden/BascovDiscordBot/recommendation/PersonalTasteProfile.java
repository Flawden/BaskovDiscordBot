package ru.flawden.BascovDiscordBot.recommendation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read-only personal preference model derived exclusively from bounded recommendation feedback.
 */
public record PersonalTasteProfile(
        int recommendations,
        int positiveSignals,
        int negativeSignals,
        Map<String, Double> trackAffinity,
        Map<String, Double> artistAffinity,
        Map<String, Double> tagAffinity) {

    public PersonalTasteProfile {
        recommendations = Math.max(0, recommendations);
        positiveSignals = Math.max(0, positiveSignals);
        negativeSignals = Math.max(0, negativeSignals);
        trackAffinity = immutable(trackAffinity);
        artistAffinity = immutable(artistAffinity);
        tagAffinity = immutable(tagAffinity);
    }

    public static PersonalTasteProfile empty() {
        return new PersonalTasteProfile(0, 0, 0, Map.of(), Map.of(), Map.of());
    }

    public int evidenceSignals() {
        return positiveSignals + negativeSignals;
    }

    public double confidence() {
        return Math.min(1.0d, evidenceSignals() / 8.0d);
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
        return Math.tanh(raw / 4.0d);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
