package ru.flawden.BascovDiscordBot.recommendation;

/**
 * Tiny dependency-free vector math for recommendation embeddings.
 */
public final class RecommendationVectorMath {

    private RecommendationVectorMath() {
    }

    public static double cosine(RecommendationEmbedding left, RecommendationEmbedding right) {
        if (left == null || right == null || left.dimensions() != right.dimensions()) {
            return 0.0d;
        }
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int index = 0; index < left.dimensions(); index++) {
            double a = left.value(index);
            double b = right.value(index);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm <= 1.0e-18d || rightNorm <= 1.0e-18d) {
            return 0.0d;
        }
        return clamp(dot / Math.sqrt(leftNorm * rightNorm));
    }

    public static RecommendationEmbedding normalized(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values cannot be empty");
        }
        double squared = 0.0d;
        for (double value : values) {
            if (Double.isFinite(value)) {
                squared += value * value;
            }
        }
        if (squared <= 1.0e-18d) {
            return RecommendationEmbedding.zero(values.length);
        }
        double norm = Math.sqrt(squared);
        double[] normalized = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            normalized[index] = Double.isFinite(values[index]) ? values[index] / norm : 0.0d;
        }
        return new RecommendationEmbedding(normalized);
    }

    public static void addScaled(double[] target, RecommendationEmbedding vector, double scale) {
        if (target == null || vector == null || target.length != vector.dimensions() || !Double.isFinite(scale)) {
            return;
        }
        for (int index = 0; index < target.length; index++) {
            target[index] += vector.value(index) * scale;
        }
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }
        return Math.max(-1.0d, Math.min(1.0d, value));
    }
}
