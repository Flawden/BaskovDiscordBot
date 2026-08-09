package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalListeningDiscoveryContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void personalHistoryIsPersistentRequesterScopedAndBounded() throws IOException {
        String repository = source("library/FileMusicLibraryRepository.java");
        String api = source("library/MusicLibraryRepository.java");

        assertTrue(api.contains("MAX_PERSONAL_HISTORY_PER_USER = 200"));
        assertTrue(api.contains("personalHistory(long guildId, long userId)"));
        assertTrue(repository.contains("case \"U\" -> loadPersonalHistory"));
        assertTrue(repository.contains("track.requesterUserId() > 0L"));
        assertTrue(repository.contains("previous.personalHistory()"));
    }

    @Test
    void slashUxKeepsServerHistoryCompatibleAndAddsPersonalDiscovery() throws IOException {
        String catalog = source("interactions/ModernCommandCatalog.java");
        String interactions = source("interactions/ModernInteractions.java");

        assertTrue(catalog.contains("historyScopeOption()"));
        assertTrue(catalog.contains("new SubcommandData(\"profile\""));
        assertTrue(catalog.contains("new SubcommandData(\"for-me\""));
        assertTrue(interactions.contains("musicLibraryRepository.personalHistory("));
        assertTrue(interactions.contains("PersonalListeningInsights.discoverySeed("));
        assertTrue(interactions.contains("startInteractiveSearch(event"));
    }

    @Test
    void autocompletePrioritizesPersonalHistoryBeforeGuildHistoryWithoutNetworking() throws IOException {
        String interactions = source("interactions/ModernInteractions.java");
        String suggestions = source("interactions/DiscoverySuggestions.java");

        assertTrue(interactions.contains("personalHistory = musicLibraryRepository.personalHistory"));
        int personal = suggestions.indexOf("addTracks(ordered, personalHistory, needle)");
        int guild = suggestions.indexOf("addTracks(ordered, history, needle)");
        assertTrue(personal >= 0 && guild > personal);
        assertTrue(!suggestions.contains("http://") && !suggestions.contains("https://"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
