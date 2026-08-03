package ru.flawden.BascovDiscordBot.config.eventconfig;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandCooldownsTest {

    @Test
    void isolatesCooldownByGuildUserAndCommand() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-03T00:00:00Z"));
        CommandCooldowns cooldowns = new CommandCooldowns(clock);

        assertTrue(cooldowns.tryAcquire(1, 10, "search", Duration.ofSeconds(3)).allowed());
        assertFalse(cooldowns.tryAcquire(1, 10, "search", Duration.ofSeconds(3)).allowed());
        assertTrue(cooldowns.tryAcquire(1, 11, "search", Duration.ofSeconds(3)).allowed());
        assertTrue(cooldowns.tryAcquire(1, 10, "help", Duration.ofSeconds(3)).allowed());

        clock.advance(Duration.ofSeconds(3));
        assertTrue(cooldowns.tryAcquire(1, 10, "search", Duration.ofSeconds(3)).allowed());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
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
