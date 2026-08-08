package ru.flawden.BascovDiscordBot.operations;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeHealthMonitorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writesConnectedHeartbeatAndRemovesItOnClose() throws Exception {
        Path healthFile = tempDirectory.resolve("ready");
        JDA jda = mock(JDA.class);
        when(jda.getStatus()).thenReturn(JDA.Status.CONNECTED);
        when(jda.getGuilds()).thenReturn(List.of());

        RuntimeHealthMonitor monitor = new RuntimeHealthMonitor(healthFile);
        monitor.start(jda, 18);

        String payload = Files.readString(healthFile);
        assertTrue(payload.contains("status=CONNECTED"));
        assertTrue(payload.contains("slashCommands=18"));
        assertTrue(payload.contains("gatewayTransitions=0"));
        assertTrue(payload.contains("disconnectedSamples=0"));

        monitor.close();
        assertFalse(Files.exists(healthFile));
    }

    @Test
    void countsGatewayTransitionsAndDisconnectedHeartbeatSamples() {
        Path healthFile = tempDirectory.resolve("ready-transitions");
        MutableClock clock = new MutableClock(Instant.parse("2026-08-08T10:00:00Z"));
        JDA jda = mock(JDA.class);
        when(jda.getGuilds()).thenReturn(List.of());
        when(jda.getStatus()).thenReturn(JDA.Status.CONNECTED);

        RuntimeHealthMonitor monitor = new RuntimeHealthMonitor(healthFile, clock);
        monitor.start(jda, 20);

        clock.advanceSeconds(10);
        when(jda.getStatus()).thenReturn(JDA.Status.DISCONNECTED);
        monitor.heartbeat();

        clock.advanceSeconds(10);
        when(jda.getStatus()).thenReturn(JDA.Status.CONNECTED);
        monitor.heartbeat();

        RuntimeHealthMonitor.Snapshot snapshot = monitor.snapshot();
        assertEquals("CONNECTED", snapshot.jdaStatus());
        assertEquals(2L, snapshot.gatewayStatusTransitions());
        assertEquals(1L, snapshot.disconnectedHeartbeatSamples());
        assertEquals(Instant.parse("2026-08-08T10:00:20Z"), snapshot.lastConnectedAt());
        assertEquals(Instant.parse("2026-08-08T10:00:20Z"), snapshot.lastStatusChangeAt());

        monitor.close();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
