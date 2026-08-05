package ru.flawden.BascovDiscordBot.interactions;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchSelectionStoreTest {

    @Test
    void selectionIsBoundToGuildUserAndCanBeClaimedOnlyOnce() {
        SearchSelectionStore store = new SearchSelectionStore();
        AudioTrack source = mock(AudioTrack.class);
        AudioTrack clone = mock(AudioTrack.class);
        when(source.makeClone()).thenReturn(clone);

        SearchSelectionStore.SearchSession session = store.create(
                10L,
                20L,
                "green day holiday",
                List.of(source));

        assertEquals(SearchSelectionStore.ClaimStatus.FORBIDDEN,
                store.claim(session.token(), 1, 10L, 21L).status());

        SearchSelectionStore.ClaimResult claimed = store.claim(
                session.token(),
                1,
                10L,
                20L);
        assertTrue(claimed.claimed());
        assertSame(clone, claimed.track());
        assertEquals(1, claimed.oneBasedIndex());

        assertEquals(SearchSelectionStore.ClaimStatus.NOT_FOUND,
                store.claim(session.token(), 1, 10L, 20L).status());
        assertEquals(0, store.activeSessionCount());
    }

    @Test
    void createRejectsEmptyResultsAndCapsCandidates() {
        SearchSelectionStore store = new SearchSelectionStore();
        assertThrows(IllegalArgumentException.class,
                () -> store.create(1L, 2L, "empty", List.of()));

        List<AudioTrack> candidates = java.util.stream.IntStream.range(0, 7)
                .mapToObj(index -> mock(AudioTrack.class))
                .toList();
        SearchSelectionStore.SearchSession session = store.create(1L, 2L, "many", candidates);

        assertEquals(SearchSelectionStore.MAX_CANDIDATES, session.candidates().size());
        assertFalse(session.expiresAt().isBefore(session.createdAt()));
    }

    @Test
    void cancelRespectsOwnershipAndRemovesSession() {
        SearchSelectionStore store = new SearchSelectionStore();
        SearchSelectionStore.SearchSession session = store.create(
                1L,
                2L,
                "query",
                List.of(mock(AudioTrack.class)));

        assertEquals(SearchSelectionStore.ClaimStatus.FORBIDDEN,
                store.cancel(session.token(), 1L, 3L));
        assertEquals(SearchSelectionStore.ClaimStatus.CANCELLED,
                store.cancel(session.token(), 1L, 2L));
        assertEquals(SearchSelectionStore.ClaimStatus.NOT_FOUND,
                store.cancel(session.token(), 1L, 2L));
    }
}
