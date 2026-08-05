package ru.flawden.BascovDiscordBot.library;

import java.util.Locale;

/**
 * Единые правила имён серверных плейлистов.
 */
public final class PlaylistName {

    public static final int MAX_LENGTH = 40;

    private PlaylistName() {
    }

    public static String display(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Название плейлиста не задано");
        }
        String normalized = raw.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Название плейлиста не может быть пустым");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Название плейлиста не может быть длиннее " + MAX_LENGTH + " символов");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Название плейлиста содержит управляющие символы");
        }
        return normalized;
    }

    public static String key(String raw) {
        return display(raw).toLowerCase(Locale.ROOT);
    }
}
