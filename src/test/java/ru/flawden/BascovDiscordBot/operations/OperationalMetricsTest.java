package ru.flawden.BascovDiscordBot.operations;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalMetricsTest {

    @Test
    void recordsIndependentSuccessAndFailureCounters() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-04T00:00:00Z"));
        OperationalMetrics metrics = new OperationalMetrics(clock);

        metrics.recordSuccess(OperationalMetrics.Channel.PREFIX);
        clock.advance(Duration.ofMinutes(1));
        metrics.recordSuccess(OperationalMetrics.Channel.SLASH);
        metrics.recordSuccess(OperationalMetrics.Channel.SLASH);
        clock.advance(Duration.ofMinutes(1));
        metrics.recordFailure(OperationalMetrics.Channel.BUTTON);
        clock.advance(Duration.ofMinutes(3));

        OperationalMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(1, snapshot.prefixSuccesses());
        assertEquals(2, snapshot.slashSuccesses());
        assertEquals(1, snapshot.buttonFailures());
        assertEquals(3, snapshot.totalSuccesses());
        assertEquals(1, snapshot.totalFailures());
        assertEquals(4, snapshot.totalInvocations());
        assertEquals(25.0d, snapshot.failureRatePercent(), 0.001d);
        assertEquals(Instant.parse("2026-08-04T00:01:00Z"), snapshot.lastSuccessAt());
        assertEquals(Instant.parse("2026-08-04T00:02:00Z"), snapshot.lastFailureAt());
        assertEquals(Duration.ofMinutes(5), snapshot.uptime());
    }

    @Test
    void emptySnapshotStartsAtZero() {
        OperationalMetrics metrics = new OperationalMetrics(
                Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneId.of("UTC")));

        OperationalMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(0, snapshot.totalSuccesses());
        assertEquals(0, snapshot.totalFailures());
        assertEquals(0, snapshot.totalInvocations());
        assertEquals(0.0d, snapshot.failureRatePercent(), 0.001d);
        assertNull(snapshot.lastSuccessAt());
        assertNull(snapshot.lastFailureAt());
        assertEquals(Duration.ZERO, snapshot.uptime());
    }

    @Test
    void recentFailureJournalIsBoundedNewestFirstAndSanitized() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-04T00:00:00Z"));
        OperationalMetrics metrics = new OperationalMetrics(clock);

        for (int index = 0; index < 30; index++) {
            metrics.recordFailure(
                    OperationalMetrics.Channel.SLASH,
                    "/play-" + index,
                    new IllegalStateException("failure `" + index + "`\nsecond line"));
            clock.advance(Duration.ofSeconds(1));
        }

        var failures = metrics.recentFailures(OperationalMetrics.MAX_RECENT_FAILURES + 10);
        assertEquals(OperationalMetrics.MAX_RECENT_FAILURES, failures.size());
        assertEquals("/play-29", failures.get(0).operation());
        assertEquals("/play-5", failures.get(failures.size() - 1).operation());
        assertTrue(failures.get(0).message().contains("failure '29' second line"));
        assertEquals("IllegalStateException", failures.get(0).errorType());
        assertEquals(30, metrics.snapshot().totalFailures());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
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
