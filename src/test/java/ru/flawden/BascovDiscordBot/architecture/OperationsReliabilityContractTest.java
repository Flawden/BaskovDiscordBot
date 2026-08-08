package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationsReliabilityContractTest {

    @Test
    void persistenceBackupsAreBoundedAtomicAndStoredOnPersistentVolume() throws IOException {
        String backup = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/operations/PersistenceBackupService.java"));
        String compose = Files.readString(Path.of("deploy/docker-compose.yml"));
        String workflow = Files.readString(Path.of(".github/workflows/delivery.yml"));
        String deploy = Files.readString(Path.of("deploy/remote-deploy.sh"));

        assertTrue(backup.contains("BASKOV_PERSISTENCE_BACKUP_V1"));
        assertTrue(backup.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(backup.contains("properties.getPersistenceBackupRetention()"));
        assertTrue(backup.contains("rw-------"));
        assertTrue(compose.contains("DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_DIRECTORY: /app/data/backups"));
        assertTrue(compose.contains("bot-data:/app/data"));
        for (String variable : new String[]{
                "DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_ENABLED",
                "DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_INTERVAL",
                "DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_RETENTION"}) {
            assertTrue(workflow.contains("printf '" + variable + "_B64=%s"), variable + " workflow B64");
            assertTrue(deploy.contains(variable + "_B64"), variable + " deploy B64");
            assertTrue(deploy.contains("printf '" + variable + "=%s"), variable + " protected env");
            assertTrue(compose.contains(variable), variable + " compose");
        }
        assertTrue(deploy.contains("Persistence backup (created|disabled)"));
    }

    @Test
    void statusContainsGatewayLifecycleBackupAndFailureRateSignals() throws IOException {
        String monitor = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/operations/RuntimeHealthMonitor.java"));
        String metrics = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/operations/OperationalMetrics.java"));
        String formatter = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/StatusMessageFormatter.java"));
        String interactions = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernInteractions.java"));

        assertTrue(monitor.contains("gatewayStatusTransitions"));
        assertTrue(monitor.contains("disconnectedHeartbeatSamples"));
        assertTrue(metrics.contains("failureRatePercent"));
        assertTrue(formatter.contains("PersistenceBackupService.Snapshot"));
        assertTrue(formatter.contains("Итог: `"));
        assertTrue(interactions.contains("Persistence backups"));
        assertTrue(interactions.contains("Reliability"));
    }

    @Test
    void applicationLogsHaveSizeAndRetentionCaps() throws IOException {
        String logback = Files.readString(Path.of("src/main/resources/logback.xml"));

        assertTrue(logback.contains("SizeAndTimeBasedRollingPolicy"));
        assertTrue(logback.contains("<maxFileSize>25MB</maxFileSize>"));
        assertTrue(logback.contains("<maxHistory>14</maxHistory>"));
        assertTrue(logback.contains("<totalSizeCap>512MB</totalSizeCap>"));
    }
}
