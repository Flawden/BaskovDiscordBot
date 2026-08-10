package ru.flawden.BascovDiscordBot.recommendation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight UCB-style online exploration policy. It learns which similarity-risk
 * bucket tends to produce better implicit feedback for each radio strategy.
 */
public final class ContextualBanditModel {

    private ContextualBanditModel() {
    }

    public static ContextualBanditProfile build(List<RecommendationFeedbackEntry> history) {
        if (history == null || history.isEmpty()) {
            return ContextualBanditProfile.empty();
        }
        EnumMap<RadioStrategy, EnumMap<ExplorationArm, MutableArm>> mutable = new EnumMap<>(RadioStrategy.class);
        for (RecommendationFeedbackEntry entry : history) {
            if (entry == null || entry.lastOutcome() == RecommendationOutcome.PENDING) {
                continue;
            }
            RadioStrategy strategy = entry.strategy() == null ? RadioStrategy.SIMILAR : entry.strategy();
            ExplorationArm arm = ExplorationArm.fromSimilarity(entry.similarity());
            MutableArm stats = mutable
                    .computeIfAbsent(strategy, ignored -> new EnumMap<>(ExplorationArm.class))
                    .computeIfAbsent(arm, ignored -> new MutableArm());
            stats.samples++;
            stats.rewardSum += normalizedReward(entry.signalScore());
        }

        EnumMap<RadioStrategy, Map<ExplorationArm, ContextualBanditProfile.ArmStats>> result =
                new EnumMap<>(RadioStrategy.class);
        mutable.forEach((strategy, arms) -> {
            EnumMap<ExplorationArm, ContextualBanditProfile.ArmStats> frozen = new EnumMap<>(ExplorationArm.class);
            arms.forEach((arm, stats) -> frozen.put(
                    arm,
                    new ContextualBanditProfile.ArmStats(stats.samples, stats.rewardSum)));
            result.put(strategy, Map.copyOf(frozen));
        });
        return new ContextualBanditProfile(Map.copyOf(result));
    }

    public static BanditDecision decide(
            RecommendationCandidate candidate,
            RadioStrategy strategy,
            ContextualBanditProfile profile,
            SessionTasteProfile sessionTaste) {
        RadioStrategy safeStrategy = strategy == null ? RadioStrategy.SIMILAR : strategy;
        ContextualBanditProfile safeProfile = profile == null ? ContextualBanditProfile.empty() : profile;
        SessionTasteProfile safeSession = sessionTaste == null ? SessionTasteProfile.empty(0L) : sessionTaste;
        ExplorationArm arm = ExplorationArm.fromSimilarity(candidate == null ? 0.0d : candidate.similarity());
        ContextualBanditProfile.ArmStats stats = safeProfile.stats(safeStrategy, arm);
        int total = safeProfile.totalSamples(safeStrategy);
        double confidence = safeProfile.confidence(safeStrategy);
        double meanReward = stats.meanReward();
        double uncertainty = Math.min(1.0d, Math.sqrt(Math.log(total + 2.0d) / (stats.samples() + 1.0d)));

        double learned = meanReward * (0.09d * confidence);
        double uncertaintyBonus = uncertainty * uncertaintyWeight(safeStrategy, arm) * (1.0d - 0.35d * confidence);
        double strategyPrior = strategyPrior(safeStrategy, arm);
        double sessionAdjustment = sessionAdjustment(safeSession, arm);
        double contribution = clamp(learned + uncertaintyBonus + strategyPrior + sessionAdjustment, -0.12d, 0.12d);

        return new BanditDecision(
                arm,
                stats.samples(),
                meanReward,
                uncertainty,
                confidence,
                contribution);
    }

    public static ExplorationArm preferredArm(
            ContextualBanditProfile profile,
            RadioStrategy strategy,
            SessionTasteProfile sessionTaste) {
        ExplorationArm best = ExplorationArm.BALANCED;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (ExplorationArm arm : ExplorationArm.values()) {
            double similarity = switch (arm) {
                case SAFE -> 0.90d;
                case BALANCED -> 0.72d;
                case BOLD -> 0.45d;
            };
            BanditDecision decision = decide(
                    new RecommendationCandidate("arm", arm.label(), similarity, "bandit", "preview"),
                    strategy,
                    profile,
                    sessionTaste);
            if (decision.contribution() > bestValue) {
                best = arm;
                bestValue = decision.contribution();
            }
        }
        return best;
    }

    static double normalizedReward(double signalScore) {
        if (!Double.isFinite(signalScore) || signalScore == 0.0d) {
            return 0.0d;
        }
        return Math.tanh(signalScore / 3.0d);
    }

    private static double uncertaintyWeight(RadioStrategy strategy, ExplorationArm arm) {
        return switch (strategy) {
            case FAMILIAR -> arm == ExplorationArm.SAFE ? 0.010d : 0.004d;
            case SIMILAR -> arm == ExplorationArm.BALANCED ? 0.020d : 0.014d;
            case DISCOVERY -> arm == ExplorationArm.BOLD ? 0.028d : 0.018d;
        };
    }

    private static double strategyPrior(RadioStrategy strategy, ExplorationArm arm) {
        return switch (strategy) {
            case FAMILIAR -> switch (arm) {
                case SAFE -> 0.020d;
                case BALANCED -> 0.000d;
                case BOLD -> -0.025d;
            };
            case SIMILAR -> switch (arm) {
                case SAFE -> 0.004d;
                case BALANCED -> 0.016d;
                case BOLD -> 0.002d;
            };
            case DISCOVERY -> switch (arm) {
                case SAFE -> -0.012d;
                case BALANCED -> 0.010d;
                case BOLD -> 0.024d;
            };
        };
    }

    private static double sessionAdjustment(SessionTasteProfile session, ExplorationArm arm) {
        if (session.evidenceSignals() == 0 || Math.abs(session.momentum()) < 0.15d) {
            return 0.0d;
        }
        double confidence = session.confidence();
        if (session.momentum() < 0.0d) {
            double pressure = -session.momentum() * confidence;
            return switch (arm) {
                case SAFE -> -0.020d * pressure;
                case BALANCED -> 0.008d * pressure;
                case BOLD -> 0.030d * pressure;
            };
        }
        double pressure = session.momentum() * confidence;
        return switch (arm) {
            case SAFE -> 0.018d * pressure;
            case BALANCED -> 0.006d * pressure;
            case BOLD -> -0.012d * pressure;
        };
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static final class MutableArm {
        private int samples;
        private double rewardSum;
    }

    public record BanditDecision(
            ExplorationArm arm,
            int samples,
            double meanReward,
            double uncertainty,
            double confidence,
            double contribution) {
    }
}
