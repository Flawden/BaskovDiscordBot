package ru.flawden.BascovDiscordBot.operations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.flawden.BascovDiscordBot.config.OperationsProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceBackupServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void createsAtomicArchiveWithManifestAndExistingStores() throws Exception {
        Path settings = write("data/guild-settings.properties", "guild=42\n");
        Path library = write("data/music-library.tsv", "library\n");
        Path sessions = write("data/music-sessions.tsv", "sessions\n");
        OperationsProperties properties = properties(5);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-08T12:00:00Z"));
        PersistenceBackupService service = new PersistenceBackupService(
                properties, settings, library, sessions, clock);

        Path archive = service.createBackupNow();

        assertNotNull(archive);
        assertTrue(Files.isRegularFile(archive));
        assertEquals("READY", service.snapshot().status());
        assertEquals(1L, service.snapshot().successfulBackups());
        assertEquals(3, service.snapshot().lastIncludedStores());
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertNotNull(zip.getEntry("guild-settings.properties"));
            assertNotNull(zip.getEntry("music-library.tsv"));
            assertNotNull(zip.getEntry("music-sessions.tsv"));
            assertNotNull(zip.getEntry("manifest.properties"));
            String manifest = new String(zip.getInputStream(zip.getEntry("manifest.properties")).readAllBytes());
            assertTrue(manifest.contains("format=BASKOV_PERSISTENCE_BACKUP_V1"));
            assertTrue(manifest.contains("guild-settings.properties.present=true"));
        }
        try (var stream = Files.list(properties.getPersistenceBackupDirectory())) {
            assertTrue(stream.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }

        service.close();
    }

    @Test
    void keepsOnlyConfiguredNumberOfNewestArchives() throws Exception {
        Path settings = write("stores/settings.properties", "one\n");
        Path library = write("stores/library.tsv", "two\n");
        Path sessions = write("stores/sessions.tsv", "three\n");
        OperationsProperties properties = properties(2);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-08T12:00:00Z"));
        PersistenceBackupService service = new PersistenceBackupService(
                properties, settings, library, sessions, clock);

        service.createBackupNow();
        clock.advance(Duration.ofSeconds(1));
        service.createBackupNow();
        clock.advance(Duration.ofSeconds(1));
        Path newest = service.createBackupNow();

        try (var stream = Files.list(properties.getPersistenceBackupDirectory())) {
            assertEquals(2L, stream.filter(path -> path.getFileName().toString().endsWith(".zip")).count());
        }
        assertTrue(Files.exists(newest));
        assertEquals(3L, service.snapshot().successfulBackups());

        service.close();
    }

    @Test
    void disabledBackupDoesNotCreateFiles() {
        OperationsProperties properties = properties(2);
        properties.setPersistenceBackupEnabled(false);
        PersistenceBackupService service = new PersistenceBackupService(
                properties,
                tempDirectory.resolve("settings.properties"),
                tempDirectory.resolve("library.tsv"),
                tempDirectory.resolve("sessions.tsv"),
                Clock.systemUTC());

        assertNull(service.createBackupNow());
        assertEquals("DISABLED", service.snapshot().status());
        assertTrue(service.snapshot().healthy());
        assertTrue(Files.notExists(properties.getPersistenceBackupDirectory()));

        service.close();
    }

    private OperationsProperties properties(int retention) {
        OperationsProperties properties = new OperationsProperties();
        properties.setPersistenceBackupDirectory(tempDirectory.resolve("backups"));
        properties.setPersistenceBackupInterval(Duration.ofHours(1));
        properties.setPersistenceBackupRetention(retention);
        return properties;
    }

    private Path write(String relative, String content) throws IOException {
        Path path = tempDirectory.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
