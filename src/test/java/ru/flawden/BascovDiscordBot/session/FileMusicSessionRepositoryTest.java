package ru.flawden.BascovDiscordBot.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.config.MusicSessionProperties;
import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMusicSessionRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsUnicodeCheckpointAcrossReload() throws Exception {
        Path file = tempDir.resolve("music-sessions.tsv");
        FileMusicSessionRepository repository = repository(file);
        repository.save(session("Трава у дома", 31_500L));

        assertEquals(FileMusicSessionRepository.HEADER, Files.readAllLines(file).get(0));
        FileMusicSessionRepository reloaded = repository(file);
        StoredMusicSession restored = reloaded.session(10L).orElseThrow();

        assertEquals(20L, restored.voiceChannelId());
        assertEquals("Трава у дома", restored.currentTrack().track().title());
        assertEquals(31_500L, restored.currentTrack().positionMillis());
        assertEquals(1, restored.queue().size());
        assertEquals(RepeatMode.QUEUE, restored.repeatMode());
    }

    @Test
    void overwritesSingleGuildCheckpointInsteadOfGrowingUnbounded() {
        FileMusicSessionRepository repository = repository(tempDir.resolve("replace.tsv"));
        repository.save(session("First", 1_000L));
        repository.save(session("Second", 2_000L));

        assertEquals(1, repository.sessions().size());
        assertEquals("Second", repository.session(10L).orElseThrow().currentTrack().track().title());
    }


    @Test
    void ignoresCheckpointWhoseProviderDoesNotMatchPlaybackUrl() throws Exception {
        Path file = tempDir.resolve("provider-mismatch.tsv");
        FileMusicSessionRepository repository = repository(file);
        repository.save(session("Valid", 1_000L));
        String corrupted = Files.readString(file).replace(",YOUTUBE,", ",HTTP,");
        Files.writeString(file, corrupted);

        FileMusicSessionRepository reloaded = repository(file);

        assertTrue(reloaded.sessions().isEmpty());
    }

    @Test
    void removeDeletesCheckpointAndPersistsEmptyHeader() throws Exception {
        Path file = tempDir.resolve("remove.tsv");
        FileMusicSessionRepository repository = repository(file);
        repository.save(session("Temporary", 0L));
        repository.remove(10L);

        assertTrue(repository.session(10L).isEmpty());
        List<String> lines = Files.readAllLines(file);
        assertEquals(List.of(FileMusicSessionRepository.HEADER), lines);
        assertFalse(Files.readString(file).contains("Temporary"));
    }

    private static FileMusicSessionRepository repository(Path file) {
        MusicSessionProperties properties = new MusicSessionProperties();
        properties.setFile(file);
        FileMusicSessionRepository repository = new FileMusicSessionRepository(properties);
        repository.load();
        return repository;
    }

    private static StoredMusicSession session(String title, long position) {
        StoredTrack current = track(title, "current");
        StoredTrack queued = track("Queued", "queued");
        return new StoredMusicSession(
                10L,
                20L,
                1_700_000_000_000L,
                true,
                85,
                RepeatMode.QUEUE,
                new StoredSessionTrack(current, position),
                List.of(new StoredSessionTrack(queued, 0L)));
    }

    private static StoredTrack track(String title, String id) {
        return new StoredTrack(
                title,
                "Artist",
                "https://www.youtube.com/watch?v=" + id,
                id,
                MediaProvider.YOUTUBE,
                180_000L,
                42L,
                "Requester",
                1_700_000_000_000L);
    }
}
