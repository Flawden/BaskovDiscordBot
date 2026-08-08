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
    void renamesCopiesMovesAndDeduplicatesPlaylistsAtomically() {
        FileMusicLibraryRepository repository = repository(tempDir.resolve("library-2.tsv"));
        repository.createPlaylist(10L, 20L, "Roadtrip");
        repository.addTrack(10L, "Roadtrip", 20L, false, track("First", 1L));
        repository.addTrack(10L, "Roadtrip", 20L, false, track("Second", 2L));
        repository.addTrack(10L, "Roadtrip", 20L, false, track("First again", 1L));

        PlaylistOperationResult moved = repository.moveTrack(10L, "Roadtrip", 20L, false, 1, 2);
        assertEquals(PlaylistOperationResult.Status.MOVED, moved.status());
        assertEquals("Second", moved.playlist().tracks().get(0).title());

        PlaylistOperationResult deduped = repository.dedupePlaylist(10L, "Roadtrip", 20L, false);
        assertEquals(PlaylistOperationResult.Status.DEDUPED, deduped.status());
        assertEquals(1, deduped.affectedTracks());
        assertEquals(2, deduped.playlist().tracks().size());

        PlaylistOperationResult renamed = repository.renamePlaylist(
                10L, "Roadtrip", "Night Drive", 20L, false);
        assertEquals(PlaylistOperationResult.Status.RENAMED, renamed.status());
        assertTrue(repository.playlist(10L, "Roadtrip").isEmpty());
        assertTrue(repository.playlist(10L, "Night Drive").isPresent());

        PlaylistOperationResult copied = repository.copyPlaylist(
                10L, "Night Drive", "My Copy", 30L);
        assertEquals(PlaylistOperationResult.Status.COPIED, copied.status());
        assertEquals(30L, copied.playlist().ownerUserId());
        assertEquals(2, copied.playlist().tracks().size());

        FileMusicLibraryRepository reloaded = repository(tempDir.resolve("library-2.tsv"));
        assertEquals(2, reloaded.playlist(10L, "Night Drive").orElseThrow().tracks().size());
        assertEquals(2, reloaded.playlist(10L, "My Copy").orElseThrow().tracks().size());
    }

    @Test
    void capturesTracksInOneMutationAndSearchesPlaylistTitlesTracksAndArtists() {
        FileMusicLibraryRepository repository = repository(tempDir.resolve("search.tsv"));
        repository.createPlaylist(10L, 20L, "Synthwave Nights");

        PlaylistOperationResult captured = repository.addTracks(
                10L,
                "Synthwave Nights",
                20L,
                false,
                java.util.List.of(
                        track("Neon Drive", 1L),
                        new StoredTrack(
                                "Midnight Run",
                                "Timecop1983",
                                "https://www.youtube.com/watch?v=id2",
                                "id2",
                                MediaProvider.YOUTUBE,
                                180_000L,
                                42L,
                                "Requester",
                                1_700_000_000_002L)));

        assertEquals(PlaylistOperationResult.Status.BULK_ADDED, captured.status());
        assertEquals(2, captured.affectedTracks());
        assertEquals(1, repository.search(10L, "synthwave").size());
        assertEquals(java.util.List.of(2), repository.search(10L, "timecop").get(0).matchingPositions());
        assertEquals(java.util.List.of(1), repository.search(10L, "neon").get(0).matchingPositions());
    }

    @Test
    void bulkCaptureRejectsOverflowWithoutPartialMutation() {
        FileMusicLibraryRepository repository = repository(tempDir.resolve("bulk-limit.tsv"));
        repository.createPlaylist(10L, 20L, "Full soon");
        for (int index = 1; index < MusicLibraryRepository.MAX_TRACKS_PER_PLAYLIST; index++) {
            repository.addTrack(10L, "Full soon", 20L, false, track("Track " + index, index));
        }

        PlaylistOperationResult result = repository.addTracks(
                10L,
                "Full soon",
                20L,
                false,
                java.util.List.of(track("Extra 1", 100L), track("Extra 2", 101L)));

        assertEquals(PlaylistOperationResult.Status.TRACK_LIMIT_REACHED, result.status());
        assertEquals(MusicLibraryRepository.MAX_TRACKS_PER_PLAYLIST - 1,
                repository.playlist(10L, "Full soon").orElseThrow().tracks().size());
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
