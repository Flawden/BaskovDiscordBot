package ru.flawden.BascovDiscordBot.operations;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.MusicLibraryProperties;
import ru.flawden.BascovDiscordBot.config.MusicSessionProperties;
import ru.flawden.BascovDiscordBot.config.PersistenceProperties;
import ru.flawden.BascovDiscordBot.config.RecommendationFeedbackProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fail-fast проверка долговременных файлов до подключения к Discord.
 *
 * <p>Проверяются раздельность путей, тип существующих файлов и возможность
 * реально создать/записать временный файл рядом с каждым storage.</p>
 */
@Slf4j
@Component
public class PersistenceReadiness {

    private final List<Path> stores;
    private volatile Snapshot snapshot = Snapshot.notChecked();

    @Autowired
    public PersistenceReadiness(
            PersistenceProperties persistenceProperties,
            MusicLibraryProperties musicLibraryProperties,
            MusicSessionProperties musicSessionProperties,
            RecommendationFeedbackProperties recommendationFeedbackProperties) {
        this(
                persistenceProperties.getFile(),
                musicLibraryProperties.getFile(),
                musicSessionProperties.getFile(),
                recommendationFeedbackProperties.getFile());
    }

    PersistenceReadiness(Path guildSettings, Path musicLibrary, Path musicSessions, Path recommendationFeedback) {
        this.stores = List.of(
                normalize(guildSettings),
                normalize(musicLibrary),
                normalize(musicSessions),
                normalize(recommendationFeedback));
    }

    public synchronized Snapshot requireReady() {
        Instant checkedAt = Instant.now();
        try {
            snapshot = evaluate(checkedAt);
            log.info("Persistence readiness: READY stores={} existing={}", stores.size(), snapshot.existingFiles());
            return snapshot;
        } catch (RuntimeException exception) {
            snapshot = failed(checkedAt, exception);
            log.error("Persistence readiness: FAILED reason={}", snapshot.details());
            throw exception;
        }
    }

    /**
     * Re-runs the storage probe for live diagnostics without terminating the
     * process. A failed probe is reflected in the returned snapshot and can be
     * surfaced by /status while the next successful probe can recover it.
     */
    public synchronized Snapshot probe() {
        Instant checkedAt = Instant.now();
        try {
            snapshot = evaluate(checkedAt);
            return snapshot;
        } catch (RuntimeException exception) {
            snapshot = failed(checkedAt, exception);
            log.warn("Persistence readiness probe degraded: {}", snapshot.details());
            return snapshot;
        }
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    private Snapshot evaluate(Instant checkedAt) {
        ensureDistinctStores();
        int existingFiles = 0;
        for (Path store : stores) {
            if (checkStore(store)) {
                existingFiles++;
            }
        }
        return new Snapshot("READY", stores.size(), existingFiles, checkedAt, "ready");
    }

    private Snapshot failed(Instant checkedAt, RuntimeException exception) {
        return new Snapshot("FAILED", stores.size(), 0, checkedAt, safeMessage(exception));
    }

    private void ensureDistinctStores() {
        Set<Path> unique = new LinkedHashSet<>(stores);
        if (unique.size() != stores.size()) {
            throw new IllegalStateException("Persistence files must use four distinct paths");
        }
    }

    private boolean checkStore(Path store) {
        Path parent = store.getParent();
        if (parent == null) {
            throw new IllegalStateException("Persistence path has no parent directory: " + store);
        }

        boolean exists = Files.exists(store, LinkOption.NOFOLLOW_LINKS);
        if (exists) {
            if (Files.isSymbolicLink(store)) {
                throw new IllegalStateException("Persistence file cannot be a symbolic link: " + store);
            }
            if (!Files.isRegularFile(store, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Persistence path is not a regular file: " + store);
            }
            if (!Files.isReadable(store) || !Files.isWritable(store)) {
                throw new IllegalStateException("Persistence file is not readable and writable: " + store);
            }
        }

        Path probe = null;
        try {
            Files.createDirectories(parent);
            probe = Files.createTempFile(parent, ".baskov-storage-preflight-", ".tmp");
            Files.writeString(
                    probe,
                    "ready\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            if (!Files.isReadable(probe) || !Files.isWritable(probe)) {
                throw new IllegalStateException("Persistence directory probe is not readable and writable: " + parent);
            }
            return exists;
        } catch (IOException exception) {
            throw new IllegalStateException("Persistence directory is not writable: " + parent, exception);
        } finally {
            if (probe != null) {
                try {
                    Files.deleteIfExists(probe);
                } catch (IOException exception) {
                    log.warn("Cannot remove persistence readiness probe {}", probe, exception);
                }
            }
        }
    }

    private static Path normalize(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Persistence path cannot be null");
        }
        return path.toAbsolutePath().normalize();
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.replace('`', '\'');
    }

    public record Snapshot(
            String status,
            int stores,
            int existingFiles,
            Instant checkedAt,
            String details) {

        static Snapshot notChecked() {
            return new Snapshot("NOT_CHECKED", 4, 0, null, "not checked");
        }

        public boolean ready() {
            return "READY".equals(status);
        }
    }
}
