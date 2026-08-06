package ru.flawden.BascovDiscordBot.settings;

import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;

import java.util.Objects;

/**
 * Сохраняемые музыкальные предпочтения одной Discord-гильдии.
 */
public record GuildPreferences(
        int volume,
        RepeatMode repeatMode,
        PlaybackAccessMode accessMode,
        long djRoleId,
        int voteSkipPercent) {

    public static final int DEFAULT_VOTE_SKIP_PERCENT = 50;

    public GuildPreferences {
        if (volume < 0) {
            throw new IllegalArgumentException("volume cannot be negative");
        }
        Objects.requireNonNull(repeatMode, "repeatMode");
        Objects.requireNonNull(accessMode, "accessMode");
        if (djRoleId < 0) {
            throw new IllegalArgumentException("djRoleId cannot be negative");
        }
        if (voteSkipPercent < 25 || voteSkipPercent > 100) {
            throw new IllegalArgumentException("voteSkipPercent must be between 25 and 100");
        }
    }

    /**
     * Совместимый конструктор для старых тестов и настроек до v0.14.0.
     */
    public GuildPreferences(int volume, RepeatMode repeatMode) {
        this(volume, repeatMode, PlaybackAccessMode.OPEN, 0L, DEFAULT_VOTE_SKIP_PERCENT);
    }

    public boolean hasDjRole() {
        return djRoleId > 0;
    }
}
