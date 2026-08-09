package ru.flawden.BascovDiscordBot.lavaplayer;

/**
 * Источник локальных seed-треков для smart radio.
 */
public enum RadioMode {
    PERSONAL("Личное"),
    SERVER("Сервер");

    private final String label;

    RadioMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
