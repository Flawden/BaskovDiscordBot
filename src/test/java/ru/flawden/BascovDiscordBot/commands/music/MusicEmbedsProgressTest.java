package ru.flawden.BascovDiscordBot.commands.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MusicEmbedsProgressTest {

    @Test
    void zeroDurationProducesEmptyProgressBar() {
        assertEquals("░░░░", MusicEmbeds.progressBar(0L, 0L, 4));
    }

    @Test
    void halfPositionProducesHalfFilledProgressBar() {
        assertEquals("██░░", MusicEmbeds.progressBar(5_000L, 10_000L, 4));
    }

    @Test
    void positionIsClampedToTrackDuration() {
        assertEquals("████", MusicEmbeds.progressBar(99_000L, 10_000L, 4));
        assertEquals("░░░░", MusicEmbeds.progressBar(-1L, 10_000L, 4));
    }

    @Test
    void widthMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> MusicEmbeds.progressBar(0L, 1L, 0));
    }
}
