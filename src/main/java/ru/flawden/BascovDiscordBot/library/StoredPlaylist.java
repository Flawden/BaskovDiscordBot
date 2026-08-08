package ru.flawden.BascovDiscordBot.library;

import java.util.ArrayList;
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

    public StoredPlaylist withName(String newName) {
        return new StoredPlaylist(newName, ownerUserId, createdAtEpochMillis, tracks);
    }

    public StoredPlaylist withAddedTrack(StoredTrack track) {
        List<StoredTrack> updated = new ArrayList<>(tracks);
        updated.add(track);
        return new StoredPlaylist(name, ownerUserId, createdAtEpochMillis, updated);
    }

    public StoredPlaylist withAddedTracks(List<StoredTrack> additions) {
        List<StoredTrack> updated = new ArrayList<>(tracks);
        updated.addAll(additions);
        return new StoredPlaylist(name, ownerUserId, createdAtEpochMillis, updated);
    }

    public StoredPlaylist withTracks(List<StoredTrack> updatedTracks) {
        return new StoredPlaylist(name, ownerUserId, createdAtEpochMillis, updatedTracks);
    }

    public StoredPlaylist withoutTrack(int zeroBasedIndex) {
        List<StoredTrack> updated = new ArrayList<>(tracks);
        updated.remove(zeroBasedIndex);
        return new StoredPlaylist(name, ownerUserId, createdAtEpochMillis, updated);
    }
}
