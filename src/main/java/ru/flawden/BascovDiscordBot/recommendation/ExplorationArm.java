package ru.flawden.BascovDiscordBot.recommendation;

/**
 * Similarity-risk bucket used by the online exploration policy.
 */
public enum ExplorationArm {
    SAFE("safe"),
    BALANCED("balanced"),
    BOLD("bold");

    private final String label;

    ExplorationArm(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ExplorationArm fromSimilarity(double similarity) {
        double safe = Math.max(0.0d, Math.min(1.0d, similarity));
        if (safe >= 0.82d) {
            return SAFE;
        }
        if (safe >= 0.62d) {
            return BALANCED;
        }
        return BOLD;
    }
}
