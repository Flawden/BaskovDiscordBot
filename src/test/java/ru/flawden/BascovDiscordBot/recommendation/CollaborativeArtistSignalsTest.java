package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollaborativeArtistSignalsTest {

    @Test
    void normalizesArtistNamesAndClampsScores() {
        CollaborativeArtistSignals signals = new CollaborativeArtistSignals(
                "ListenBrainz",
                Map.of("  Green   Day ", 2.0d, "Other Artist", -1.0d));

        assertEquals(1.0d, signals.affinity("green day"), 0.0001d);
        assertEquals(0.0d, signals.affinity("OTHER ARTIST"), 0.0001d);
        assertTrue(signals.available());
    }

    @Test
    void emptySignalsAreUnavailable() {
        assertFalse(CollaborativeArtistSignals.empty().available());
    }
}
