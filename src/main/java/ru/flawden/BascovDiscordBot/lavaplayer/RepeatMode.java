package ru.flawden.BascovDiscordBot.lavaplayer;

import java.util.Locale;

/**
 * Режим повторения музыкальной сессии.
 */
public enum RepeatMode {
    OFF("Выключен"),
    TRACK("Текущий трек"),
    QUEUE("Вся очередь");

    private final String label;

    RepeatMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public RepeatMode next() {
        return switch (this) {
            case OFF -> TRACK;
            case TRACK -> QUEUE;
            case QUEUE -> OFF;
        };
    }

    public static RepeatMode parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Repeat mode is required");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "off" -> OFF;
            case "track" -> TRACK;
            case "queue" -> QUEUE;
            default -> throw new IllegalArgumentException("Unknown repeat mode: " + value);
        };
    }
}
