package ru.flawden.BascovDiscordBot.interactions;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.library.StoredPlaylist;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoverySuggestionsTest {

    @Test
    void combinesRecentFavoritesHistoryAndPlaylistsWithoutDuplicates() {
        StoredTrack favoriteTrack = track("Poison", "Alice Cooper", "favorite-1");
        StoredTrack historyTrack = track("Bismarck", "Sabaton", "history-1");
        StoredTrack playlistTrack = track("Army of the Night", "Powerwolf", "playlist-1");
        StoredPlaylist playlist = new StoredPlaylist(
                "Metal",
                42L,
                1_700_000_000_000L,
                List.of(playlistTrack, historyTrack));

        assertEquals(
                List.of(
                        "Sabaton Bismarck live",
                        "Alice Cooper Poison",
                        "Sabaton Bismarck",
                        "Powerwolf Army of the Night"),
                DiscoverySuggestions.suggest(
                        "",
                        List.of("Sabaton Bismarck live"),
                        List.of(favoriteTrack),
                        List.of(historyTrack),
                        List.of(playlist)));
    }

    @Test
    void prioritizesPersonalHistoryBeforeGuildHistory() {
        StoredTrack personal = track("Personal Song", "My Artist", "personal");
        StoredTrack guild = track("Guild Song", "Other Artist", "guild");

        assertEquals(
                List.of("My Artist Personal Song", "Other Artist Guild Song"),
                DiscoverySuggestions.suggest(
                        "",
                        List.of(),
                        List.of(),
                        List.of(personal),
                        List.of(guild),
                        List.of()));
    }

    @Test
    void filtersCaseInsensitivelyAndKeepsRecentQueriesFirst() {
        StoredTrack historyTrack = track("The Last Stand", "Sabaton", "history-2");
        StoredTrack playlistTrack = track("Bismarck", "Sabaton", "playlist-2");

        assertEquals(
                List.of("sabaton live", "Sabaton The Last Stand", "Sabaton Bismarck"),
                DiscoverySuggestions.suggest(
                        "SABATON",
                        List.of("sabaton live", "powerwolf"),
                        List.of(),
                        List.of(historyTrack),
                        List.of(new StoredPlaylist(
                                "Metal",
                                42L,
                                1_700_000_000_000L,
                                List.of(playlistTrack)))));
    }

    @Test
    void derivesBoundedDiscoveryQuery() {
        assertEquals("Sabaton Bismarck", DiscoverySuggestions.discoveryQuery(" Sabaton ", " Bismarck "));
        assertEquals("Bismarck", DiscoverySuggestions.discoveryQuery("unknown artist", "Bismarck"));
        assertTrue(DiscoverySuggestions.discoveryQuery("a".repeat(80), "b".repeat(80)).length() <= 100);
    }

    private static StoredTrack track(String title, String author, String suffix) {
        return new StoredTrack(
                title,
                author,
                "https://www.youtube.com/watch?v=" + suffix,
                suffix,
                MediaProvider.YOUTUBE,
                180_000L,
                42L,
                "Tester",
                1_700_000_000_000L);
    }
}
