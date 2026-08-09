package ru.flawden.BascovDiscordBot.recommendation;

import java.util.Map;
import java.util.Set;

/**
 * Derives one personal taste vector from the existing explainable affinity maps.
 * No persistence/network access: the vector can always be rebuilt from feedback V2.
 */
public final class PersonalTasteVectorModel {

    private PersonalTasteVectorModel() {
    }

    public static PersonalTasteVector build(
            PersonalTasteProfile profile,
            RecommendationEmbeddingProvider provider) {
        if (provider == null) {
            return PersonalTasteVector.empty(null);
        }
        PersonalTasteProfile safe = profile == null ? PersonalTasteProfile.empty() : profile;
        if (safe.evidenceSignals() == 0) {
            return PersonalTasteVector.empty(provider);
        }

        double[] accumulator = new double[provider.dimensions()];
        int features = 0;
        double absoluteWeight = 0.0d;

        for (Map.Entry<String, Double> entry : safe.trackAffinity().entrySet()) {
            String[] parts = splitIdentity(entry.getKey());
            double weight = normalizedAffinity(entry.getValue()) * 0.55d;
            if (Math.abs(weight) <= 1.0e-9d) {
                continue;
            }
            RecommendationVectorMath.addScaled(
                    accumulator,
                    provider.embed(parts[0], parts[1], Set.of()),
                    weight);
            absoluteWeight += Math.abs(weight);
            features++;
        }
        for (Map.Entry<String, Double> entry : safe.artistAffinity().entrySet()) {
            double weight = normalizedAffinity(entry.getValue()) * 0.30d;
            if (Math.abs(weight) <= 1.0e-9d) {
                continue;
            }
            RecommendationVectorMath.addScaled(
                    accumulator,
                    provider.embed(entry.getKey(), "", Set.of()),
                    weight);
            absoluteWeight += Math.abs(weight);
            features++;
        }
        for (Map.Entry<String, Double> entry : safe.tagAffinity().entrySet()) {
            double weight = normalizedAffinity(entry.getValue()) * 0.15d;
            if (Math.abs(weight) <= 1.0e-9d) {
                continue;
            }
            RecommendationVectorMath.addScaled(
                    accumulator,
                    provider.embed("", "", Set.of(entry.getKey())),
                    weight);
            absoluteWeight += Math.abs(weight);
            features++;
        }

        RecommendationEmbedding vector = RecommendationVectorMath.normalized(accumulator);
        double coverage = Math.min(1.0d, features / 12.0d);
        double weightConfidence = Math.min(1.0d, absoluteWeight / 3.0d);
        double confidence = safe.confidence() * (0.45d + coverage * 0.30d + weightConfidence * 0.25d);
        if (vector.empty()) {
            confidence = 0.0d;
        }
        return new PersonalTasteVector(
                vector,
                provider.name(),
                provider.dimensions(),
                features,
                confidence);
    }

    private static String[] splitIdentity(String identity) {
        String safe = identity == null ? "" : identity;
        int separator = safe.indexOf("::");
        if (separator < 0) {
            return new String[]{"", safe};
        }
        return new String[]{safe.substring(0, separator), safe.substring(separator + 2)};
    }

    private static double normalizedAffinity(double raw) {
        if (!Double.isFinite(raw) || raw == 0.0d) {
            return 0.0d;
        }
        return Math.tanh(raw / 4.0d);
    }
}
