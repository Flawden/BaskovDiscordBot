package ru.flawden.BascovDiscordBot.interactions;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmationStoreTest {

    @Test
    void confirmationIsBoundToGuildAndUserAndCanOnlyBeClaimedOnce() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-09T05:00:00Z"));
        ConfirmationStore store = new ConfirmationStore(clock, Duration.ofMinutes(2));
        var pending = store.create(ConfirmationStore.Action.DELETE_PLAYLIST, 10L, 20L, "Roadtrip");

        assertEquals(ConfirmationStore.ClaimStatus.FORBIDDEN,
                store.claim(pending.token(), 10L, 21L).status());

        var claimed = store.claim(pending.token(), 10L, 20L);
        assertTrue(claimed.claimed());
        assertEquals("Roadtrip", claimed.confirmation().payload());
        assertEquals(ConfirmationStore.Action.DELETE_PLAYLIST, claimed.confirmation().action());

        assertFalse(store.claim(pending.token(), 10L, 20L).claimed());
        assertEquals(ConfirmationStore.ClaimStatus.MISSING,
                store.claim(pending.token(), 10L, 20L).status());
    }

    @Test
    void expiredConfirmationNeverExecutes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-09T05:00:00Z"));
        ConfirmationStore store = new ConfirmationStore(clock, Duration.ofSeconds(30));
        var pending = store.create(ConfirmationStore.Action.STOP, 1L, 2L, "track");
        assertNotNull(pending.token());

        clock.advance(Duration.ofSeconds(31));

        assertEquals(ConfirmationStore.ClaimStatus.EXPIRED,
                store.claim(pending.token(), 1L, 2L).status());
    }

    @Test
    void cancellationConsumesConfirmation() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-09T05:00:00Z"));
        ConfirmationStore store = new ConfirmationStore(clock, Duration.ofMinutes(2));
        var pending = store.create(ConfirmationStore.Action.CLEAR_QUEUE, 5L, 6L, "4");

        assertEquals(ConfirmationStore.ClaimStatus.CANCELLED,
                store.cancel(pending.token(), 5L, 6L));
        assertEquals(ConfirmationStore.ClaimStatus.MISSING,
                store.claim(pending.token(), 5L, 6L).status());
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
