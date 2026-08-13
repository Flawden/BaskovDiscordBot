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
    int MAX_PERSONAL_HISTORY_PER_USER = 200;

    List<StoredPlaylist> playlists(long guildId);

    Optional<StoredPlaylist> playlist(long guildId, String name);

    PlaylistOperationResult createPlaylist(long guildId, long ownerUserId, String name);

    PlaylistOperationResult addTrack(
            long guildId,
            String name,
            long actorUserId,
            boolean administrator,
            StoredTrack track);

    PlaylistOperationResult addTracks(
            long guildId,
            String name,
            long actorUserId,
            boolean administrator,
            List<StoredTrack> tracks);

    PlaylistOperationResult renamePlaylist(
            long guildId,
            String name,
            String newName,
            long actorUserId,
            boolean administrator);

    PlaylistOperationResult copyPlaylist(
            long guildId,
            String sourceName,
            String newName,
            long actorUserId);

    PlaylistOperationResult moveTrack(
            long guildId,
            String name,
            long actorUserId,
            boolean administrator,
            int fromOneBasedPosition,
            int toOneBasedPosition);

    PlaylistOperationResult dedupePlaylist(
            long guildId,
            String name,
            long actorUserId,
            boolean administrator);

    List<PlaylistSearchHit> search(long guildId, String query);

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

    List<StoredTrack> personalHistory(long guildId, long userId);

    List<StoredTrack> favorites(long guildId, long userId);

    FavoriteOperationResult addFavorite(long guildId, long userId, StoredTrack track);

    FavoriteOperationResult removeFavorite(long guildId, long userId, int oneBasedPosition);

    Optional<StoredTrack> favoriteByStableKey(long guildId, long userId, String stableKey);

    FavoriteOperationResult removeFavoriteByStableKey(long guildId, long userId, String stableKey);

    FavoriteOperationResult clearFavorites(long guildId, long userId);

    List<FavoriteSearchHit> searchFavorites(long guildId, long userId, String query);
}
