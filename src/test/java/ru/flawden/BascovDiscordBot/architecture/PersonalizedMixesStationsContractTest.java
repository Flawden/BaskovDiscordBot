package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalizedMixesStationsContractTest {

    @Test
    void mixIsProductLayerOverExistingRadioLifecycle() throws Exception {
        String interactions = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernInteractions.java"));
        String player = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));

        assertTrue(interactions.contains("case \"mix\" -> mix(event)"));
        assertTrue(interactions.contains("playerManager.startStation(guild, station, owner, selectedThemeFocus)"));
        assertTrue(player.contains("return startRadioInternal("));
        assertTrue(player.contains("RadioMode.PERSONAL"));
    }

    @Test
    void moodUsesFreshPersonalHistoryInsteadOfNewPersistence() throws Exception {
        String player = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));

        assertTrue(player.contains("selected.recentSeedsOnly()"));
        assertTrue(player.contains("personalHistory.stream().limit(12).toList()"));
        assertFalse(player.contains("mixes.tsv"));
        assertFalse(player.contains("stations.tsv"));
    }

    @Test
    void curatedStationsCannotBypassPlaybackTransport() throws Exception {
        String station = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/PersonalizedStation.java"));
        String planner = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/DailyMixSeedPlanner.java"));
        String player = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));

        assertFalse(station.contains("AudioTrack"));
        assertFalse(station.contains("loadItem"));
        assertFalse(planner.contains("AudioTrack"));
        assertFalse(planner.contains("loadItem"));
        assertTrue(player.contains("audioPlayerManager.loadItemOrdered"));
        assertTrue(player.contains("MediaQueryResolver.YOUTUBE_SEARCH_PREFIX"));
    }

    @Test
    void discoveriesStillDelegatesToHardNoveltyStrategy() throws Exception {
        String station = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/PersonalizedStation.java"));
        String ranker = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/RecommendationRanker.java"));

        assertTrue(station.contains("\"discoveries\""));
        assertTrue(station.contains("\"daily-discoveries\""));
        assertTrue(station.contains("RadioStrategy.DISCOVERY"));
        assertTrue(ranker.contains("strategy.hardNovelty() && known"));
    }

    @Test
    void manualRadioAndCuratedMixesRemainDistinguishable() throws Exception {
        String player = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));

        assertTrue(player.contains("PersonalizedStation.CUSTOM"));
        assertTrue(player.contains("public PersonalizedStation activeStation(long guildId)"));
        assertTrue(player.contains("selectedStation"));
    }

    @Test
    void dailyMixesAreDerivedWithoutAStorageMigration() throws Exception {
        String player = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));
        String planner = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/DailyMixSeedPlanner.java"));

        assertTrue(player.contains("selected.dailySeeded()"));
        assertTrue(player.contains("DailyMixSeedPlanner.plan("));
        assertTrue(planner.contains("guildId + \":\" + userId + \":\" + selected.slug() + \":\" + selectedDate"));
        assertFalse(player.contains("daily-mixes.tsv"));
        assertFalse(player.contains("station-continuity.tsv"));
    }

    @Test
    void continuityIsExplicitBoundedAndProcessLocal() throws Exception {
        String player = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));
        String interactions = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernInteractions.java"));

        assertTrue(player.contains("STATION_CONTINUITY_TTL = Duration.ofHours(36)"));
        assertTrue(player.contains("stationContinuations = new ConcurrentHashMap<>()"));
        assertTrue(player.contains("public RadioStartResult resumeStation("));
        assertTrue(player.contains("stationContinuations.clear()"));
        assertTrue(interactions.contains("/mix resume"));
        assertFalse(player.contains("StationContinuationRepository"));
    }

    @Test
    void resumePreservesDailyReleaseAndAntiRepeatMemory() throws Exception {
        String player = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));

        assertTrue(player.contains("continuation.seedDate()"));
        assertTrue(player.contains("this.seedCursor = continuation.seedCursor()"));
        assertTrue(player.contains("this.recentTrackKeys.addAll(continuation.recentTrackKeys())"));
        assertTrue(player.contains("this.recentTrackIdentities.addAll(continuation.recentTrackIdentities())"));
        assertTrue(player.contains("this.recentArtists.addAll(continuation.recentArtists())"));
        assertTrue(player.contains("this.recentMixArtists.addAll(continuation.recentMixArtists())"));
        assertTrue(player.contains("this.recentTagSets.addAll(continuation.recentTagSets())"));
    }
}
