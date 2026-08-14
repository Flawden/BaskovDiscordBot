package ru.flawden.BascovDiscordBot.product;

import java.util.List;

/** Explicitly describes which parts of the product API are safe to expose. */
public record ProductCapabilities(
        String apiVersion,
        String mode,
        boolean authenticationRequiredForReads,
        boolean mutationsEnabled,
        boolean authenticationRequiredForMutations,
        List<String> resources) {

    public ProductCapabilities {
        apiVersion = apiVersion == null || apiVersion.isBlank() ? "v1" : apiVersion.trim();
        mode = mode == null || mode.isBlank() ? "AUTHENTICATED_READ" : mode.trim();
        resources = List.copyOf(resources == null ? List.of() : resources);
    }

    public static ProductCapabilities authenticatedRead() {
        return authenticatedLibraryWrite();
    }

    /** v1.39 adds read-only Smart Autoplay continuation while keeping Discord player/voice mutations unavailable. */
    public static ProductCapabilities authenticatedLibraryWrite() {
        return new ProductCapabilities(
                "v1",
                "AUTHENTICATED_READ_LIBRARY_WRITE",
                true,
                true,
                true,
                List.of("auth", "me", "devices", "guilds", "home", "mixes", "search", "autoplay", "player", "library", "favorites", "playlists", "taste", "playback", "capabilities"));
    }
}
