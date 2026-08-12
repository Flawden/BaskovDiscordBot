package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductSearchApiContractTest {

    @Test
    void searchStaysBehindClientNeutralProductBoundary() throws Exception {
        String controller = read("product/api/ProductApiController.java");
        String service = read("product/MusicProductService.java");
        String adapter = read("product/RuntimeMusicProductReadAdapter.java");

        assertTrue(controller.contains("@GetMapping(\"/search\")"));
        assertTrue(controller.contains("access.requireGuild"));
        assertTrue(controller.contains("product.search"));
        assertFalse(controller.contains("PlayerManager"));
        assertFalse(service.contains("PlayerManager"));
        assertTrue(adapter.contains("playerManager.search"));
        assertTrue(adapter.contains("MediaQueryResolver.YOUTUBE_SEARCH_PREFIX"));
    }

    @Test
    void searchIsReadOnlyAndHasBoundedQueryContract() throws Exception {
        String controller = read("product/api/ProductApiController.java");
        String service = read("product/MusicProductService.java");
        String openApi = Files.readString(Path.of("docs/openapi/baskov-product-api-v1.yaml"));

        assertFalse(controller.contains("@PostMapping(\"/search\")"));
        assertTrue(service.contains("normalized.length() > 200"));
        assertTrue(service.contains("maxResults < 1 || maxResults > 10"));
        assertTrue(openApi.contains("operationId: searchTracks"));
        assertTrue(openApi.contains("maximum: 10"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/flawden/BascovDiscordBot").resolve(relative));
    }
}
