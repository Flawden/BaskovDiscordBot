package ru.flawden.BascovDiscordBot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Operational safety settings that do not change user-facing playback semantics.
 */
@Component
@ConfigurationProperties(prefix = "discord-bot.operations")
public class OperationsProperties {

    private boolean persistenceBackupEnabled = true;
    private Path persistenceBackupDirectory = Path.of("data", "backups");
    private Duration persistenceBackupInterval = Duration.ofHours(6);
    private int persistenceBackupRetention = 14;

    public boolean isPersistenceBackupEnabled() {
        return persistenceBackupEnabled;
    }

    public void setPersistenceBackupEnabled(boolean persistenceBackupEnabled) {
        this.persistenceBackupEnabled = persistenceBackupEnabled;
    }

    public Path getPersistenceBackupDirectory() {
        return persistenceBackupDirectory;
    }

    public void setPersistenceBackupDirectory(Path persistenceBackupDirectory) {
        this.persistenceBackupDirectory = Objects.requireNonNull(
                persistenceBackupDirectory,
                "discord-bot.operations.persistenceBackupDirectory");
    }

    public Duration getPersistenceBackupInterval() {
        return persistenceBackupInterval;
    }

    public void setPersistenceBackupInterval(Duration persistenceBackupInterval) {
        if (persistenceBackupInterval == null
                || persistenceBackupInterval.compareTo(Duration.ofMinutes(1)) < 0
                || persistenceBackupInterval.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException(
                    "discord-bot.operations.persistenceBackupInterval must be between 1m and 7d");
        }
        this.persistenceBackupInterval = persistenceBackupInterval;
    }

    public int getPersistenceBackupRetention() {
        return persistenceBackupRetention;
    }

    public void setPersistenceBackupRetention(int persistenceBackupRetention) {
        if (persistenceBackupRetention < 1 || persistenceBackupRetention > 100) {
            throw new IllegalArgumentException(
                    "discord-bot.operations.persistenceBackupRetention must be between 1 and 100");
        }
        this.persistenceBackupRetention = persistenceBackupRetention;
    }
}
