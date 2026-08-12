package ru.flawden.BascovDiscordBot.product;

import ru.flawden.BascovDiscordBot.home.HomeSnapshot;

import java.util.List;

/** Client-neutral search result for mobile/web product surfaces. */
public record ProductSearchSnapshot(
        long guildId,
        long userId,
        String query,
        List<HomeSnapshot.TrackPreview> tracks) {

    public ProductSearchSnapshot {
        if (guildId <= 0L) {
            throw new IllegalArgumentException("guildId must be positive");
        }
        if (userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }
        query = query == null ? "" : query.trim();
        tracks = List.copyOf(tracks == null ? List.of() : tracks);
    }
}
