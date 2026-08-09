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
        assertEquals(1, restored.history().size());
        assertEquals("Previous", restored.history().get(0).track().title());
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
    void loadsLegacyV1CheckpointWithoutHistoryAndUpgradesOnNextSave() throws Exception {
        Path file = tempDir.resolve("legacy-v1.tsv");
        FileMusicSessionRepository writer = repository(file);
        writer.save(session("Legacy", 12_000L));
        List<String> v2 = Files.readAllLines(file);
        String legacyLine = String.join("\t", v2.get(1).split("\t", -1)[0],
                v2.get(1).split("\t", -1)[1], v2.get(1).split("\t", -1)[2],
                v2.get(1).split("\t", -1)[3], v2.get(1).split("\t", -1)[4],
                v2.get(1).split("\t", -1)[5], v2.get(1).split("\t", -1)[6],
                v2.get(1).split("\t", -1)[7], v2.get(1).split("\t", -1)[8]);
        Files.write(file, List.of(FileMusicSessionRepository.LEGACY_HEADER_V1, legacyLine));

        FileMusicSessionRepository legacy = repository(file);
        StoredMusicSession restored = legacy.session(10L).orElseThrow();
        assertTrue(restored.history().isEmpty());

        legacy.save(restored);
        assertEquals(FileMusicSessionRepository.HEADER, Files.readAllLines(file).get(0));
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
                List.of(new StoredSessionTrack(queued, 0L)),
                List.of(new StoredSessionTrack(track("Previous", "previous"), 0L)));
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
