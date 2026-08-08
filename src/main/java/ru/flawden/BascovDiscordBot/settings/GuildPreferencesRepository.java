package ru.flawden.BascovDiscordBot.settings;

import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;

import java.util.List;

/**
 * Долговременное хранилище настроек Discord-серверов.
 */
public interface GuildPreferencesRepository {

    GuildPreferences get(long guildId);

    GuildPreferences saveVolume(long guildId, int volume);

    GuildPreferences saveRepeatMode(long guildId, RepeatMode repeatMode);

    GuildPreferences saveAccessMode(long guildId, PlaybackAccessMode accessMode);

    GuildPreferences saveRequestAccessMode(long guildId, RequestAccessMode accessMode);

    GuildPreferences saveDjRoleId(long guildId, long roleId);

    GuildPreferences saveManagerRoleId(long guildId, long roleId);

    GuildPreferences saveMusicChannelId(long guildId, long channelId);

    GuildPreferences saveVoteSkipPercent(long guildId, int percent);

    GuildPreferences replace(long guildId, GuildPreferences preferences);

    GuildPreferences reset(long guildId);

    void recordAudit(long guildId, long actorUserId, String action);

    List<GuildSettingsAuditEntry> recentAudit(long guildId);
}
