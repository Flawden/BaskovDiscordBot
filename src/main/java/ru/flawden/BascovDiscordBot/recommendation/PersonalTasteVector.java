package ru.flawden.BascovDiscordBot.recommendation;

/**
 * Vector representation of a user's bounded recommendation feedback.
 */
public record PersonalTasteVector(
        RecommendationEmbedding vector,
        String provider,
        int dimensions,
        int contributingFeatures,
        double confidence) {

    public PersonalTasteVector {
        provider = provider == null || provider.isBlank() ? "none" : provider.trim();
        dimensions = Math.max(0, dimensions);
        contributingFeatures = Math.max(0, contributingFeatures);
        confidence = clamp(confidence);
        if (vector == null && dimensions > 0) {
            vector = RecommendationEmbedding.zero(dimensions);
        }
    }

    public static PersonalTasteVector empty(RecommendationEmbeddingProvider provider) {
        int dimensions = provider == null ? 0 : provider.dimensions();
        return new PersonalTasteVector(
                dimensions == 0 ? null : RecommendationEmbedding.zero(dimensions),
                provider == null ? "none" : provider.name(),
                dimensions,
                0,
                0.0d);
    }

    public boolean available() {
        return vector != null && !vector.empty() && confidence > 0.0d;
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
