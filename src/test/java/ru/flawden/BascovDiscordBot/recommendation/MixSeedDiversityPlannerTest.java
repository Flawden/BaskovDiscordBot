package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MixSeedDiversityPlannerTest {

    @Test
    void spreadsArtistsBeforeReusingOneAndNeverDropsSeeds() {
        List<StoredTrack> source = List.of(
                track("A1", "Artist A", "a1"),
                track("A2", "Artist A", "a2"),
                track("A3", "Artist A", "a3"),
                track("B1", "Artist B", "b1"),
                track("C1", "Artist C", "c1"));

        List<StoredTrack> planned = MixSeedDiversityPlanner.spreadArtists(source);

        assertEquals(source.size(), planned.size());
        assertEquals(source.stream().map(StoredTrack::playbackIdentifier).sorted().toList(),
                planned.stream().map(StoredTrack::playbackIdentifier).sorted().toList());
        assertNotEquals(planned.get(0).author(), planned.get(1).author());
        assertNotEquals(planned.get(1).author(), planned.get(2).author());
    }

    @Test
    void preservesSingleArtistPoolWithoutDroppingTracks() {
        List<StoredTrack> source = List.of(
                track("A1", "Artist A", "a1"),
                track("A2", "Artist A", "a2"));

        assertEquals(source, MixSeedDiversityPlanner.spreadArtists(source));
    }

    private static StoredTrack track(String title, String author, String id) {
        return new StoredTrack(
                title,
                author,
                "https://www.youtube.com/watch?v=" + id,
                id,
                MediaProvider.YOUTUBE,
                180_000L,
                7L,
                "user",
                1_800_000_000_000L);
    }
}
