package ru.flawden.BascovDiscordBot.product;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.home.HomeSnapshot;
import ru.flawden.BascovDiscordBot.home.MusicHomeReadPort;
import ru.flawden.BascovDiscordBot.home.MusicHomeService;
import ru.flawden.BascovDiscordBot.library.StoredTrack;
import ru.flawden.BascovDiscordBot.recommendation.PersonalTasteProfile;
import ru.flawden.BascovDiscordBot.recommendation.PersonalizedStation;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicProductServiceTest {

    @Test
    void homeDelegatesToExistingClientNeutralHomeUseCase() {
        MusicProductService service = service();

        HomeSnapshot home = service.home(10L, 20L);

        assertEquals(10L, home.guildId());
        assertEquals(20L, home.userId());
        assertEquals(2, home.today().size());
    }

    @Test
    void mixesAreProjectedFromExactlyTheSameHomeSnapshot() {
        MusicProductService service = service();

        HomeSnapshot home = service.home(10L, 20L);
        ProductMixesSnapshot mixes = service.mixes(10L, 20L);

        assertEquals(home.date(), mixes.date());
        assertEquals(home.today(), mixes.today());
        assertEquals(home.forYou(), mixes.forYou());
        assertEquals(home.themes(), mixes.themes());
    }

    @Test
    void librarySummaryUsesHomeCountsAndRecentPreview() {
        MusicProductService service = service();

        ProductLibrarySnapshot library = service.library(10L, 20L);

        assertEquals(0, library.favorites());
        assertEquals(0, library.personalHistory());
        assertTrue(library.recent().isEmpty());
        assertTrue(library.favoriteTracks().isEmpty());
        assertTrue(library.historyTracks().isEmpty());
    }

    @Test
    void mixDetailRejectsUnknownStationInsteadOfFallingBackSilently() {
        MusicProductService service = service();

        assertThrows(IllegalArgumentException.class, () -> service.mix(10L, 20L, "not-a-station"));
        assertEquals("my-mix", service.mix(10L, 20L, "my-mix").stationSlug());
    }

    @Test
    void playerDelegatesOnlyToProductReadPort() {
        ProductPlaybackSnapshot expected = ProductPlaybackSnapshot.idle(10L);
        MusicProductService service = new MusicProductService(
                new MusicHomeService(new FakeHomePort()),
                guildId -> expected);

        assertEquals(expected, service.player(10L));
        assertThrows(IllegalArgumentException.class, () -> service.player(0L));
    }

    @Test
    void searchValidatesInputAndDelegatesToProductReadPort() {
        MusicProductReadPort port = new MusicProductReadPort() {
            @Override
            public ProductPlaybackSnapshot playback(long guildId) {
                return ProductPlaybackSnapshot.idle(guildId);
            }

            @Override
            public List<HomeSnapshot.TrackPreview> search(long guildId, String query, int maxResults) {
                return List.of(new HomeSnapshot.TrackPreview("Holiday", "Green Day"));
            }
        };
        MusicProductService service = new MusicProductService(
                new MusicHomeService(new FakeHomePort()),
                port);

        ProductSearchSnapshot result = service.search(10L, 20L, "  Green Day Holiday  ", 5);

        assertEquals("Green Day Holiday", result.query());
        assertEquals(1, result.tracks().size());
        assertEquals("Holiday", result.tracks().get(0).title());
        assertThrows(IllegalArgumentException.class, () -> service.search(10L, 20L, "   ", 5));
        assertThrows(IllegalArgumentException.class, () -> service.search(10L, 20L, "query", 11));
    }

    @Test
    void capabilitiesExposeAuthenticatedPersonalLibraryWrites() {
        ProductCapabilities capabilities = service().capabilities();

        assertEquals("v1", capabilities.apiVersion());
        assertEquals("AUTHENTICATED_READ_LIBRARY_WRITE", capabilities.mode());
        assertTrue(capabilities.authenticationRequiredForReads());
        assertTrue(capabilities.mutationsEnabled());
        assertTrue(capabilities.authenticationRequiredForMutations());
        assertEquals(List.of("auth", "me", "devices", "guilds", "home", "mixes", "search", "player", "library", "favorites", "playlists", "taste", "playback", "capabilities"), capabilities.resources());
    }

    private static MusicProductService service() {
        return new MusicProductService(
                new MusicHomeService(new FakeHomePort()),
                ProductPlaybackSnapshot::idle);
    }

    private static final class FakeHomePort implements MusicHomeReadPort {
        @Override
        public List<StoredTrack> favorites(long guildId, long userId) {
            return List.of();
        }

        @Override
        public List<StoredTrack> personalHistory(long guildId, long userId) {
            return List.of();
        }

        @Override
        public PersonalTasteProfile tasteProfile(long guildId, long userId) {
            return PersonalTasteProfile.empty();
        }

        @Override
        public boolean hasStationSeeds(long guildId, long userId, PersonalizedStation station) {
            return true;
        }

        @Override
        public Optional<StationState> activeStation(long guildId, long userId) {
            return Optional.empty();
        }

        @Override
        public Optional<StationState> resumableStation(long guildId, long userId) {
            return Optional.empty();
        }
    }
}
