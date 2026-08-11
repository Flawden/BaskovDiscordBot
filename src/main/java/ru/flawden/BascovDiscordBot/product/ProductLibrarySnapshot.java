package ru.flawden.BascovDiscordBot.product;

import ru.flawden.BascovDiscordBot.home.HomeSnapshot;

import java.util.List;

/** Read-only personal library snapshot for external clients. */
public record ProductLibrarySnapshot(
        long guildId,
        long userId,
        int favorites,
        int personalHistory,
        List<HomeSnapshot.TrackPreview> recent,
        List<HomeSnapshot.TrackPreview> favoriteTracks,
        List<HomeSnapshot.TrackPreview> historyTracks) {

    public ProductLibrarySnapshot {
        if (guildId <= 0L || userId <= 0L) {
            throw new IllegalArgumentException("guildId and userId must be positive");
        }
        favorites = Math.max(0, favorites);
        personalHistory = Math.max(0, personalHistory);
        recent = List.copyOf(recent == null ? List.of() : recent);
        favoriteTracks = List.copyOf(favoriteTracks == null ? List.of() : favoriteTracks);
        historyTracks = List.copyOf(historyTracks == null ? List.of() : historyTracks);
    }
}
