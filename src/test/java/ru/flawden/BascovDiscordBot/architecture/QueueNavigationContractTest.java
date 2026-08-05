package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueNavigationContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void slashCatalogPublishesOptionalQueuePage() throws IOException {
        String catalog = read("interactions/ModernCommandCatalog.java");

        assertTrue(catalog.contains("Commands.slash(\"queue\""));
        assertTrue(catalog.contains("\"page\""));
        assertTrue(catalog.contains("Страница очереди, начиная с 1"));
    }

    @Test
    void dynamicQueueButtonsStayReadOnly() throws IOException {
        String interactions = read("interactions/ModernInteractions.java");

        int navigation = interactions.indexOf("OptionalInt queuePage");
        int controlPolicy = interactions.indexOf("MusicControlPolicy.Decision decision", navigation);
        assertTrue(navigation > 0);
        assertTrue(controlPolicy > navigation);
        assertTrue(interactions.contains("event.editMessageEmbeds(view.embed())"));
    }

    @Test
    void queueEmbedKeepsGlobalPositionsEtaAndPageFooter() throws IOException {
        String embeds = read("commands/music/MusicEmbeds.java");

        assertTrue(embeds.contains("int globalPosition = page.firstPosition() + index"));
        assertTrue(embeds.contains("через `"));
        assertTrue(embeds.contains("Номера подходят для /remove и /move"));
        assertTrue(embeds.contains("progressBar(position, duration"));
    }

    @Test
    void legacyQueueUsesTheSamePaginationControls() throws IOException {
        String legacy = read("commands/music/TrackListEvent.java");

        assertTrue(legacy.contains("MusicEmbeds.queueView(musicManager, 1)"));
        assertTrue(legacy.contains("MusicControls.queueRows(view.page(), view.totalPages())"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
