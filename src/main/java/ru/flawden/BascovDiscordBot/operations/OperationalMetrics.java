package ru.flawden.BascovDiscordBot.operations;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * Неблокирующие счётчики выполнения пользовательских команд.
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
    }

    public void recordFailure(Channel channel) {
        failures.get(Objects.requireNonNull(channel, "channel")).increment();
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
                count(failures, Channel.BUTTON));
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
            long buttonFailures) {

        public long totalSuccesses() {
            return prefixSuccesses + slashSuccesses + buttonSuccesses;
        }

        public long totalFailures() {
            return prefixFailures + slashFailures + buttonFailures;
        }
    }
}
