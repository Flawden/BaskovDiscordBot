package ru.flawden.BascovDiscordBot.library;

import java.util.List;
import java.util.Optional;

/**
 * Долговременные серверные плейлисты и история воспроизведения.
 */
public interface MusicLibraryRepository {

    int MAX_PLAYLISTS_PER_GUILD = 20;
    int MAX_TRACKS_PER_PLAYLIST = 50;
    int MAX_HISTORY_PER_GUILD = 50;

    List<StoredPlaylist> playlists(long guildId);

    Optional<StoredPlaylist> playlist(long guildId, String name);

    PlaylistOperationResult createPlaylist(long guildId, long ownerUserId, String name);

    PlaylistOperationResult addTrack(
            long guildId,
            String name,
            long actorUserId,
            boolean administrator,
            StoredTrack track);

    PlaylistOperationResult removeTrack(
            long guildId,
            String name,
            long actorUserId,
            boolean administrator,
            int oneBasedPosition);

    PlaylistOperationResult deletePlaylist(
            long guildId,
            String name,
            long actorUserId,
            boolean administrator);

    List<StoredTrack> history(long guildId);

    void recordHistory(long guildId, StoredTrack track);
}
