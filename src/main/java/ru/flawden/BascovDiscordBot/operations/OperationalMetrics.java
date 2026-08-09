package ru.flawden.BascovDiscordBot.operations;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Non-blocking command counters plus a bounded, privacy-safe recent failure journal.
 */
@Component
public class OperationalMetrics {

    public static final int MAX_RECENT_FAILURES = 25;

    public enum Channel {
        PREFIX,
        SLASH,
        BUTTON
    }

    private final Clock clock;
    private final Instant startedAt;
    private final Map<Channel, LongAdder> successes = new EnumMap<>(Channel.class);
    private final Map<Channel, LongAdder> failures = new EnumMap<>(Channel.class);
    private final AtomicReference<Instant> lastSuccessAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastFailureAt = new AtomicReference<>();
    private final Object failureJournalLock = new Object();
    private final ArrayDeque<FailureEvent> recentFailures = new ArrayDeque<>();

    public OperationalMetrics() {
        this(Clock.systemUTC());
    }

    OperationalMetrics(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.startedAt = clock.instant();
        for (Channel channel : Channel.values()) {
            successes.put(channel, new LongAdder());
            failures.put(channel, new LongAdder());
        }
    }

    public void recordSuccess(Channel channel) {
        successes.get(Objects.requireNonNull(channel, "channel")).increment();
        lastSuccessAt.set(clock.instant());
    }

    public void recordFailure(Channel channel) {
        recordFailure(channel, "unknown", null);
    }

    public void recordFailure(Channel channel, String operation, Throwable failure) {
        Channel safeChannel = Objects.requireNonNull(channel, "channel");
        failures.get(safeChannel).increment();
        Instant now = clock.instant();
        lastFailureAt.set(now);

        FailureEvent event = new FailureEvent(
                now,
                safeChannel,
                sanitize(operation, "unknown"),
                failure == null ? "unknown" : failure.getClass().getSimpleName(),
                failure == null ? "no details" : sanitize(failure.getMessage(), failure.getClass().getSimpleName()));
        synchronized (failureJournalLock) {
            recentFailures.addFirst(event);
            while (recentFailures.size() > MAX_RECENT_FAILURES) {
                recentFailures.removeLast();
            }
        }
    }

    public List<FailureEvent> recentFailures(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int bounded = Math.min(limit, MAX_RECENT_FAILURES);
        synchronized (failureJournalLock) {
            List<FailureEvent> copy = new ArrayList<>(Math.min(bounded, recentFailures.size()));
            int index = 0;
            for (FailureEvent event : recentFailures) {
                if (index++ >= bounded) {
                    break;
                }
                copy.add(event);
            }
            return List.copyOf(copy);
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(
                startedAt,
                Duration.between(startedAt, clock.instant()),
                count(successes, Channel.PREFIX),
                count(failures, Channel.PREFIX),
                count(successes, Channel.SLASH),
                count(failures, Channel.SLASH),
                count(successes, Channel.BUTTON),
                count(failures, Channel.BUTTON),
                lastSuccessAt.get(),
                lastFailureAt.get());
    }

    private long count(Map<Channel, LongAdder> source, Channel channel) {
        return source.get(channel).sum();
    }

    private static String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String compact = value.replace('\n', ' ').replace('\r', ' ').replace('`', '\'').trim();
        compact = compact.replaceAll("\\b\\d{15,20}\\b", "<id>")
                .replaceAll("(?i)(token|secret|password|authorization)\\s*[:=]\\s*\\S+", "$1=<redacted>");
        return compact.length() <= 180 ? compact : compact.substring(0, 177) + "...";
    }

    public record FailureEvent(
            Instant at,
            Channel channel,
            String operation,
            String errorType,
            String message) {
    }

    public record Snapshot(
            Instant startedAt,
            Duration uptime,
            long prefixSuccesses,
            long prefixFailures,
            long slashSuccesses,
            long slashFailures,
            long buttonSuccesses,
            long buttonFailures,
            Instant lastSuccessAt,
            Instant lastFailureAt) {

        public long totalSuccesses() {
            return prefixSuccesses + slashSuccesses + buttonSuccesses;
        }

        public long totalFailures() {
            return prefixFailures + slashFailures + buttonFailures;
        }

        public long totalInvocations() {
            return totalSuccesses() + totalFailures();
        }

        public double failureRatePercent() {
            long total = totalInvocations();
            return total == 0L ? 0.0d : (totalFailures() * 100.0d) / total;
        }
    }
}
