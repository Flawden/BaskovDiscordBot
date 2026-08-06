package ru.flawden.BascovDiscordBot.settings;

import java.util.Locale;

/**
 * Кто может напрямую управлять активной музыкальной сессией.
 */
public enum PlaybackAccessMode {
    OPEN("Открытый"),
    DJ_ONLY("Только DJ"),
    VOTE_SKIP("DJ + голосование за пропуск");

    private final String label;

    PlaybackAccessMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static PlaybackAccessMode parse(String value) {
        if (value == null || value.isBlank()) {
            return OPEN;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "open" -> OPEN;
            case "dj", "dj_only", "dj-only" -> DJ_ONLY;
            case "vote", "vote_skip", "vote-skip" -> VOTE_SKIP;
            default -> throw new IllegalArgumentException("Unknown playback access mode: " + value);
        };
    }
}
