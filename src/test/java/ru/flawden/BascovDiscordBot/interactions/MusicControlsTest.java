package ru.flawden.BascovDiscordBot.interactions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicControlsTest {

    @Test
    void queuePageIdsRoundTrip() {
        String componentId = MusicControls.queuePageId(7);

        assertEquals(7, MusicControls.queuePage(componentId).orElseThrow());
        assertTrue(MusicControls.supports(componentId));
    }

    @Test
    void malformedQueuePageIdsAreIgnored() {
        assertFalse(MusicControls.queuePage("baskov:queue:page:nope").isPresent());
        assertFalse(MusicControls.queuePage("baskov:queue:page:0").isPresent());
        assertFalse(MusicControls.queuePage("other:queue:page:2").isPresent());
    }

    @Test
    void paginationRowAppearsOnlyWhenNavigationIsPossible() {
        assertEquals(1, MusicControls.queueRows(1, 1).size());
        assertEquals(2, MusicControls.queueRows(1, 3).size());
        assertEquals(2, MusicControls.queueRows(2, 3).size());
        assertEquals(2, MusicControls.queueRows(3, 3).size());
    }

    @Test
    void nowControlsExposePreviousRelativeSeekAndShuffle() {
        assertEquals(2, MusicControls.nowRows().size());
        assertTrue(MusicControls.supports(MusicControls.PREVIOUS));
        assertTrue(MusicControls.supports(MusicControls.SEEK_BACKWARD));
        assertTrue(MusicControls.supports(MusicControls.SEEK_FORWARD));
        assertTrue(MusicControls.supports(MusicControls.SHUFFLE));
    }

    @Test
    void pageIdsRejectNonPositiveNumbers() {
        assertThrows(IllegalArgumentException.class, () -> MusicControls.queuePageId(0));
    }
}
