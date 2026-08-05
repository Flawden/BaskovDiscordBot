package ru.flawden.BascovDiscordBot.library;

/**
 * Транспорт-независимый результат изменения серверного плейлиста.
 */
public record PlaylistOperationResult(
        Status status,
        StoredPlaylist playlist,
        StoredTrack track) {

    public enum Status {
        CREATED,
        ADDED,
        REMOVED,
        DELETED,
        ALREADY_EXISTS,
        NOT_FOUND,
        FORBIDDEN,
        PLAYLIST_LIMIT_REACHED,
        TRACK_LIMIT_REACHED,
        INVALID_POSITION,
        UNREPLAYABLE_TRACK
    }

    public static PlaylistOperationResult of(Status status, StoredPlaylist playlist) {
        return new PlaylistOperationResult(status, playlist, null);
    }

    public static PlaylistOperationResult of(
            Status status,
            StoredPlaylist playlist,
            StoredTrack track) {
        return new PlaylistOperationResult(status, playlist, track);
    }
}
