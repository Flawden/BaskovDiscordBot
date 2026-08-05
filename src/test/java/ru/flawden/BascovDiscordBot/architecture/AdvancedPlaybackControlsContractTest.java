package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedPlaybackControlsContractTest {

    @Test
    void slashCatalogPublishesPreviousAlongsideExistingAdvancedControls() throws IOException {
        String catalog = source("src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernCommandCatalog.java");

        assertTrue(catalog.contains("Commands.slash(\"previous\""));
        assertTrue(catalog.contains("Commands.slash(\"seek\""));
        assertTrue(catalog.contains("Commands.slash(\"repeat\""));
        assertTrue(catalog.contains("Commands.slash(\"shuffle\""));
    }

    @Test
    void nowPanelContainsHistorySeekAndQueueActions() throws IOException {
        String controls = source("src/main/java/ru/flawden/BascovDiscordBot/interactions/MusicControls.java");
        String interactions = source("src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernInteractions.java");

        assertTrue(controls.contains("public static List<ActionRow> nowRows()"));
        assertTrue(controls.contains("SEEK_BACKWARD"));
        assertTrue(controls.contains("SEEK_FORWARD"));
        assertTrue(controls.contains("SHUFFLE"));
        assertTrue(interactions.contains("setComponents(MusicControls.nowRows())"));
    }

    @Test
    void previousHistoryIsBoundedAndDoesNotRecordBrokenSources() throws IOException {
        String scheduler = source("src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/TrackScheduler.java");

        assertTrue(scheduler.contains("Math.min(maxQueueSize, 25)"));
        assertTrue(scheduler.contains("advanceToNext(false)"));
        assertTrue(scheduler.contains("PreviousStatus"));
        assertFalse(scheduler.contains("new LinkedBlockingQueue"));
    }

    @Test
    void statusShowsLivePlaybackModes() throws IOException {
        String formatter = source("src/main/java/ru/flawden/BascovDiscordBot/interactions/StatusMessageFormatter.java");
        String interactions = source("src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernInteractions.java");

        assertTrue(formatter.contains("static String playback(GuildMusicManager manager)"));
        assertTrue(formatter.contains("Предыдущих"));
        assertTrue(interactions.contains("Playback modes"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
