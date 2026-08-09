package ru.flawden.BascovDiscordBot.session;

import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;

/**
 * Guild-scoped recovery state exposed to Discord diagnostics.
 */
public record SessionRecoveryDetails(
        State state,
        long voiceChannelId,
        long capturedAtEpochMillis,
        boolean paused,
        int volume,
        RepeatMode repeatMode,
        int savedTracks,
        int savedHistoryTracks,
        long resumePositionMillis,
        String lastEvent) {

    public enum State {
        NONE,
        SAVED,
        RESTORING,
        ACTIVE
    }

    public static SessionRecoveryDetails none(String lastEvent) {
        return new SessionRecoveryDetails(
                State.NONE, 0L, 0L, false, 0, RepeatMode.OFF, 0, 0, 0L, lastEvent);
    }
}
