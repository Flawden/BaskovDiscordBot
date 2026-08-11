package ru.flawden.BascovDiscordBot.product;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.home.HomeSnapshot;
import ru.flawden.BascovDiscordBot.product.api.ProductApiMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductApiMapperTest {

    private final ProductApiMapper mapper = new ProductApiMapper();

    @Test
    void mapsHomeWithoutLeakingInternalOptional() {
        HomeSnapshot source = home(Optional.of(new HomeSnapshot.ContinuationCard(
                HomeSnapshot.ContinuationCard.Kind.RESUMABLE,
                "Микс дня",
                "daily-mix",
                "",
                LocalDate.of(2026, 8, 10),
                4L)));

        var result = mapper.home(source, "baskov-user-1");

        assertEquals("10", result.guildId());
        assertEquals("baskov-user-1", result.userId());
        assertEquals("RESUMABLE", result.continuation().kind());
        assertEquals("daily-mix", result.continuation().stationSlug());
        assertEquals(1, result.today().size());
        assertEquals("linkin park::numb", result.recent().get(0).stableKey());
    }

    @Test
    void mapsAccessibleGuildsWithJsonSafeSnowflakeStrings() {
        var result = mapper.guilds("baskov-user-1", List.of(
                new ru.flawden.BascovDiscordBot.product.api.ProductGuildAccessPort.GuildSummary(123456789012345678L, "Music Guild")));

        assertEquals("baskov-user-1", result.userId());
        assertEquals("123456789012345678", result.guilds().get(0).guildId());
        assertEquals("Music Guild", result.guilds().get(0).name());
    }

    @Test
    void absentContinuationBecomesJsonFriendlyNull() {
        var result = mapper.home(home(Optional.empty()), "baskov-user-1");
        assertNull(result.continuation());
    }

    @Test
    void mapsPlayerTrackUsingStableProviderNeutralKey() {
        ProductPlaybackSnapshot source = new ProductPlaybackSnapshot(
                10L, true, true, false, 100, "OFF", 2, 12_000L, 180_000L,
                Optional.of(new ProductPlaybackSnapshot.Track("linkin park::numb", "Numb", "Linkin Park")),
                new ProductPlaybackSnapshot.Radio(true, "daily-mix", "", "similar", 3));

        var result = mapper.player(source);

        assertEquals("linkin park::numb", result.current().stableKey());
        assertEquals("daily-mix", result.radio().stationSlug());
        assertTrue(result.playing());
    }

    @Test
    void mapsExpandedLibraryAndMixSeedPreview() {
        ProductLibrarySnapshot library = new ProductLibrarySnapshot(
                10L, 20L, 1, 1,
                List.of(new HomeSnapshot.TrackPreview("Numb", "Linkin Park")),
                List.of(new HomeSnapshot.TrackPreview("Monster", "Skillet")),
                List.of(new HomeSnapshot.TrackPreview("Numb", "Linkin Park")));

        var libraryWire = mapper.library(library, "baskov-user-1");
        assertEquals("skillet::monster", libraryWire.favoriteTracks().get(0).stableKey());
        assertEquals("linkin park::numb", libraryWire.historyTracks().get(0).stableKey());

        ProductMixDetailSnapshot detail = new ProductMixDetailSnapshot(
                10L, 20L, "my-mix", "Мой микс", "personal", true, false,
                List.of(new HomeSnapshot.TrackPreview("Monster", "Skillet")));
        var mixWire = mapper.mix(detail, "baskov-user-1");
        assertEquals("my-mix", mixWire.stationSlug());
        assertEquals("skillet::monster", mixWire.seedPreview().get(0).stableKey());
    }

    @Test
    void capabilitiesAdvertiseAuthenticatedReadsWithoutMusicMutations() {
        var result = mapper.capabilities(ProductCapabilities.authenticatedRead());
        assertTrue(result.authenticationRequiredForReads());
        assertFalse(result.mutationsEnabled());
        assertTrue(result.authenticationRequiredForMutations());
    }

    private static HomeSnapshot home(Optional<HomeSnapshot.ContinuationCard> continuation) {
        return new HomeSnapshot(
                10L,
                20L,
                LocalDate.of(2026, 8, 10),
                continuation,
                List.of(new HomeSnapshot.MixCard("daily-mix", "Микс дня", "daily", true, true)),
                List.of(new HomeSnapshot.MixCard("my-mix", "Мой микс", "personal", true, false)),
                List.of(new HomeSnapshot.ThemeCard("pop punk", 2.0d)),
                new HomeSnapshot.LibraryCard(5, 12),
                List.of(new HomeSnapshot.TrackPreview("Numb", "Linkin Park")),
                new HomeSnapshot.TasteCard(7, 0.75d, 9));
    }
}
