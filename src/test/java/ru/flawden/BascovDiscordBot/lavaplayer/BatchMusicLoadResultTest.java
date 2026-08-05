package ru.flawden.BascovDiscordBot.lavaplayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchMusicLoadResultTest {

    @Test
    void reportsAcceptedTracks() {
        BatchMusicLoadResult result = new BatchMusicLoadResult(5, 1, 3, 1, null);
        assertEquals(4, result.accepted());
    }

    @Test
    void rejectsInconsistentCounters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BatchMusicLoadResult(5, 1, 2, 1, null));
    }
}
