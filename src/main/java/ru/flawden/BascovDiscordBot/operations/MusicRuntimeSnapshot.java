package ru.flawden.BascovDiscordBot.operations;

/**
 * Компактный снимок состояния музыкального движка без раскрытия данных пользователей.
 */
public record MusicRuntimeSnapshot(
        int activeSessions,
        int playingSessions,
        int queuedTracks) {
}
