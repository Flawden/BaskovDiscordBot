package ru.flawden.BascovDiscordBot.library;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalListeningInsightsTest {

    @Test
    void ranksRepeatedTracksAndUsesRecencyAsTieBreaker() {
        StoredTrack newest = track("Poison", "Alice Cooper", "poison", 10L);
        StoredTrack older = track("Bismarck", "Sabaton", "bismarck", 9L);

        List<PersonalTrackStat> top = PersonalListeningInsights.topTracks(
                List.of(newest, older, newest, older, newest),
                5);

        assertEquals("Poison", top.get(0).track().title());
        assertEquals(3, top.get(0).plays());
        assertEquals("Bismarck", top.get(1).track().title());
        assertEquals(2, top.get(1).plays());
    }

    @Test
    void aggregatesArtistsCaseInsensitivelyAndIgnoresUnknown() {
        List<PersonalArtistStat> artists = PersonalListeningInsights.topArtists(
                List.of(
                        track("One", "Sabaton", "1", 10L),
                        track("Two", "sabaton", "2", 9L),
                        track("Three", "Powerwolf", "3", 8L),
                        track("Unknown", "unknown artist", "4", 7L)),
                5);

        assertEquals("Sabaton", artists.get(0).artist());
        assertEquals(2, artists.get(0).plays());
        assertEquals("Powerwolf", artists.get(1).artist());
        assertEquals(1, artists.get(1).plays());
        assertEquals(2, artists.size());
    }

    @Test
    void discoverySeedCombinesExplicitFavoritesAndRepeatHistory() {
        StoredTrack favorite = track("Poison", "Alice Cooper", "poison", 10L);
        StoredTrack repeated = track("Bismarck", "Sabaton", "bismarck", 9L);

        assertEquals(
                "Poison",
                PersonalListeningInsights.discoverySeed(
                                List.of(favorite),
                                List.of(repeated, repeated, repeated))
                        .orElseThrow()
                        .title());

        assertEquals(
                "Bismarck",
                PersonalListeningInsights.discoverySeed(
                                List.of(),
                                List.of(repeated, repeated, favorite))
                        .orElseThrow()
                        .title());
    }

    @Test
    void countsUniqueTracksByReplayIdentity() {
        StoredTrack first = track("First title", "Artist", "same", 10L);
        StoredTrack renamed = track("Renamed", "Artist", "same", 9L);
        StoredTrack other = track("Other", "Artist", "other", 8L);

        assertEquals(2, PersonalListeningInsights.uniqueTrackCount(List.of(first, renamed, other)));
        assertTrue(PersonalListeningInsights.discoverySeed(List.of(), List.of()).isEmpty());
    }

    private static StoredTrack track(String title, String author, String id, long captured) {
        return new StoredTrack(
                title,
                author,
                "https://www.youtube.com/watch?v=" + id,
                id,
                MediaProvider.YOUTUBE,
                180_000L,
                42L,
                "Tester",
                1_700_000_000_000L + captured);
    }
}
