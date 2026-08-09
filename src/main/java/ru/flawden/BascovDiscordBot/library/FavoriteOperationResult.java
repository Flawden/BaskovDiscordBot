package ru.flawden.BascovDiscordBot.library;

/**
 * Транспорт-независимый результат изменения личного избранного пользователя.
 */
public record FavoriteOperationResult(
        Status status,
        StoredTrack track,
        int affectedTracks) {

    public enum Status {
        ADDED,
        ALREADY_EXISTS,
        REMOVED,
        CLEARED,
        NOT_FOUND,
        LIMIT_REACHED,
        UNREPLAYABLE_TRACK
    }

    public static FavoriteOperationResult of(Status status, StoredTrack track) {
        return new FavoriteOperationResult(status, track, track == null ? 0 : 1);
    }

    public static FavoriteOperationResult of(Status status, StoredTrack track, int affectedTracks) {
        return new FavoriteOperationResult(status, track, Math.max(0, affectedTracks));
    }
}
