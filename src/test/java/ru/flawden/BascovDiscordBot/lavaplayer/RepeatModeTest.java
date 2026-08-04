package ru.flawden.BascovDiscordBot.lavaplayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepeatModeTest {

    @Test
    void parsesStableDiscordValuesCaseInsensitively() {
        assertEquals(RepeatMode.OFF, RepeatMode.parse("off"));
        assertEquals(RepeatMode.TRACK, RepeatMode.parse("TRACK"));
        assertEquals(RepeatMode.QUEUE, RepeatMode.parse(" queue "));
        assertThrows(IllegalArgumentException.class, () -> RepeatMode.parse("forever"));
    }

    @Test
    void cyclesThroughAllModes() {
        assertEquals(RepeatMode.TRACK, RepeatMode.OFF.next());
        assertEquals(RepeatMode.QUEUE, RepeatMode.TRACK.next());
        assertEquals(RepeatMode.OFF, RepeatMode.QUEUE.next());
    }
}
