package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductFavoriteApiContractTest {

    @Test
    void mobileFavoritesReuseDiscordPersistenceAndLinkedDiscordIdentity() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/product/ProductFavoriteService.java"));
        String controller = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/product/api/ProductApiController.java"));

        assertTrue(service.contains("MusicLibraryRepository"));
        assertTrue(service.contains("library.favorites"));
        assertTrue(service.contains("library.addFavorite"));
        assertTrue(service.contains("library.removeFavorite"));
        assertTrue(service.contains("library.removeFavoriteByStableKey"));
        assertTrue(service.contains("library.clearFavorites"));
        assertTrue(controller.contains("principal.discordUserId()"));
        assertFalse(controller.contains("MusicLibraryRepository"));
    }

    @Test
    void selectedFavoriteIsResolvedServerSideBeforePersistence() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/product/ProductFavoriteService.java"));
        String controller = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/product/api/ProductApiController.java"));

        assertTrue(service.contains("trackResolver.resolve"));
        assertTrue(controller.contains("FavoriteOperationResult.Status.ALREADY_EXISTS"));
        assertTrue(controller.contains("@GetMapping(\"/favorites\")"));
        assertTrue(controller.contains("@PostMapping(\"/favorites\")"));
        assertTrue(controller.contains("@DeleteMapping(\"/favorites/{position}\")"));
        assertTrue(controller.contains("@DeleteMapping(\"/favorites/by-key\")"));
        assertTrue(controller.contains("@GetMapping(\"/favorites/keys\")"));
        assertTrue(controller.contains("@GetMapping(\"/favorites/status\")"));
        assertTrue(controller.contains("@DeleteMapping(\"/favorites\")"));
    }
}
