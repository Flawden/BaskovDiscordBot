package ru.flawden.BascovDiscordBot.operations;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Updates the dynamic readiness heartbeat only while the Discord gateway is
 * connected and keeps a compact lifecycle history for operational diagnosis.
 */
@Slf4j
@Component
public class RuntimeHealthMonitor {

    public static final Path DEFAULT_HEALTH_FILE = Path.of(
            System.getProperty("java.io.tmpdir"),
            "baskov-discord-bot.ready");
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10);

    private final Path healthFile;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private volatile JDA jda;
    private volatile int registeredSlashCommands;
    private volatile Instant lastHealthyAt;
    private volatile Instant lastConnectedAt;
    private volatile Instant lastStatusChangeAt;
    private volatile String lastObservedStatus = "STARTING";
    private volatile long gatewayStatusTransitions;
    private volatile long disconnectedHeartbeatSamples;
    private volatile ScheduledFuture<?> heartbeatTask;

    public RuntimeHealthMonitor() {
        this(DEFAULT_HEALTH_FILE, Clock.systemUTC());
    }

    RuntimeHealthMonitor(Path healthFile) {
        this(healthFile, Clock.systemUTC());
    }

    RuntimeHealthMonitor(Path healthFile, Clock clock) {
        this.healthFile = Objects.requireNonNull(healthFile, "healthFile");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "baskov-runtime-health");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void start(JDA readyJda, int slashCommands) {
        this.jda = Objects.requireNonNull(readyJda, "readyJda");
        this.registeredSlashCommands = slashCommands;
        deleteHealthFile();
        heartbeat();
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        heartbeatTask = scheduler.scheduleWithFixedDelay(
                this::heartbeatSafely,
                HEARTBEAT_INTERVAL.toSeconds(),
                HEARTBEAT_INTERVAL.toSeconds(),
                TimeUnit.SECONDS);
        log.info("Runtime health heartbeat started: file={}, interval={}",
                healthFile, HEARTBEAT_INTERVAL);
    }

    public Snapshot snapshot() {
        JDA current = jda;
        String status = current == null ? "STARTING" : current.getStatus().name();
        return new Snapshot(
                status,
                current == null ? 0 : current.getGuilds().size(),
                registeredSlashCommands,
                lastHealthyAt,
                lastConnectedAt,
                lastStatusChangeAt,
                gatewayStatusTransitions,
                disconnectedHeartbeatSamples);
    }

    private void heartbeatSafely() {
        try {
            heartbeat();
        } catch (RuntimeException exception) {
            deleteHealthFile();
            log.warn("Runtime health heartbeat failed", exception);
        }
    }

    synchronized void heartbeat() {
        JDA current = jda;
        Instant now = clock.instant();
        if (current == null) {
            observeStatus("STARTING", now);
            deleteHealthFile();
            return;
        }

        JDA.Status currentStatus = current.getStatus();
        String status = currentStatus.name();
        observeStatus(status, now);
        if (currentStatus != JDA.Status.CONNECTED) {
            disconnectedHeartbeatSamples++;
            deleteHealthFile();
            return;
        }

        lastConnectedAt = now;
        String payload = "status=CONNECTED\n"
                + "timestamp=" + now + "\n"
                + "guilds=" + current.getGuilds().size() + "\n"
                + "slashCommands=" + registeredSlashCommands + "\n"
                + "gatewayTransitions=" + gatewayStatusTransitions + "\n"
                + "disconnectedSamples=" + disconnectedHeartbeatSamples + "\n";
        writeAtomically(payload);
        lastHealthyAt = now;
    }

    private void observeStatus(String status, Instant now) {
        if (!Objects.equals(lastObservedStatus, status)) {
            if (!"STARTING".equals(lastObservedStatus)) {
                gatewayStatusTransitions++;
            }
            log.info("Discord gateway status transition: {} -> {}", lastObservedStatus, status);
            lastObservedStatus = status;
            lastStatusChangeAt = now;
        }
    }

    private void writeAtomically(String payload) {
        Path parent = healthFile.getParent();
        Path temporary = healthFile.resolveSibling(healthFile.getFileName() + ".tmp");
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    temporary,
                    payload,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(
                        temporary,
                        healthFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, healthFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            deleteHealthFile();
            throw new IllegalStateException("Cannot update runtime health heartbeat", exception);
        }
    }

    private void deleteHealthFile() {
        try {
            Files.deleteIfExists(healthFile);
            Files.deleteIfExists(healthFile.resolveSibling(healthFile.getFileName() + ".tmp"));
        } catch (IOException exception) {
            log.debug("Cannot remove runtime health file {}", healthFile, exception);
        }
    }

    @PreDestroy
    public synchronized void close() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
        scheduler.shutdownNow();
        deleteHealthFile();
    }

    public record Snapshot(
            String jdaStatus,
            int guildCount,
            int registeredSlashCommands,
            Instant lastHealthyAt,
            Instant lastConnectedAt,
            Instant lastStatusChangeAt,
            long gatewayStatusTransitions,
            long disconnectedHeartbeatSamples) {
    }
}
