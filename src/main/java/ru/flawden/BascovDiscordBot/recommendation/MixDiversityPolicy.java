package ru.flawden.BascovDiscordBot.recommendation;

import java.util.Set;

/**
 * Curated-station reranking policy.
 *
 * <p>Hard novelty is evaluated by {@link RecommendationRanker} before this policy.
 * This layer may reject an immediate artist repeat and otherwise contributes only a bounded
 * bonus/penalty, so it cannot become a second recommendation engine.</p>
 */
public final class MixDiversityPolicy {

    private static final double MAX_ABSOLUTE_CONTRIBUTION = 0.28d;

    private MixDiversityPolicy() {
    }

    public static Decision evaluate(RecommendationCandidate candidate, MixDiversityProfile profile) {
        MixDiversityProfile safe = profile == null ? MixDiversityProfile.disabled() : profile;
        if (!safe.enabled() || candidate == null) {
            return Decision.neutral();
        }

        boolean immediateArtistRepeat = safe.repeatsImmediateArtist(candidate.artist());
        int artistOccurrences = safe.recentArtistOccurrences(candidate.artist());
        double artistPenalty = immediateArtistRepeat
                ? -0.28d
                : artistOccurrences >= 2
                        ? -0.20d
                        : artistOccurrences == 1 ? -0.08d : 0.04d;

        double tagPenalty = tagSaturationPenalty(candidate.tags(), safe);
        double themeAffinity = themeAffinity(candidate.tags(), safe.themeFocus());
        double themeContribution = safe.themed()
                ? (themeAffinity > 0.0d ? 0.22d * themeAffinity : candidate.tags().isEmpty() ? 0.0d : -0.12d)
                : 0.0d;
        double contribution = clamp(artistPenalty + tagPenalty + themeContribution,
                -MAX_ABSOLUTE_CONTRIBUTION,
                MAX_ABSOLUTE_CONTRIBUTION);

        return new Decision(
                immediateArtistRepeat,
                contribution,
                artistPenalty,
                tagPenalty,
                themeAffinity,
                safe.themeFocus());
    }

    private static double tagSaturationPenalty(Set<String> tags, MixDiversityProfile profile) {
        if (tags == null || tags.isEmpty() || profile.recentTagSets().isEmpty()) {
            return 0.0d;
        }
        double mostSaturated = 0.0d;
        for (String tag : tags) {
            mostSaturated = Math.max(mostSaturated, profile.recentTagShare(tag));
        }
        if (mostSaturated >= 0.80d) {
            return -0.16d;
        }
        if (mostSaturated >= 0.60d) {
            return -0.10d;
        }
        if (mostSaturated >= 0.40d) {
            return -0.05d;
        }
        return tags.isEmpty() ? 0.0d : 0.03d;
    }

    private static double themeAffinity(Set<String> tags, String themeFocus) {
        String focus = MixDiversityProfile.normalizeTheme(themeFocus);
        if (focus.isBlank() || tags == null || tags.isEmpty()) {
            return 0.0d;
        }
        for (String tag : tags) {
            String normalized = MixDiversityProfile.normalizeTheme(tag);
            if (normalized.equals(focus)) {
                return 1.0d;
            }
            if (!normalized.isBlank() && (normalized.contains(focus) || focus.contains(normalized))) {
                return 0.75d;
            }
        }
        return 0.0d;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Decision(
            boolean rejected,
            double contribution,
            double artistPenalty,
            double tagPenalty,
            double themeAffinity,
            String themeFocus) {

        public static Decision neutral() {
            return new Decision(false, 0.0d, 0.0d, 0.0d, 0.0d, "");
        }
    }
}
