package ru.flawden.BascovDiscordBot.home;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.library.StoredTrack;
import ru.flawden.BascovDiscordBot.recommendation.PersonalTasteProfile;
import ru.flawden.BascovDiscordBot.recommendation.PersonalizedStation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicHomeServiceTest {

    @Test
    void buildsStableProductSectionsFromExistingLibraryAndTasteData() {
        FakePort port = new FakePort();
        port.favorites = List.of(track("Favorite", "Artist A", 4));
        port.history = List.of(
                track("Newest", "Artist C", 3),
                track("Middle", "Artist B", 2),
                track("Oldest", "Artist A", 1),
                track("Outside preview", "Artist Z", 0));
        port.taste = new PersonalTasteProfile(
                7,
                5,
                1,
                Map.of(),
                Map.of(),
                new LinkedHashMap<>(Map.of(
                        "pop punk", 4.0d,
                        "alternative rock", 2.0d,
                        "bad-tag", -9.0d)));

        HomeSnapshot snapshot = new MusicHomeService(port).snapshot(10L, 20L);

        assertEquals(2, snapshot.today().size());
        assertEquals(List.of("daily-mix", "daily-discoveries"), snapshot.today().stream()
                .map(HomeSnapshot.MixCard::stationSlug)
                .toList());
        assertEquals(4, snapshot.forYou().size());
        assertEquals(1, snapshot.library().favorites());
        assertEquals(4, snapshot.library().personalHistory());
        assertEquals(3, snapshot.recent().size());
        assertEquals("Newest", snapshot.recent().get(0).title());
        assertEquals(List.of("pop punk", "alternative rock"), snapshot.themes().stream()
                .map(HomeSnapshot.ThemeCard::name)
                .toList());
        assertEquals(6, snapshot.taste().evidenceSignals());
        assertEquals(0.75d, snapshot.taste().confidence(), 0.0001d);
    }

    @Test
    void activeOwnedStationWinsOverResumableContinuation() {
        FakePort port = new FakePort();
        port.active = Optional.of(new MusicHomeReadPort.StationState(
                PersonalizedStation.DAILY_MIX,
                "",
                LocalDate.of(2026, 8, 10),
                4));
        port.resumable = Optional.of(new MusicHomeReadPort.StationState(
                PersonalizedStation.THEME,
                "pop punk",
                null,
                7));

        HomeSnapshot snapshot = new MusicHomeService(port).snapshot(10L, 20L);

        var continuation = snapshot.continuation().orElseThrow();
        assertEquals(HomeSnapshot.ContinuationCard.Kind.ACTIVE, continuation.kind());
        assertEquals("daily-mix", continuation.stationSlug());
        assertEquals(4, continuation.generatedTracks());
    }

    @Test
    void resumableStationAppearsWhenNothingIsCurrentlyActive() {
        FakePort port = new FakePort();
        port.resumable = Optional.of(new MusicHomeReadPort.StationState(
                PersonalizedStation.THEME,
                "pop punk",
                null,
                7));

        HomeSnapshot snapshot = new MusicHomeService(port).snapshot(10L, 20L);

        var continuation = snapshot.continuation().orElseThrow();
        assertEquals(HomeSnapshot.ContinuationCard.Kind.RESUMABLE, continuation.kind());
        assertEquals("theme", continuation.stationSlug());
        assertEquals("pop punk", continuation.theme());
    }

    @Test
    void mixAvailabilityIsReadOnlyCapabilityNotHomeSideEffect() {
        FakePort port = new FakePort();
        port.unavailable.add(PersonalizedStation.DAILY_DISCOVERIES);
        port.unavailable.add(PersonalizedStation.DISCOVERIES);

        HomeSnapshot snapshot = new MusicHomeService(port).snapshot(10L, 20L);

        assertTrue(snapshot.today().stream()
                .filter(card -> card.stationSlug().equals("daily-mix"))
                .findFirst().orElseThrow().available());
        assertFalse(snapshot.today().stream()
                .filter(card -> card.stationSlug().equals("daily-discoveries"))
                .findFirst().orElseThrow().available());
        assertFalse(snapshot.forYou().stream()
                .filter(card -> card.stationSlug().equals("discoveries"))
                .findFirst().orElseThrow().available());
    }

    @Test
    void rejectsMissingProductIdentity() {
        MusicHomeService service = new MusicHomeService(new FakePort());
        boolean guildRejected = false;
        boolean userRejected = false;
        try {
            service.snapshot(0L, 2L);
        } catch (IllegalArgumentException expected) {
            guildRejected = true;
        }
        try {
            service.snapshot(1L, 0L);
        } catch (IllegalArgumentException expected) {
            userRejected = true;
        }
        assertTrue(guildRejected);
        assertTrue(userRejected);
    }

    private static StoredTrack track(String title, String artist, long order) {
        return new StoredTrack(
                title,
                artist,
                "https://www.youtube.com/watch?v=test" + order,
                "id" + order,
                MediaProvider.YOUTUBE,
                180_000L,
                20L,
                "User",
                1_000L + order);
    }

    private static final class FakePort implements MusicHomeReadPort {
        private List<StoredTrack> favorites = List.of();
        private List<StoredTrack> history = List.of();
        private PersonalTasteProfile taste = PersonalTasteProfile.empty();
        private Optional<StationState> active = Optional.empty();
        private Optional<StationState> resumable = Optional.empty();
        private final List<PersonalizedStation> unavailable = new ArrayList<>();

        @Override
        public List<StoredTrack> favorites(long guildId, long userId) {
            return favorites;
        }

        @Override
        public List<StoredTrack> personalHistory(long guildId, long userId) {
            return history;
        }

        @Override
        public PersonalTasteProfile tasteProfile(long guildId, long userId) {
            return taste;
        }

        @Override
        public boolean hasStationSeeds(long guildId, long userId, PersonalizedStation station) {
            return !unavailable.contains(station);
        }

        @Override
        public Optional<StationState> activeStation(long guildId, long userId) {
            return active;
        }

        @Override
        public Optional<StationState> resumableStation(long guildId, long userId) {
            return resumable;
        }
    }
}
