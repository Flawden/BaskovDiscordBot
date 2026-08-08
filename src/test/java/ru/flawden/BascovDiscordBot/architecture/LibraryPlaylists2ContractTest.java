package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryPlaylists2ContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void playlistCatalogPublishesLifecycleCaptureAndSearchCommands() throws IOException {
        String catalog = read("interactions/ModernCommandCatalog.java");

        assertTrue(catalog.contains("new SubcommandData(\"rename\""));
        assertTrue(catalog.contains("new SubcommandData(\"copy\""));
        assertTrue(catalog.contains("new SubcommandData(\"move\""));
        assertTrue(catalog.contains("new SubcommandData(\"dedupe\""));
        assertTrue(catalog.contains("new SubcommandData(\"capture-queue\""));
        assertTrue(catalog.contains("new SubcommandData(\"add-history\""));
        assertTrue(catalog.contains("new SubcommandData(\"search\""));
    }

    @Test
    void repositoryKeepsPlaylistMutationsAtomicAndBackwardCompatible() throws IOException {
        String repository = read("library/FileMusicLibraryRepository.java");

        assertTrue(repository.contains("PlaylistOperationResult renamePlaylist("));
        assertTrue(repository.contains("PlaylistOperationResult copyPlaylist("));
        assertTrue(repository.contains("PlaylistOperationResult moveTrack("));
        assertTrue(repository.contains("PlaylistOperationResult dedupePlaylist("));
        assertTrue(repository.contains("PlaylistOperationResult addTracks("));
        assertTrue(repository.contains("replaceAndPersist(guildId, previous"));
        assertTrue(repository.contains("BASKOV_MUSIC_LIBRARY_V1"));
    }

    @Test
    void interactionsCanCaptureQueueSearchAndReuseHistory() throws IOException {
        String interactions = read("interactions/ModernInteractions.java");
        String embeds = read("commands/music/MusicEmbeds.java");

        assertTrue(interactions.contains("case \"capture-queue\""));
        assertTrue(interactions.contains("queuedRequests()"));
        assertTrue(interactions.contains("case \"add-history\""));
        assertTrue(interactions.contains("case \"search\""));
        assertTrue(embeds.contains("playlistSearch(String query"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
