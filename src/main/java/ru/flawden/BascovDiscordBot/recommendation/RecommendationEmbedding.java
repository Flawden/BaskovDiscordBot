package ru.flawden.BascovDiscordBot.recommendation;

import java.util.Arrays;

/**
 * Immutable bounded vector used by recommendation ranking.
 */
public final class RecommendationEmbedding {

    private final double[] values;

    public RecommendationEmbedding(double[] values) {
        if (values == null || values.length == 0 || values.length > 512) {
            throw new IllegalArgumentException("embedding dimension must be between 1 and 512");
        }
        this.values = Arrays.copyOf(values, values.length);
        for (int index = 0; index < this.values.length; index++) {
            if (!Double.isFinite(this.values[index])) {
                this.values[index] = 0.0d;
            }
        }
    }

    public static RecommendationEmbedding zero(int dimensions) {
        if (dimensions <= 0 || dimensions > 512) {
            throw new IllegalArgumentException("embedding dimension must be between 1 and 512");
        }
        return new RecommendationEmbedding(new double[dimensions]);
    }

    public int dimensions() {
        return values.length;
    }

    public double value(int index) {
        return values[index];
    }

    public double[] values() {
        return Arrays.copyOf(values, values.length);
    }

    public double norm() {
        double squared = 0.0d;
        for (double value : values) {
            squared += value * value;
        }
        return Math.sqrt(squared);
    }

    public boolean empty() {
        return norm() <= 1.0e-12d;
    }
}
