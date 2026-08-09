package ru.flawden.BascovDiscordBot.lavaplayer;

/**
 * Безопасный read-only снимок ephemeral smart-radio режима guild.
 */
public record RadioSnapshot(
        boolean enabled,
        RadioMode mode,
        long ownerUserId,
        String ownerDisplayName,
        long generatedTracks,
        int consecutiveFailures,
        boolean refillInProgress,
        String lastSeed,
        String lastTrack) {

    public RadioSnapshot {
        mode = mode == null ? RadioMode.PERSONAL : mode;
        ownerDisplayName = ownerDisplayName == null || ownerDisplayName.isBlank()
                ? "Неизвестно"
                : ownerDisplayName.trim();
        lastSeed = lastSeed == null ? "—" : lastSeed;
        lastTrack = lastTrack == null ? "—" : lastTrack;
    }

    public static RadioSnapshot disabled() {
        return new RadioSnapshot(false, RadioMode.PERSONAL, 0L, "—", 0L, 0, false, "—", "—");
    }
}
