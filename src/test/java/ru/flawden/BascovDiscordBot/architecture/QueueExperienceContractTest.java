package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueExperienceContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void schedulerKeepsRequesterRepeatAndQueueMutationState() throws IOException {
        String scheduler = read("lavaplayer/TrackScheduler.java");

        assertTrue(scheduler.contains("BlockingQueue<TrackRequest>"));
        assertTrue(scheduler.contains("RepeatMode repeatMode"));
        assertTrue(scheduler.contains("removeAt("));
        assertTrue(scheduler.contains("move("));
        assertTrue(scheduler.contains("shuffleQueue("));
        assertTrue(scheduler.contains("estimatedWaitMillis("));
    }

    @Test
    void modernInterfacePublishesQueueManagementCommands() throws IOException {
        String catalog = read("interactions/ModernCommandCatalog.java");
        String interactions = read("interactions/ModernInteractions.java");

        for (String command : new String[]{"volume", "repeat", "shuffle", "remove", "move", "clear"}) {
            assertTrue(catalog.contains("Commands.slash(\"" + command + "\""), command);
            assertTrue(interactions.contains("case \"" + command + "\""), command);
        }
    }

    @Test
    void queueEmbedsExposeRequesterEtaVolumeAndRepeat() throws IOException {
        String embeds = read("commands/music/MusicEmbeds.java");

        assertTrue(embeds.contains("Заказал"));
        assertTrue(embeds.contains("Примерно начнётся через"));
        assertTrue(embeds.contains("Громкость"));
        assertTrue(embeds.contains("Повтор"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
