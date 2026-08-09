package ru.flawden.BascovDiscordBot.recommendation;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Local deterministic embedding foundation.
 *
 * This is intentionally not advertised as a neural/semantic model. It uses signed
 * feature hashing over artist/title/tags so the rest of the system can evolve around
 * a stable vector API before a heavier provider is introduced.
 */
@Component
public class FeatureHashRecommendationEmbeddingProvider implements RecommendationEmbeddingProvider {

    public static final int DIMENSIONS = 64;
    private static final int MAX_TITLE_TOKENS = 8;

    @Override
    public String name() {
        return "feature-hash-v1";
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public RecommendationEmbedding embed(String artist, String title, Set<String> tags) {
        double[] values = new double[DIMENSIONS];
        String normalizedArtist = normalize(artist);
        String normalizedTitle = normalize(title);

        if (!normalizedArtist.isBlank() && !"unknown".equals(normalizedArtist)) {
            addFeature(values, "artist:" + normalizedArtist, 1.00d);
        }
        if (!normalizedTitle.isBlank() && !"unknown".equals(normalizedTitle)) {
            addFeature(values, "track:" + normalizedArtist + "::" + normalizedTitle, 0.80d);
            int used = 0;
            for (String token : normalizedTitle.split(" ")) {
                if (token.length() < 3 || used >= MAX_TITLE_TOKENS) {
                    continue;
                }
                addFeature(values, "title:" + token, 0.28d);
                used++;
            }
        }
        if (tags != null) {
            LinkedHashSet<String> bounded = new LinkedHashSet<>();
            for (String tag : tags) {
                String normalized = normalize(tag);
                if (!normalized.isBlank() && !"unknown".equals(normalized)) {
                    bounded.add(normalized);
                }
                if (bounded.size() >= 8) {
                    break;
                }
            }
            for (String tag : bounded) {
                addFeature(values, "tag:" + tag, 0.72d);
            }
        }
        return RecommendationVectorMath.normalized(values);
    }

    private static void addFeature(double[] values, String feature, double weight) {
        long first = fnv1a64(feature);
        long second = mix64(first ^ 0x9E3779B97F4A7C15L);
        addHashed(values, first, weight);
        addHashed(values, second, weight * 0.55d);
    }

    private static void addHashed(double[] values, long hash, double weight) {
        int index = (int) Math.floorMod(hash, values.length);
        double sign = (hash & 1L) == 0L ? 1.0d : -1.0d;
        values[index] += sign * weight;
    }

    private static long fnv1a64(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return mix64(hash);
    }

    private static long mix64(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
