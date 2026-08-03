package ru.flawden.BascovDiscordBot.interactions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackPositionParserTest {

    @Test
    void parsesSecondsMinutesAndHours() {
        assertEquals(90_000L, PlaybackPositionParser.parseMillis("90").orElseThrow());
        assertEquals(90_000L, PlaybackPositionParser.parseMillis("01:30").orElseThrow());
        assertEquals(3_690_000L, PlaybackPositionParser.parseMillis("01:01:30").orElseThrow());
    }

    @Test
    void rejectsInvalidClockFields() {
        assertTrue(PlaybackPositionParser.parseMillis("1:99").isEmpty());
        assertTrue(PlaybackPositionParser.parseMillis("1:60:00").isEmpty());
        assertTrue(PlaybackPositionParser.parseMillis("1::2").isEmpty());
    }

    @Test
    void rejectsBlankAndNonNumericInput() {
        assertTrue(PlaybackPositionParser.parseMillis("").isEmpty());
        assertTrue(PlaybackPositionParser.parseMillis("one minute").isEmpty());
    }
}
