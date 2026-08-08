package ru.flawden.BascovDiscordBot.interactions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecentSearchHistoryTest {

    @Test
    void returnsNewestMatchingQueriesFirst() {
        RecentSearchHistory history = new RecentSearchHistory();
        history.remember(10L, "Sabaton The Last Stand");
        history.remember(10L, "Powerwolf Army of the Night");
        history.remember(10L, "Sabaton Bismarck");

        assertEquals(
                List.of("Sabaton Bismarck", "Sabaton The Last Stand"),
                history.suggest(10L, "sabaton"));
    }

    @Test
    void deduplicatesQueriesIgnoringCase() {
        RecentSearchHistory history = new RecentSearchHistory();
        history.remember(10L, "Bismarck");
        history.remember(10L, "bismarck");

        assertEquals(List.of("bismarck"), history.suggest(10L, ""));
    }

    @Test
    void directUrlsDoNotPolluteDiscoveryHistory() {
        RecentSearchHistory history = new RecentSearchHistory();
        history.remember(10L, "https://youtu.be/example");
        history.remember(10L, "Sabaton Bismarck");

        assertEquals(List.of("Sabaton Bismarck"), history.recent(10L, 10));
    }

    @Test
    void exposesRecentQueriesAndLastQuery() {
        RecentSearchHistory history = new RecentSearchHistory();
        history.remember(10L, "First");
        history.remember(10L, "Second");
        history.remember(10L, "Third");

        assertEquals(List.of("Third", "Second"), history.recent(10L, 2));
        assertEquals("Third", history.last(10L).orElseThrow());
        assertTrue(history.last(11L).isEmpty());
    }

    @Test
    void isolatesUsers() {
        RecentSearchHistory history = new RecentSearchHistory();
        history.remember(10L, "First user song");

        assertTrue(history.suggest(11L, "").isEmpty());
    }
}
