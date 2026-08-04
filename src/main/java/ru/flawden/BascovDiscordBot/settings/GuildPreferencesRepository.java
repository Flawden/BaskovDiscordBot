package ru.flawden.BascovDiscordBot.settings;

import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;

/**
 * Долговременное хранилище настроек Discord-серверов.
 */
public interface GuildPreferencesRepository {

    GuildPreferences get(long guildId);

    GuildPreferences saveVolume(long guildId, int volume);

    GuildPreferences saveRepeatMode(long guildId, RepeatMode repeatMode);

    GuildPreferences reset(long guildId);
}
