package ru.flawden.BascovDiscordBot.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaybackAccessModeTest {

    @Test
    void parsesStableCommandValues() {
        assertEquals(PlaybackAccessMode.OPEN, PlaybackAccessMode.parse("open"));
        assertEquals(PlaybackAccessMode.DJ_ONLY, PlaybackAccessMode.parse("dj"));
        assertEquals(PlaybackAccessMode.VOTE_SKIP, PlaybackAccessMode.parse("vote"));
    }

    @Test
    void blankValueKeepsBackwardCompatibleOpenMode() {
        assertEquals(PlaybackAccessMode.OPEN, PlaybackAccessMode.parse(" "));
    }

    @Test
    void rejectsUnknownMode() {
        assertThrows(IllegalArgumentException.class, () -> PlaybackAccessMode.parse("chaos"));
    }
}
