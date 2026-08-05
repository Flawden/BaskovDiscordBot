package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class QueuePageTest {

    @Test
    void emptyQueueStillHasStableFirstPage() {
        QueuePage page = QueuePage.of(List.of(), 8);

        assertEquals(1, page.number());
        assertEquals(1, page.totalPages());
        assertEquals(0, page.totalItems());
        assertEquals(0, page.firstPosition());
        assertEquals(0, page.lastPosition());
        assertTrue(page.items().isEmpty());
    }

    @Test
    void firstPageContainsFirstTenGlobalPositions() {
        QueuePage page = QueuePage.of(requests(23), 1);

        assertEquals(1, page.number());
        assertEquals(3, page.totalPages());
        assertEquals(1, page.firstPosition());
        assertEquals(10, page.lastPosition());
        assertEquals(10, page.items().size());
        assertFalse(page.hasPrevious());
        assertTrue(page.hasNext());
    }

    @Test
    void middlePageKeepsGlobalQueuePositions() {
        QueuePage page = QueuePage.of(requests(23), 2);

        assertEquals(11, page.firstPosition());
        assertEquals(20, page.lastPosition());
        assertTrue(page.hasPrevious());
        assertTrue(page.hasNext());
    }

    @Test
    void pagePastEndClampsToLastAvailablePage() {
        QueuePage page = QueuePage.of(requests(23), 999);

        assertEquals(3, page.number());
        assertEquals(21, page.firstPosition());
        assertEquals(23, page.lastPosition());
        assertEquals(3, page.items().size());
        assertTrue(page.hasPrevious());
        assertFalse(page.hasNext());
    }

    @Test
    void nonPositivePageClampsToFirstPage() {
        QueuePage page = QueuePage.of(requests(12), -5);

        assertEquals(1, page.number());
        assertEquals(1, page.firstPosition());
        assertEquals(10, page.lastPosition());
    }

    private static List<TrackRequest> requests(int size) {
        return IntStream.range(0, size)
                .mapToObj(ignored -> TrackRequest.create(
                        mock(AudioTrack.class),
                        TrackRequester.unknown()))
                .toList();
    }
}
