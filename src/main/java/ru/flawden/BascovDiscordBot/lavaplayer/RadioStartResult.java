package ru.flawden.BascovDiscordBot.lavaplayer;

/**
 * Результат включения smart radio без привязки к Discord transport.
 */
public record RadioStartResult(Status status, RadioSnapshot snapshot) {

    public enum Status {
        STARTED,
        UPDATED,
        NO_SEEDS
    }

    public RadioStartResult {
        snapshot = snapshot == null ? RadioSnapshot.disabled() : snapshot;
    }
}
