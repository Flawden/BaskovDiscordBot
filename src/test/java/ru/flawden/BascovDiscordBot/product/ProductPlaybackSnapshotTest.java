package ru.flawden.BascovDiscordBot.product;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductPlaybackSnapshotTest {

    @Test
    void idleSnapshotIsSafeAndProviderNeutral() {
        ProductPlaybackSnapshot idle = ProductPlaybackSnapshot.idle(42L);

        assertFalse(idle.sessionActive());
        assertFalse(idle.playing());
        assertEquals(Optional.empty(), idle.current());
        assertFalse(idle.radio().enabled());
    }

    @Test
    void sanitizesNegativeRuntimeCounters() {
        ProductPlaybackSnapshot snapshot = new ProductPlaybackSnapshot(
                42L, true, false, false, -5, "", -2, -10L, -20L,
                Optional.empty(), null);

        assertEquals(0, snapshot.volume());
        assertEquals(0, snapshot.queueSize());
        assertEquals(0L, snapshot.positionMillis());
        assertEquals(0L, snapshot.durationMillis());
        assertEquals("OFF", snapshot.repeatMode());
    }

    @Test
    void rejectsInvalidGuildIdentity() {
        assertThrows(IllegalArgumentException.class, () -> ProductPlaybackSnapshot.idle(0L));
    }
}
