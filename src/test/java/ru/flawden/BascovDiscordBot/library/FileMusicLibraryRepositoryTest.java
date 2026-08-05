package ru.flawden.BascovDiscordBot.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.config.MusicLibraryProperties;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMusicLibraryRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsUnicodePlaylistAndTracksAcrossReload() {
        Path file = tempDir.resolve("music-library.tsv");
        FileMusicLibraryRepository repository = repository(file);

        PlaylistOperationResult created = repository.createPlaylist(10L, 20L, "  Рок   вечером ");
        PlaylistOperationResult added = repository.addTrack(
                10L, "рок вечером", 20L, false, track("Беспечный ангел", 1L));

        assertEquals(PlaylistOperationResult.Status.CREATED, created.status());
        assertEquals(PlaylistOperationResult.Status.ADDED, added.status());

        FileMusicLibraryRepository reloaded = repository(file);
        StoredPlaylist playlist = reloaded.playlist(10L, "РОК ВЕЧЕРОМ").orElseThrow();
        assertEquals("Рок вечером", playlist.name());
        assertEquals(1, playlist.tracks().size());
        assertEquals("Беспечный ангел", playlist.tracks().get(0).title());
    }

    @Test
    void enforcesOwnerButAllowsManageServerOverride() {
        FileMusicLibraryRepository repository = repository(tempDir.resolve("permissions.tsv"));
        repository.createPlaylist(10L, 20L, "Owned");

        assertEquals(
                PlaylistOperationResult.Status.FORBIDDEN,
                repository.addTrack(10L, "Owned", 30L, false, track("Denied", 1L)).status());
        assertEquals(
                PlaylistOperationResult.Status.ADDED,
                repository.addTrack(10L, "Owned", 30L, true, track("Allowed", 2L)).status());
    }

    @Test
    void removesTrackAndDeletesPlaylistAtomically() {
        FileMusicLibraryRepository repository = repository(tempDir.resolve("mutations.tsv"));
        repository.createPlaylist(10L, 20L, "Queue");
        repository.addTrack(10L, "Queue", 20L, false, track("First", 1L));
        repository.addTrack(10L, "Queue", 20L, false, track("Second", 2L));

        PlaylistOperationResult removed = repository.removeTrack(10L, "Queue", 20L, false, 1);
        assertEquals(PlaylistOperationResult.Status.REMOVED, removed.status());
        assertEquals("First", removed.track().title());
        assertEquals("Second", removed.playlist().tracks().get(0).title());

        assertEquals(
                PlaylistOperationResult.Status.DELETED,
                repository.deletePlaylist(10L, "Queue", 20L, false).status());
        assertTrue(repository.playlist(10L, "Queue").isEmpty());
    }

    @Test
    void keepsNewestHistoryFirstAndBoundsIt() {
        Path file = tempDir.resolve("history.tsv");
        FileMusicLibraryRepository repository = repository(file);
        for (int index = 1; index <= MusicLibraryRepository.MAX_HISTORY_PER_GUILD + 5; index++) {
            repository.recordHistory(10L, track("Track " + index, index));
        }

        assertEquals(MusicLibraryRepository.MAX_HISTORY_PER_GUILD, repository.history(10L).size());
        assertEquals("Track 55", repository.history(10L).get(0).title());
        assertEquals("Track 6", repository.history(10L).get(49).title());

        FileMusicLibraryRepository reloaded = repository(file);
        assertEquals("Track 55", reloaded.history(10L).get(0).title());
        assertEquals(50, reloaded.history(10L).size());
    }

    @Test
    void rejectsDuplicatePlaylistNamesIgnoringCase() {
        FileMusicLibraryRepository repository = repository(tempDir.resolve("duplicates.tsv"));
        assertEquals(
                PlaylistOperationResult.Status.CREATED,
                repository.createPlaylist(10L, 20L, "Favorites").status());
        assertEquals(
                PlaylistOperationResult.Status.ALREADY_EXISTS,
                repository.createPlaylist(10L, 20L, "favorites").status());
    }

    private static FileMusicLibraryRepository repository(Path file) {
        MusicLibraryProperties properties = new MusicLibraryProperties();
        properties.setFile(file);
        FileMusicLibraryRepository repository = new FileMusicLibraryRepository(properties);
        repository.load();
        return repository;
    }

    private static StoredTrack track(String title, long sequence) {
        return new StoredTrack(
                title,
                "Artist",
                "https://www.youtube.com/watch?v=id" + sequence,
                "id" + sequence,
                MediaProvider.YOUTUBE,
                180_000L,
                42L,
                "Requester",
                1_700_000_000_000L + sequence);
    }
}
