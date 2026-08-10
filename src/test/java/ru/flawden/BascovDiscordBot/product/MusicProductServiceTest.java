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
    void capabilitiesExplicitlyKeepV128ReadOnly() {
        ProductCapabilities capabilities = service().capabilities();

        assertEquals("v1", capabilities.apiVersion());
        assertEquals("READ_ONLY_PREVIEW", capabilities.mode());
        assertFalse(capabilities.mutationsEnabled());
        assertTrue(capabilities.authenticationRequiredForMutations());
        assertEquals(List.of("home", "mixes", "player", "library", "capabilities"), capabilities.resources());
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
