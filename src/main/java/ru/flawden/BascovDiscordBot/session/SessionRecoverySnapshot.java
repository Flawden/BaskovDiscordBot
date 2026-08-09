package ru.flawden.BascovDiscordBot.session;

/**
 * Агрегированное состояние восстановления для /status.
 */
public record SessionRecoverySnapshot(
        int persistedSessions,
        int recoveriesInProgress,
        long transportAttempts,
        long transportSuccesses,
        long transportFailures,
        long startupRestoreSuccesses,
        long startupRestoreFailures,
        long startupHistoryTracksRestored,
        long startupHistoryTrackFailures,
        String lastEvent) {
}
