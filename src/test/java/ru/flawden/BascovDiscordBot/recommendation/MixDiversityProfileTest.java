package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixDiversityProfileTest {

    @Test
    void normalizesThemeAndPreservesOrderedArtistOccurrences() {
        MixDiversityProfile profile = new MixDiversityProfile(
                true,
                "  Pop   Punk  ",
                List.of("Artist A", "Artist A", "Artist B"),
                List.of(Set.of("Pop Punk", "Rock")));

        assertEquals("pop punk", profile.themeFocus());
        assertEquals(2, profile.recentArtistOccurrences("ARTIST A"));
        assertTrue(profile.repeatsImmediateArtist("artist a"));
    }

    @Test
    void tagShareUsesBoundedTrackWindow() {
        MixDiversityProfile profile = new MixDiversityProfile(
                true,
                "",
                List.of(),
                List.of(Set.of("rock"), Set.of("rock"), Set.of("pop"), Set.of("rock")));

        assertEquals(0.75d, profile.recentTagShare("rock"), 0.0001d);
    }
}
