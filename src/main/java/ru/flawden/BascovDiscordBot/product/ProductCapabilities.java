package ru.flawden.BascovDiscordBot.product;

import java.util.List;

/** Explicitly describes which parts of the first product API are safe to expose. */
public record ProductCapabilities(
        String apiVersion,
        String mode,
        boolean mutationsEnabled,
        boolean authenticationRequiredForMutations,
        List<String> resources) {

    public ProductCapabilities {
        apiVersion = apiVersion == null || apiVersion.isBlank() ? "v1" : apiVersion.trim();
        mode = mode == null || mode.isBlank() ? "READ_ONLY_PREVIEW" : mode.trim();
        resources = List.copyOf(resources == null ? List.of() : resources);
    }

    public static ProductCapabilities readOnlyPreview() {
        return new ProductCapabilities(
                "v1",
                "READ_ONLY_PREVIEW",
                false,
                true,
                List.of("home", "mixes", "player", "library", "capabilities"));
    }
}
