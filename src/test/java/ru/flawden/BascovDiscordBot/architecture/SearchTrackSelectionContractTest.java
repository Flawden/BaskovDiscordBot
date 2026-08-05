package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchTrackSelectionContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void slashCatalogPublishesInteractiveSearch() throws IOException {
        String catalog = source("interactions/ModernCommandCatalog.java");
        String interactions = source("interactions/ModernInteractions.java");

        assertTrue(catalog.contains("Commands.slash(\"search\""));
        assertTrue(interactions.contains("case \"search\" -> search(event)"));
        assertTrue(interactions.contains("deferReply(true)"));
        assertTrue(interactions.contains("MusicControls.searchRows"));
    }

    @Test
    void searchResultsAreOwnerBoundExpiringAndSingleUse() throws IOException {
        String store = source("interactions/SearchSelectionStore.java");

        assertTrue(store.contains("Duration.ofMinutes(5)"));
        assertTrue(store.contains("session.guildId() != guildId || session.userId() != userId"));
        assertTrue(store.contains("sessions.remove(token, session)"));
        assertTrue(store.contains("MAX_CANDIDATES = 5"));
    }

    @Test
    void selectionQueuesAlreadyLoadedTrackWithoutSecondProviderLookup() throws IOException {
        String player = source("lavaplayer/PlayerManager.java");
        String interactions = source("interactions/ModernInteractions.java");

        assertTrue(player.contains("public void search("));
        assertTrue(player.contains("public void queueLoadedTrack("));
        assertTrue(interactions.contains("playerManager.queueLoadedTrack("));
        assertTrue(interactions.contains("claim.track()"));
    }

    @Test
    void searchButtonsUseJdaSixCollectionOverload() throws IOException {
        String controls = source("interactions/MusicControls.java");

        assertTrue(controls.contains("ActionRow.of(choices)"));
        assertTrue(!controls.contains("choices.toArray(Button[]::new)"));
    }

    @Test
    void searchAutocompleteSharesRecentHistoryWithPlay() throws IOException {
        String interactions = source("interactions/ModernInteractions.java");

        assertTrue(interactions.contains("\"play\".equals(event.getName()) || \"search\".equals(event.getName())"));
        assertTrue(interactions.contains("searchHistory.remember"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
