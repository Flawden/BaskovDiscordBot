package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductPlaylistApiContractTest {

    @Test
    void mobilePlaylistsReuseDiscordPersistenceAndRemainOwnerScoped() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/product/ProductPlaylistService.java"));
        String controller = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/product/api/ProductApiController.java"));

        assertTrue(service.contains("MusicLibraryRepository"));
        assertTrue(service.contains("library.createPlaylist"));
        assertTrue(service.contains("library.addTrack"));
        assertTrue(service.contains("library.removeTrack"));
        assertTrue(service.contains("library.moveTrack"));
        assertTrue(service.contains("library.renamePlaylist"));
        assertTrue(service.contains("library.deletePlaylist"));
        assertTrue(service.contains("actorUserId, false"));
        assertFalse(controller.contains("MusicLibraryRepository"));
        assertTrue(controller.contains("access.requireGuild"));
        assertTrue(controller.contains("principal.discordUserId()"));
    }

    @Test
    void selectedMobileTracksAreResolvedServerSideBeforePersistence() throws Exception {
        String resolver = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/product/ProductPlaylistTrackResolver.java"));
        String stream = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/ExternalAudioTrackStream.java"));

        assertTrue(resolver.contains("PlaybackClientCapabilities.android"));
        assertTrue(resolver.contains("stream.storedTrack"));
        assertTrue(stream.contains("StoredTrack.from(sourceTrack"));
    }
}
