package ru.flawden.BascovDiscordBot.operations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceReadinessTest {

    @TempDir
    Path tempDirectory;

    @Test
    void acceptsThreeDistinctWritableStores() throws Exception {
        Path settings = tempDirectory.resolve("data/guild-settings.properties");
        Path library = tempDirectory.resolve("data/music-library.tsv");
        Path sessions = tempDirectory.resolve("data/music-sessions.tsv");
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, "# settings\n");

        PersistenceReadiness readiness = new PersistenceReadiness(settings, library, sessions);
        PersistenceReadiness.Snapshot snapshot = readiness.requireReady();

        assertTrue(snapshot.ready());
        assertEquals("READY", snapshot.status());
        assertEquals(3, snapshot.stores());
        assertEquals(1, snapshot.existingFiles());
        try (var stream = Files.list(settings.getParent())) {
            assertFalse(stream.anyMatch(path -> path.getFileName().toString().startsWith(".baskov-storage-preflight-")));
        }
    }

    @Test
    void rejectsCollidingStoragePaths() {
        Path shared = tempDirectory.resolve("data/shared.tsv");
        PersistenceReadiness readiness = new PersistenceReadiness(
                shared,
                shared,
                tempDirectory.resolve("data/sessions.tsv"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, readiness::requireReady);

        assertTrue(exception.getMessage().contains("distinct"));
        assertEquals("FAILED", readiness.snapshot().status());
    }

    @Test
    void rejectsDirectoryUsedAsStorageFile() throws Exception {
        Path settings = tempDirectory.resolve("settings");
        Files.createDirectories(settings);
        PersistenceReadiness readiness = new PersistenceReadiness(
                settings,
                tempDirectory.resolve("library.tsv"),
                tempDirectory.resolve("sessions.tsv"));

        assertThrows(IllegalStateException.class, readiness::requireReady);
        assertEquals("FAILED", readiness.snapshot().status());
    }
    @Test
    void liveProbeReportsFailureWithoutThrowing() throws Exception {
        Path settings = tempDirectory.resolve("live/settings.properties");
        Path library = tempDirectory.resolve("live/library.tsv");
        Path sessions = tempDirectory.resolve("live/sessions.tsv");
        PersistenceReadiness readiness = new PersistenceReadiness(settings, library, sessions);

        assertTrue(readiness.requireReady().ready());
        Files.createDirectories(settings);

        PersistenceReadiness.Snapshot snapshot = readiness.probe();

        assertEquals("FAILED", snapshot.status());
        assertFalse(snapshot.ready());
        assertTrue(snapshot.details().contains("not a regular file"));
    }

}
