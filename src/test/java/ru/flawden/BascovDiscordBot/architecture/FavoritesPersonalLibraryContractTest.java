package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FavoritesPersonalLibraryContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void slashCatalogAndInteractionsExposePersonalFavorites() throws IOException {
        String catalog = source("interactions/ModernCommandCatalog.java");
        String interactions = source("interactions/ModernInteractions.java");

        assertTrue(catalog.contains("Commands.slash(\"favorites\""));
        assertTrue(catalog.contains("new SubcommandData(\"play-all\""));
        assertTrue(catalog.contains("new SubcommandData(\"clear\""));
        assertTrue(interactions.contains("case \"favorites\" -> favorites(event)"));
        assertTrue(interactions.contains("ConfirmationStore.Action.CLEAR_FAVORITES"));
    }

    @Test
    void favoritesRemainPersonalPersistentAndReuseStoredTrackLoading() throws IOException {
        String repository = source("library/FileMusicLibraryRepository.java");
        String interactions = source("interactions/ModernInteractions.java");

        assertTrue(repository.contains("case \"F\" -> loadFavorite"));
        assertTrue(repository.contains("removeFavoriteByStableKey"));
        assertTrue(repository.contains("Map<Long, List<StoredTrack>> favorites"));
        assertTrue(interactions.contains("musicLibraryRepository.favorites("));
        assertTrue(interactions.contains("queueStoredTracks(event, favorites"));
    }

    @Test
    void autocompletePrioritizesPersonalFavoritesWithoutNetworkCalls() throws IOException {
        String interactions = source("interactions/ModernInteractions.java");
        String suggestions = source("interactions/DiscoverySuggestions.java");

        assertTrue(interactions.contains("favorites = musicLibraryRepository.favorites"));
        assertTrue(suggestions.contains("addTracks(ordered, favorites, needle)"));
        assertTrue(!suggestions.contains("http://") && !suggestions.contains("https://"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
