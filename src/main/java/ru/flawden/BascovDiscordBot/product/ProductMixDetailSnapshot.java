package ru.flawden.BascovDiscordBot.product;

import ru.flawden.BascovDiscordBot.home.HomeSnapshot;

import java.util.List;

/** Read-only details for a curated station. seedPreview is not a playback queue. */
public record ProductMixDetailSnapshot(
        long guildId,
        long userId,
        String stationSlug,
        String label,
        String description,
        boolean available,
        boolean daily,
        List<HomeSnapshot.TrackPreview> seedPreview) {

    public ProductMixDetailSnapshot {
        if (guildId <= 0L || userId <= 0L) {
            throw new IllegalArgumentException("guildId and userId must be positive");
        }
        stationSlug = stationSlug == null ? "" : stationSlug.trim();
        label = label == null ? "" : label.trim();
        description = description == null ? "" : description.trim();
        seedPreview = List.copyOf(seedPreview == null ? List.of() : seedPreview);
    }
}
