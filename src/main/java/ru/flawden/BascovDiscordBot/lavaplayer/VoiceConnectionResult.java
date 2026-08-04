package ru.flawden.BascovDiscordBot.lavaplayer;

/**
 * Итог ограниченной попытки подключения к голосовому каналу.
 */
public record VoiceConnectionResult(Status status, String details) {

    public boolean connected() {
        return status == Status.CONNECTED;
    }

    public enum Status {
        CONNECTED,
        TIMEOUT,
        COOLDOWN,
        BUSY,
        FAILED,
        SHUTTING_DOWN
    }
}
