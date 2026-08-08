package ru.flawden.BascovDiscordBot.settings;

import java.util.Locale;

/**
 * Кто может добавлять новые треки и запускать поисковые/playlist playback операции.
 */
public enum RequestAccessMode {
    OPEN("Все слушатели"),
    DJ_ONLY("Только DJ");

    private final String label;

    RequestAccessMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static RequestAccessMode parse(String value) {
        if (value == null || value.isBlank()) {
            return OPEN;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "open" -> OPEN;
            case "dj", "dj_only", "dj-only" -> DJ_ONLY;
            default -> throw new IllegalArgumentException("Unknown request access mode: " + value);
        };
    }
}
