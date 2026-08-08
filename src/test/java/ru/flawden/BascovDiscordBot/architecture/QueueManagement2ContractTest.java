package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueManagement2ContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void slashCatalogPublishesQueueManagerWithoutRetiringLegacyCommands() throws IOException {
        String catalog = read("interactions/ModernCommandCatalog.java");

        assertTrue(catalog.contains("Commands.slash(\"queue-manage\""));
        assertTrue(catalog.contains("new SubcommandData(\"stats\""));
        assertTrue(catalog.contains("new SubcommandData(\"remove-range\""));
        assertTrue(catalog.contains("new SubcommandData(\"dedupe\""));
        assertTrue(catalog.contains("new SubcommandData(\"remove-mine\""));
        assertTrue(catalog.contains("Commands.slash(\"remove\""));
        assertTrue(catalog.contains("Commands.slash(\"move\""));
        assertTrue(catalog.contains("Commands.slash(\"clear\""));
    }

    @Test
    void schedulerProvidesRevisionGuardAndAtomicBatchMutations() throws IOException {
        String scheduler = read("lavaplayer/TrackScheduler.java");

        assertTrue(scheduler.contains("long queueRevision"));
        assertTrue(scheduler.contains("QueueSnapshot queueSnapshot()"));
        assertTrue(scheduler.contains("QueueMutationStatus.STALE_REVISION"));
        assertTrue(scheduler.contains("removeRange("));
        assertTrue(scheduler.contains("deduplicateQueue("));
        assertTrue(scheduler.contains("removeRequester("));
        assertTrue(scheduler.contains("synchronized (mutationLock)"));
    }

    @Test
    void queueUiExposesRevisionStatsAndBatchHint() throws IOException {
        String embeds = read("commands/music/MusicEmbeds.java");
        String interactions = read("interactions/ModernInteractions.java");

        assertTrue(embeds.contains("Ревизия очереди"));
        assertTrue(embeds.contains("Дубликатов"));
        assertTrue(embeds.contains("queueStats(GuildMusicManager"));
        assertTrue(interactions.contains("case \"queue-manage\""));
        assertTrue(interactions.contains("Обнови `/queue` и повтори команду"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
