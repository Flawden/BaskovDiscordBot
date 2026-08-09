package ru.flawden.BascovDiscordBot.lavaplayer;

import ru.flawden.BascovDiscordBot.recommendation.RadioStrategy;

/**
 * Безопасный read-only снимок ephemeral smart-radio режима guild.
 */
public record RadioSnapshot(
        boolean enabled,
        RadioMode mode,
        RadioStrategy strategy,
        long ownerUserId,
        String ownerDisplayName,
        long generatedTracks,
        int consecutiveFailures,
        boolean refillInProgress,
        String provider,
        String lastSeed,
        String lastTrack,
        String lastReason) {

    public RadioSnapshot {
        mode = mode == null ? RadioMode.PERSONAL : mode;
        strategy = strategy == null ? RadioStrategy.FAMILIAR : strategy;
        ownerDisplayName = ownerDisplayName == null || ownerDisplayName.isBlank()
                ? "Неизвестно"
                : ownerDisplayName.trim();
        provider = provider == null || provider.isBlank() ? "local" : provider.trim();
        lastSeed = lastSeed == null ? "—" : lastSeed;
        lastTrack = lastTrack == null ? "—" : lastTrack;
        lastReason = lastReason == null || lastReason.isBlank() ? "—" : lastReason.trim();
    }

    public static RadioSnapshot disabled() {
        return new RadioSnapshot(
                false,
                RadioMode.PERSONAL,
                RadioStrategy.FAMILIAR,
                0L,
                "—",
                0L,
                0,
                false,
                "local",
                "—",
                "—",
                "—");
    }
}
