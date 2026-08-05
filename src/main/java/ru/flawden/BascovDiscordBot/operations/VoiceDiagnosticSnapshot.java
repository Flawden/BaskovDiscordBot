package ru.flawden.BascovDiscordBot.operations;

import java.time.Duration;

/**
 * Безопасный снимок voice-диагностики одной Discord-гильдии.
 */
public record VoiceDiagnosticSnapshot(
        String networkMode,
        String controlState,
        String voiceChannelId,
        boolean audioManagerConnected,
        boolean sessionActive,
        boolean playbackExpected,
        String currentTrack,
        long frameRequestCount,
        Duration lastFrameRequestAge,
        long connectionAttempts,
        long selfJoinEvents,
        long selfLeaveEvents,
        long trackExceptions,
        long cleanupEvents,
        long fallbackAttempts,
        long staleCallbacks,
        long watchdogWarnings,
        String lastVoiceEvent,
        String lastVoiceError,
        String lastSourceError,
        String lastRecoveryEvent,
        String lastStaleCallback,
        boolean watchdogEnforced) {
}
