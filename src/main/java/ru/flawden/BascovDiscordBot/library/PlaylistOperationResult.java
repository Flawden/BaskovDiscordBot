package ru.flawden.BascovDiscordBot.library;

/**
 * Транспорт-независимый результат изменения серверного плейлиста.
 */
public record PlaylistOperationResult(
        Status status,
        StoredPlaylist playlist,
        StoredTrack track,
        int affectedTracks) {

    public enum Status {
        CREATED,
        ADDED,
        BULK_ADDED,
        REMOVED,
        MOVED,
        DEDUPED,
        RENAMED,
        COPIED,
        DELETED,
        ALREADY_EXISTS,
        NOT_FOUND,
        FORBIDDEN,
        PLAYLIST_LIMIT_REACHED,
        TRACK_LIMIT_REACHED,
        INVALID_POSITION,
        UNREPLAYABLE_TRACK
    }

    public PlaylistOperationResult(Status status, StoredPlaylist playlist, StoredTrack track) {
        this(status, playlist, track, track == null ? 0 : 1);
    }

    public static PlaylistOperationResult of(Status status, StoredPlaylist playlist) {
        return new PlaylistOperationResult(status, playlist, null, 0);
    }

    public static PlaylistOperationResult of(
            Status status,
            StoredPlaylist playlist,
            StoredTrack track) {
        return new PlaylistOperationResult(status, playlist, track, track == null ? 0 : 1);
    }

    public static PlaylistOperationResult of(
            Status status,
            StoredPlaylist playlist,
            StoredTrack track,
            int affectedTracks) {
        return new PlaylistOperationResult(status, playlist, track, Math.max(0, affectedTracks));
    }
}
