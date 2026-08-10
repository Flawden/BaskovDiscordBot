package ru.flawden.BascovDiscordBot.recommendation;

import java.util.EnumMap;
import java.util.Map;

/**
 * Read-only online-learning profile reconstructed from durable recommendation feedback.
 */
public record ContextualBanditProfile(
        Map<RadioStrategy, Map<ExplorationArm, ArmStats>> strategies) {

    public ContextualBanditProfile {
        strategies = immutable(strategies);
    }

    public static ContextualBanditProfile empty() {
        return new ContextualBanditProfile(Map.of());
    }

    public ArmStats stats(RadioStrategy strategy, ExplorationArm arm) {
        RadioStrategy safeStrategy = strategy == null ? RadioStrategy.SIMILAR : strategy;
        ExplorationArm safeArm = arm == null ? ExplorationArm.BALANCED : arm;
        return strategies.getOrDefault(safeStrategy, Map.of()).getOrDefault(safeArm, ArmStats.empty());
    }

    public int totalSamples(RadioStrategy strategy) {
        return strategies.getOrDefault(strategy == null ? RadioStrategy.SIMILAR : strategy, Map.of())
                .values()
                .stream()
                .mapToInt(ArmStats::samples)
                .sum();
    }

    public double confidence(RadioStrategy strategy) {
        return Math.min(1.0d, totalSamples(strategy) / 12.0d);
    }

    public int totalSamples() {
        return strategies.values().stream()
                .flatMap(values -> values.values().stream())
                .mapToInt(ArmStats::samples)
                .sum();
    }

    private static Map<RadioStrategy, Map<ExplorationArm, ArmStats>> immutable(
            Map<RadioStrategy, Map<ExplorationArm, ArmStats>> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        EnumMap<RadioStrategy, Map<ExplorationArm, ArmStats>> outer = new EnumMap<>(RadioStrategy.class);
        input.forEach((strategy, arms) -> {
            if (strategy == null || arms == null || arms.isEmpty()) {
                return;
            }
            EnumMap<ExplorationArm, ArmStats> inner = new EnumMap<>(ExplorationArm.class);
            arms.forEach((arm, stats) -> {
                if (arm != null && stats != null) {
                    inner.put(arm, stats);
                }
            });
            outer.put(strategy, Map.copyOf(inner));
        });
        return Map.copyOf(outer);
    }

    public record ArmStats(int samples, double rewardSum) {
        public ArmStats {
            samples = Math.max(0, samples);
            rewardSum = Double.isFinite(rewardSum) ? rewardSum : 0.0d;
        }

        public static ArmStats empty() {
            return new ArmStats(0, 0.0d);
        }

        public double meanReward() {
            return samples == 0 ? 0.0d : rewardSum / samples;
        }
    }
}
