package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyMixSeedPlannerTest {

    @Test
    void sameUserStationAndDayProducesStableOrder() {
        List<StoredTrack> source = tracks(12);
        LocalDate day = LocalDate.of(2026, 8, 10);

        List<StoredTrack> first = DailyMixSeedPlanner.plan(
                source, 42L, 7L, PersonalizedStation.DAILY_MIX, day, 8);
        List<StoredTrack> second = DailyMixSeedPlanner.plan(
                source, 42L, 7L, PersonalizedStation.DAILY_MIX, day, 8);

        assertEquals(first, second);
        assertEquals(8, first.size());
    }

    @Test
    void nextDayRotatesTheBoundedSeedOrder() {
        List<StoredTrack> source = tracks(12);

        List<StoredTrack> today = DailyMixSeedPlanner.plan(
                source, 42L, 7L, PersonalizedStation.DAILY_MIX, LocalDate.of(2026, 8, 10), 8);
        List<StoredTrack> tomorrow = DailyMixSeedPlanner.plan(
                source, 42L, 7L, PersonalizedStation.DAILY_MIX, LocalDate.of(2026, 8, 11), 8);

        assertNotEquals(today, tomorrow);
    }

    @Test
    void stationIdentityParticipatesInDailyRotation() {
        List<StoredTrack> source = tracks(12);
        LocalDate day = LocalDate.of(2026, 8, 10);

        List<StoredTrack> mix = DailyMixSeedPlanner.plan(
                source, 42L, 7L, PersonalizedStation.DAILY_MIX, day, 8);
        List<StoredTrack> discoveries = DailyMixSeedPlanner.plan(
                source, 42L, 7L, PersonalizedStation.DAILY_DISCOVERIES, day, 8);

        assertNotEquals(mix, discoveries);
    }

    @Test
    void plannerIsBoundedAndDoesNotMutateSource() {
        List<StoredTrack> source = new ArrayList<>(tracks(12));
        List<StoredTrack> original = List.copyOf(source);

        List<StoredTrack> planned = DailyMixSeedPlanner.plan(
                source, 1L, 2L, PersonalizedStation.DAILY_MIX, LocalDate.of(2026, 8, 10), 3);

        assertEquals(3, planned.size());
        assertEquals(original, source);
        assertTrue(original.containsAll(planned));
    }

    private static List<StoredTrack> tracks(int count) {
        List<StoredTrack> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(new StoredTrack(
                    "Track " + i,
                    "Artist " + (i % 4),
                    "https://youtube.com/watch?v=daily" + i,
                    "daily" + i,
                    MediaProvider.YOUTUBE,
                    180_000L,
                    7L,
                    "tester",
                    1_700_000_000_000L + i));
        }
        return List.copyOf(result);
    }
}
