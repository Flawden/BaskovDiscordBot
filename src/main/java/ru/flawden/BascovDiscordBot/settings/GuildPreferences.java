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
        RequestAccessMode requestAccessMode,
        long djRoleId,
        long managerRoleId,
        long musicChannelId,
        int voteSkipPercent) {

    public static final int DEFAULT_VOTE_SKIP_PERCENT = 50;

    public GuildPreferences {
        if (volume < 0) {
            throw new IllegalArgumentException("volume cannot be negative");
        }
        Objects.requireNonNull(repeatMode, "repeatMode");
        Objects.requireNonNull(accessMode, "accessMode");
        Objects.requireNonNull(requestAccessMode, "requestAccessMode");
        if (djRoleId < 0 || managerRoleId < 0 || musicChannelId < 0) {
            throw new IllegalArgumentException("Discord ids cannot be negative");
        }
        if (voteSkipPercent < 25 || voteSkipPercent > 100) {
            throw new IllegalArgumentException("voteSkipPercent must be between 25 and 100");
        }
    }

    /**
     * Совместимый конструктор для настроек до v0.14.0.
     */
    public GuildPreferences(int volume, RepeatMode repeatMode) {
        this(
                volume,
                repeatMode,
                PlaybackAccessMode.OPEN,
                RequestAccessMode.OPEN,
                0L,
                0L,
                0L,
                DEFAULT_VOTE_SKIP_PERCENT);
    }

    /**
     * Совместимый конструктор для настроек v0.14.0-v1.4.0.
     */
    public GuildPreferences(
            int volume,
            RepeatMode repeatMode,
            PlaybackAccessMode accessMode,
            long djRoleId,
            int voteSkipPercent) {
        this(
                volume,
                repeatMode,
                accessMode,
                RequestAccessMode.OPEN,
                djRoleId,
                0L,
                0L,
                voteSkipPercent);
    }

    public boolean hasDjRole() {
        return djRoleId > 0;
    }

    public boolean hasManagerRole() {
        return managerRoleId > 0;
    }

    public boolean hasMusicChannel() {
        return musicChannelId > 0;
    }
}
