package ru.flawden.BascovDiscordBot.product;

import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductPlaybackStreamServiceTest {

    @Test
    void opensProviderNeutralTrackIdentityThroughRuntimePort() {
        AtomicReference<String> stableKey = new AtomicReference<>();
        ProductPlaybackStreamService service = new ProductPlaybackStreamService((guildId, userId, track, startPositionMillis) -> {
            assertEquals(10L, guildId);
            assertEquals(20L, userId);
            assertEquals(12_345L, startPositionMillis);
            stableKey.set(track.stableKey());
            return emptySession();
        });

        try (var ignored = service.open(10L, 20L, "Skillet", "Monster", 12_345L)) {
            assertEquals("skillet::monster", stableKey.get());
        }
    }

    @Test
    void rejectsInvalidScopeBeforeOpeningRuntimeStream() {
        ProductPlaybackStreamService service = new ProductPlaybackStreamService((guildId, userId, track, startPositionMillis) -> emptySession());

        assertThrows(IllegalArgumentException.class, () -> service.open(0L, 20L, "Skillet", "Monster"));
        assertThrows(IllegalArgumentException.class, () -> service.open(10L, 0L, "Skillet", "Monster"));
        assertThrows(IllegalArgumentException.class, () -> service.open(10L, 20L, "Skillet", "Monster", -1L));
    }

    private static ProductPlaybackStreamSession emptySession() {
        return new ProductPlaybackStreamSession() {
            @Override public long durationMillis() { return 1L; }
            @Override public void writeOgg(OutputStream output) { }
            @Override public void close() { }
        };
    }
}
