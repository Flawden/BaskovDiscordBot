package ru.flawden.BascovDiscordBot.recommendation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider-neutral collaborative signal: normalized artist -> [0..1] affinity.
 */
public record CollaborativeArtistSignals(
        String source,
        Map<String, Double> artistAffinity) {

    public CollaborativeArtistSignals {
        source = source == null || source.isBlank() ? "none" : source.trim();
        LinkedHashMap<String, Double> safe = new LinkedHashMap<>();
        if (artistAffinity != null) {
            artistAffinity.forEach((artist, score) -> {
                String normalized = RecommendationIdentity.normalizeArtist(artist);
                if (!normalized.isBlank() && score != null && Double.isFinite(score)) {
                    safe.put(normalized, clamp(score));
                }
            });
        }
        artistAffinity = Map.copyOf(safe);
    }

    public static CollaborativeArtistSignals empty() {
        return new CollaborativeArtistSignals("none", Map.of());
    }

    public boolean available() {
        return !artistAffinity.isEmpty();
    }

    public double affinity(String artist) {
        return artistAffinity.getOrDefault(RecommendationIdentity.normalizeArtist(artist), 0.0d);
    }

    private static double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
