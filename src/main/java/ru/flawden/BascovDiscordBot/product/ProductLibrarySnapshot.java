package ru.flawden.BascovDiscordBot.product;

import ru.flawden.BascovDiscordBot.home.HomeSnapshot;

import java.util.List;

/** Small library summary suitable for Home, Discord and the first external API contract. */
public record ProductLibrarySnapshot(
        long guildId,
        long userId,
        int favorites,
        int personalHistory,
        List<HomeSnapshot.TrackPreview> recent) {

    public ProductLibrarySnapshot {
        if (guildId <= 0L || userId <= 0L) {
            throw new IllegalArgumentException("guildId and userId must be positive");
        }
        favorites = Math.max(0, favorites);
        personalHistory = Math.max(0, personalHistory);
        recent = List.copyOf(recent == null ? List.of() : recent);
    }
}
