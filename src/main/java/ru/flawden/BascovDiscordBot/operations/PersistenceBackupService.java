package ru.flawden.BascovDiscordBot.operations;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.MusicLibraryProperties;
import ru.flawden.BascovDiscordBot.config.MusicSessionProperties;
import ru.flawden.BascovDiscordBot.config.OperationsProperties;
import ru.flawden.BascovDiscordBot.config.PersistenceProperties;
import ru.flawden.BascovDiscordBot.config.RecommendationFeedbackProperties;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates bounded, atomic ZIP snapshots of the persistent stores.
 *
 * <p>The backup directory is expected to live on the same persistent Docker
 * volume as the source data. Backups never contain absolute host paths and the
 * scheduler is deliberately independent from Discord/JDA availability.</p>
 */
@Slf4j
@Component
public class PersistenceBackupService {

    private static final String BACKUP_PREFIX = "baskov-persistence-";
    private static final String BACKUP_SUFFIX = ".zip";
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);

    private final OperationsProperties properties;
    private final List<Store> stores;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong successfulBackups = new AtomicLong();
    private final AtomicLong failedBackups = new AtomicLong();

    private volatile Snapshot snapshot;
    private volatile ScheduledFuture<?> scheduledBackup;

    @Autowired
    public PersistenceBackupService(
            OperationsProperties properties,
            PersistenceProperties persistenceProperties,
            MusicLibraryProperties musicLibraryProperties,
            MusicSessionProperties musicSessionProperties,
            RecommendationFeedbackProperties recommendationFeedbackProperties) {
        this(
                properties,
                persistenceProperties.getFile(),
                musicLibraryProperties.getFile(),
                musicSessionProperties.getFile(),
                recommendationFeedbackProperties.getFile(),
                Clock.systemUTC());
    }

    PersistenceBackupService(
            OperationsProperties properties,
            Path guildSettings,
            Path musicLibrary,
            Path musicSessions,
            Path recommendationFeedback,
            Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.stores = List.of(
                new Store("guild-settings.properties", normalize(guildSettings)),
                new Store("music-library.tsv", normalize(musicLibrary)),
                new Store("music-sessions.tsv", normalize(musicSessions)),
                new Store("recommendation-feedback.tsv", normalize(recommendationFeedback)));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "baskov-persistence-backup");
            thread.setDaemon(true);
            return thread;
        });
        this.snapshot = properties.isPersistenceBackupEnabled()
                ? Snapshot.waiting(properties)
                : Snapshot.disabled(properties);
    }

    public synchronized void start() {
        if (!properties.isPersistenceBackupEnabled()) {
            snapshot = Snapshot.disabled(properties);
            log.info("Persistence backup disabled");
            return;
        }
        if (scheduledBackup != null) {
            return;
        }

        createBackupSafely();
        Duration interval = properties.getPersistenceBackupInterval();
        scheduledBackup = scheduler.scheduleWithFixedDelay(
                this::createBackupSafely,
                interval.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS);
        log.info("Persistence backup scheduler started: directory={}, interval={}, retention={}",
                normalizedBackupDirectory(), interval, properties.getPersistenceBackupRetention());
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    synchronized Path createBackupNow() {
        if (!properties.isPersistenceBackupEnabled()) {
            snapshot = Snapshot.disabled(properties);
            return null;
        }
        return createBackup();
    }

    private void createBackupSafely() {
        try {
            createBackupNow();
        } catch (RuntimeException exception) {
            log.warn("Persistence backup attempt failed: {}", safeMessage(exception));
        }
    }

    private Path createBackup() {
        Instant now = clock.instant();
        Path directory = normalizedBackupDirectory();
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory)) {
                throw new IllegalStateException("Backup directory cannot be a symbolic link");
            }
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Backup destination is not a directory");
            }
            hardenDirectoryPermissions(directory);

            String fileName = BACKUP_PREFIX + FILE_TIMESTAMP.format(now) + BACKUP_SUFFIX;
            Path destination = directory.resolve(fileName);
            temporary = directory.resolve(fileName + ".tmp");

            int includedStores = writeBackup(temporary, now);
            moveAtomically(temporary, destination);
            temporary = null;
            hardenFilePermissions(destination);
            pruneOldBackups(directory);

            long successes = successfulBackups.incrementAndGet();
            snapshot = new Snapshot(
                    "READY",
                    properties.isPersistenceBackupEnabled(),
                    properties.getPersistenceBackupInterval(),
                    properties.getPersistenceBackupRetention(),
                    successes,
                    failedBackups.get(),
                    now,
                    snapshot.lastFailureAt(),
                    destination.getFileName().toString(),
                    includedStores,
                    "ready");
            log.info("Persistence backup created: file={}, stores={}/{}",
                    destination.getFileName(), includedStores, stores.size());
            return destination;
        } catch (IOException | RuntimeException exception) {
            long failures = failedBackups.incrementAndGet();
            snapshot = new Snapshot(
                    "FAILED",
                    properties.isPersistenceBackupEnabled(),
                    properties.getPersistenceBackupInterval(),
                    properties.getPersistenceBackupRetention(),
                    successfulBackups.get(),
                    failures,
                    snapshot.lastSuccessAt(),
                    now,
                    snapshot.lastBackupFile(),
                    snapshot.lastIncludedStores(),
                    safeMessage(exception));
            throw exception instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new IllegalStateException("Cannot create persistence backup", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    log.debug("Cannot delete failed backup temp file {}", temporary, exception);
                }
            }
        }
    }

    private int writeBackup(Path temporary, Instant now) throws IOException {
        try (OutputStream output = Files.newOutputStream(
                temporary,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            int included = 0;
            StringBuilder manifest = new StringBuilder()
                    .append("format=BASKOV_PERSISTENCE_BACKUP_V1\n")
                    .append("createdAt=").append(now).append('\n');

            for (Store store : stores) {
                boolean exists = Files.exists(store.path(), LinkOption.NOFOLLOW_LINKS);
                manifest.append(store.entryName()).append(".present=").append(exists).append('\n');
                if (!exists) {
                    continue;
                }
                if (Files.isSymbolicLink(store.path())
                        || !Files.isRegularFile(store.path(), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException("Persistent store changed to an unsafe file type");
                }
                zip.putNextEntry(new ZipEntry(store.entryName()));
                Files.copy(store.path(), zip);
                zip.closeEntry();
                included++;
            }

            zip.putNextEntry(new ZipEntry("manifest.properties"));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            return included;
        }
    }

    private void pruneOldBackups(Path directory) throws IOException {
        List<Path> backups;
        try (var stream = Files.list(directory)) {
            backups = stream
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(BACKUP_PREFIX) && name.endsWith(BACKUP_SUFFIX);
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        int excess = backups.size() - properties.getPersistenceBackupRetention();
        for (int index = 0; index < excess; index++) {
            Path expired = backups.get(index);
            Files.deleteIfExists(expired);
            log.info("Persistence backup retention removed {}", expired.getFileName());
        }
    }

    private void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }


    private void hardenDirectoryPermissions(Path directory) {
        try {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException exception) {
            log.debug("POSIX directory permissions are not supported for {}", directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot harden backup directory permissions", exception);
        }
    }

    private void hardenFilePermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException exception) {
            log.debug("POSIX file permissions are not supported for {}", file);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot harden backup file permissions", exception);
        }
    }

    private Path normalizedBackupDirectory() {
        return properties.getPersistenceBackupDirectory().toAbsolutePath().normalize();
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path, "persistence path").toAbsolutePath().normalize();
    }

    private static String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.replace('`', '\'');
    }

    @PreDestroy
    public synchronized void close() {
        if (scheduledBackup != null) {
            scheduledBackup.cancel(false);
            scheduledBackup = null;
        }
        scheduler.shutdownNow();
    }

    private record Store(String entryName, Path path) {
    }

    public record Snapshot(
            String status,
            boolean enabled,
            Duration interval,
            int retention,
            long successfulBackups,
            long failedBackups,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            String lastBackupFile,
            int lastIncludedStores,
            String details) {

        static Snapshot waiting(OperationsProperties properties) {
            return new Snapshot(
                    "WAITING",
                    true,
                    properties.getPersistenceBackupInterval(),
                    properties.getPersistenceBackupRetention(),
                    0L,
                    0L,
                    null,
                    null,
                    "none",
                    0,
                    "waiting for first backup");
        }

        static Snapshot disabled(OperationsProperties properties) {
            return new Snapshot(
                    "DISABLED",
                    false,
                    properties.getPersistenceBackupInterval(),
                    properties.getPersistenceBackupRetention(),
                    0L,
                    0L,
                    null,
                    null,
                    "none",
                    0,
                    "disabled");
        }

        public boolean healthy() {
            return !enabled || !"FAILED".equals(status);
        }
    }
}
