package ru.flawden.BascovDiscordBot.operations;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.dave.DaveRuntimeInfo;
import ru.flawden.BascovDiscordBot.session.SessionRecoverySnapshot;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemDoctorTest {

    private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");

    @Test
    void healthyRuntimeProducesOkReport() {
        SystemDoctor.Report report = SystemDoctor.evaluate(
                NOW,
                runtime("CONNECTED", NOW.minusSeconds(5)),
                dave(true),
                voice("none", 0L, null, false, false, false),
                recovery("none", 0),
                storage(true),
                backups("READY", true),
                commands(20, 0, null));

        assertEquals(SystemDoctor.Severity.OK, report.severity());
        assertEquals(8, report.checks().size());
        assertTrue(report.checks().stream().allMatch(check -> check.severity() == SystemDoctor.Severity.OK));
    }

    @Test
    void disconnectedGatewayAndFailedStorageAreHardFailures() {
        SystemDoctor.Report report = SystemDoctor.evaluate(
                NOW,
                runtime("DISCONNECTED", NOW.minus(Duration.ofMinutes(2))),
                dave(true),
                voice("none", 0L, null, false, false, false),
                recovery("none", 0),
                storage(false),
                backups("READY", true),
                commands(10, 0, null));

        assertEquals(SystemDoctor.Severity.FAIL, report.severity());
        assertEquals(SystemDoctor.Severity.FAIL, report.checks("gateway").get(0).severity());
        assertEquals(SystemDoctor.Severity.FAIL, report.checks("storage").get(0).severity());
    }

    @Test
    void recentSourceFailureIsWarnWithoutExternalProbe() {
        String sourceFailure = NOW.minus(Duration.ofMinutes(2)) + " YouTube track: HTTP 403";
        SystemDoctor.Report report = SystemDoctor.evaluate(
                NOW,
                runtime("CONNECTED", NOW.minusSeconds(3)),
                dave(true),
                voice(sourceFailure, 2L, Duration.ofSeconds(1), true, true, true),
                recovery("none", 0),
                storage(true),
                backups("READY", true),
                commands(15, 0, null));

        SystemDoctor.Check source = report.checks("source").get(0);
        assertEquals(SystemDoctor.Severity.WARN, source.severity());
        assertTrue(source.details().contains("HTTP 403"));
        assertTrue(source.action().contains("Повтори запрос"));
    }

    @Test
    void highCommandFailureRateBecomesFailAndRecoveryInProgressWarns() {
        SystemDoctor.Report report = SystemDoctor.evaluate(
                NOW,
                runtime("CONNECTED", NOW.minusSeconds(2)),
                dave(true),
                voice("none", 0L, null, false, false, false),
                recovery(NOW.minus(Duration.ofMinutes(1)) + " recovery started", 1),
                storage(true),
                backups("READY", true),
                commands(10, 6, NOW.minusSeconds(30)));

        assertEquals(SystemDoctor.Severity.FAIL, report.severity());
        assertEquals(SystemDoctor.Severity.FAIL, report.checks("commands").get(0).severity());
        assertEquals(SystemDoctor.Severity.WARN, report.checks("session").get(0).severity());
    }

    private RuntimeHealthMonitor.Snapshot runtime(String status, Instant healthyAt) {
        return new RuntimeHealthMonitor.Snapshot(
                status, 2, 30, healthyAt, healthyAt, healthyAt, 1L, 0L);
    }

    private DaveRuntimeInfo.Snapshot dave(boolean ready) {
        return new DaveRuntimeInfo.Snapshot(
                ready ? "READY" : "FAILED",
                "libdave-jvm",
                "test",
                ready ? 1 : 0,
                "test-platform",
                ready ? "none" : "native missing");
    }

    private VoiceDiagnosticSnapshot voice(
            String lastSourceError,
            long trackExceptions,
            Duration frameAge,
            boolean sessionActive,
            boolean playbackExpected,
            boolean audioManagerConnected) {
        return new VoiceDiagnosticSnapshot(
                "bridge",
                sessionActive ? "CONNECTED" : "IDLE",
                sessionActive ? "123" : "none",
                audioManagerConnected,
                sessionActive,
                playbackExpected,
                sessionActive ? "track" : "none",
                sessionActive ? 10L : 0L,
                frameAge,
                1L,
                1L,
                0L,
                trackExceptions,
                0L,
                0L,
                0L,
                0L,
                "none",
                "none",
                lastSourceError,
                "none",
                "none",
                true);
    }

    private SessionRecoverySnapshot recovery(String lastEvent, int inProgress) {
        return new SessionRecoverySnapshot(
                1,
                inProgress,
                1L,
                1L,
                0L,
                1L,
                0L,
                2L,
                0L,
                lastEvent);
    }

    private PersistenceReadiness.Snapshot storage(boolean ready) {
        return new PersistenceReadiness.Snapshot(
                ready ? "READY" : "FAILED",
                3,
                ready ? 3 : 0,
                NOW,
                ready ? "ready" : "permission denied");
    }

    private PersistenceBackupService.Snapshot backups(String status, boolean enabled) {
        return new PersistenceBackupService.Snapshot(
                status,
                enabled,
                Duration.ofHours(6),
                7,
                "READY".equals(status) ? 3L : 0L,
                "FAILED".equals(status) ? 1L : 0L,
                NOW.minus(Duration.ofHours(1)),
                "FAILED".equals(status) ? NOW.minus(Duration.ofMinutes(5)) : null,
                "backup.zip",
                3,
                status.toLowerCase());
    }

    private OperationalMetrics.Snapshot commands(long total, long failures, Instant lastFailure) {
        long successes = Math.max(0L, total - failures);
        return new OperationalMetrics.Snapshot(
                NOW.minus(Duration.ofHours(1)),
                Duration.ofHours(1),
                0L,
                0L,
                successes,
                failures,
                0L,
                0L,
                NOW.minus(Duration.ofMinutes(1)),
                lastFailure);
    }
}
