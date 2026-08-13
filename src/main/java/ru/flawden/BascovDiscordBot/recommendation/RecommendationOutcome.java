package ru.flawden.BascovDiscordBot.recommendation;

/**
 * Latest observable result of a recommendation. Positive/negative signal counts
 * are accumulated separately so future ranking can use repeated evidence.
 */
public enum RecommendationOutcome {
    PENDING(0.0d),
    PLAYED(0.25d),
    COMPLETED(1.0d),
    FAVORITED(3.0d),
    REPLAYED(2.0d),
    SKIPPED(0.0d),
    QUICK_SKIPPED(-2.0d),
    STOPPED(0.0d),
    QUICK_STOPPED(-2.0d),
    UNFAVORITED(-3.0d);

    private final double weight;

    RecommendationOutcome(double weight) {
        this.weight = weight;
    }

    public double weight() {
        return weight;
    }

    public boolean positive() {
        return weight > 0.0d;
    }

    public boolean negative() {
        return weight < 0.0d;
    }
}
