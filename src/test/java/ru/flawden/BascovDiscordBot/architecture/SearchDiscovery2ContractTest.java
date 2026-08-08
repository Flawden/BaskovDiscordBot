package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchDiscovery2ContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void catalogPublishesDiscoveryModesAlongsideInteractiveSearch() throws IOException {
        String catalog = read("interactions/ModernCommandCatalog.java");

        assertTrue(catalog.contains("Commands.slash(\"discover\""));
        assertTrue(catalog.contains("new SubcommandData(\"recent\""));
        assertTrue(catalog.contains("new SubcommandData(\"again\""));
        assertTrue(catalog.contains("new SubcommandData(\"related\""));
        assertTrue(catalog.contains("new SubcommandData(\"history\""));
    }

    @Test
    void discoveryReusesInteractiveSearchWithoutBypassingSafety() throws IOException {
        String interactions = read("interactions/ModernInteractions.java");

        assertTrue(interactions.contains("private void startInteractiveSearch("));
        assertTrue(interactions.contains("queryResolver.resolve(safeQuery)"));
        assertTrue(interactions.contains("MediaQueryResolver.YOUTUBE_SEARCH_PREFIX"));
        assertTrue(interactions.contains("SearchSelectionStore.MAX_CANDIDATES"));
        assertTrue(interactions.contains("DiscoverySuggestions.discoveryQuery("));
    }

    @Test
    void autocompleteMergesMemoryWithPersistentLibraryWithoutNetworkCalls() throws IOException {
        String interactions = read("interactions/ModernInteractions.java");
        String suggestions = read("interactions/DiscoverySuggestions.java");

        assertTrue(interactions.contains("DiscoverySuggestions.suggest("));
        assertTrue(interactions.contains("musicLibraryRepository.history(guildId)"));
        assertTrue(interactions.contains("musicLibraryRepository.playlists(guildId)"));
        assertTrue(suggestions.contains("MAX_SUGGESTIONS = 25"));
        assertTrue(suggestions.contains("LinkedHashMap"));
        assertTrue(!suggestions.contains("http://") && !suggestions.contains("https://"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
