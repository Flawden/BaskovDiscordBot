package ru.flawden.BascovDiscordBot.operations;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Non-blocking command counters plus the most recent success/failure timestamps.
 */
@Component
public class OperationalMetrics {

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
        failures.get(Objects.requireNonNull(channel, "channel")).increment();
        lastFailureAt.set(clock.instant());
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
