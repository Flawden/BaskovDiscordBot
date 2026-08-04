package ru.flawden.BascovDiscordBot.settings;

import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;

import java.util.Objects;

/**
 * Сохраняемые музыкальные предпочтения одной Discord-гильдии.
 */
public record GuildPreferences(int volume, RepeatMode repeatMode) {

    public GuildPreferences {
        if (volume < 0) {
            throw new IllegalArgumentException("volume cannot be negative");
        }
        Objects.requireNonNull(repeatMode, "repeatMode");
    }
}
