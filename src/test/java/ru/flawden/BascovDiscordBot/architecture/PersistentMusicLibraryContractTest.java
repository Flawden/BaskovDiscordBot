package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentMusicLibraryContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void slashCatalogPublishesPlaylistHistoryAndReplay() throws IOException {
        String catalog = source("interactions/ModernCommandCatalog.java");
        String interactions = source("interactions/ModernInteractions.java");

        assertTrue(catalog.contains("Commands.slash(\"playlist\""));
        assertTrue(catalog.contains("Commands.slash(\"history\""));
        assertTrue(catalog.contains("Commands.slash(\"replay\""));
        assertTrue(interactions.contains("case \"playlist\" -> playlist(event)"));
        assertTrue(interactions.contains("case \"history\" -> history(event)"));
        assertTrue(interactions.contains("case \"replay\" -> replay(event)"));
    }

    @Test
    void libraryUsesAtomicDedicatedPersistentFile() throws IOException {
        String repository = source("library/FileMusicLibraryRepository.java");
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));
        String compose = Files.readString(Path.of("deploy/docker-compose.yml"));

        assertTrue(repository.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(repository.contains("BASKOV_MUSIC_LIBRARY_V1"));
        assertTrue(properties.contains("DISCORD_BOT_MUSIC_LIBRARY_FILE"));
        assertTrue(compose.contains("/app/data/music-library.tsv"));
    }

    @Test
    void historyIsRecordedOffAudioCallbackThreadAndSkipsSourceFailures() throws IOException {
        String scheduler = source("lavaplayer/TrackScheduler.java");
        String recorder = source("library/PlaybackHistoryRecorder.java");

        assertTrue(scheduler.contains("historyListener.accept(remembered)"));
        assertTrue(scheduler.contains("advanceToNext(false)"));
        assertTrue(recorder.contains("newSingleThreadExecutor"));
        assertTrue(recorder.contains("baskov-playback-history"));
    }

    @Test
    void replayAndPlaylistUseOrderedBatchLoadingOfStoredUrls() throws IOException {
        String player = source("lavaplayer/PlayerManager.java");
        String interactions = source("interactions/ModernInteractions.java");

        assertTrue(player.contains("public void loadBatch("));
        assertTrue(player.contains("loadAndPlay(guild, identifier, requester"));
        assertTrue(interactions.contains("StoredTrack::playbackIdentifier"));
        assertTrue(interactions.contains("playerManager.loadBatch("));
        assertTrue(interactions.contains("awaitPlaybackReady"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
