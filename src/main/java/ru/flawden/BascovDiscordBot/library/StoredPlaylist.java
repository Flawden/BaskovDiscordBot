package ru.flawden.BascovDiscordBot.library;

import java.util.List;

/**
 * Серверный плейлист с владельцем и неизменяемым порядком треков.
 */
public record StoredPlaylist(
        String name,
        long ownerUserId,
        long createdAtEpochMillis,
        List<StoredTrack> tracks) {

    public StoredPlaylist {
        name = PlaylistName.display(name);
        if (ownerUserId <= 0L) {
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
        if (createdAtEpochMillis <= 0L) {
            throw new IllegalArgumentException("createdAtEpochMillis must be positive");
        }
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }

    public String key() {
        return PlaylistName.key(name);
    }

    public StoredPlaylist withAddedTrack(StoredTrack track) {
        List<StoredTrack> updated = new java.util.ArrayList<>(tracks);
        updated.add(track);
        return new StoredPlaylist(name, ownerUserId, createdAtEpochMillis, updated);
    }

    public StoredPlaylist withoutTrack(int zeroBasedIndex) {
        List<StoredTrack> updated = new java.util.ArrayList<>(tracks);
        updated.remove(zeroBasedIndex);
        return new StoredPlaylist(name, ownerUserId, createdAtEpochMillis, updated);
    }
}
