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
        return authenticatedPlaylistWrite();
    }

    /** v1.36 keeps playback/guild control read-only while enabling owner-scoped shared-playlist writes. */
    public static ProductCapabilities authenticatedPlaylistWrite() {
        return new ProductCapabilities(
                "v1",
                "AUTHENTICATED_READ_PLAYLIST_WRITE",
                true,
                true,
                true,
                List.of("auth", "me", "devices", "guilds", "home", "mixes", "search", "player", "library", "playlists", "playback", "capabilities"));
    }
}
