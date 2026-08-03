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
    void isolatesUsers() {
        RecentSearchHistory history = new RecentSearchHistory();
        history.remember(10L, "First user song");

        assertTrue(history.suggest(11L, "").isEmpty());
    }
}
