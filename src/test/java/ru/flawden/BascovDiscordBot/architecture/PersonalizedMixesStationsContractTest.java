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
        assertTrue(interactions.contains("playerManager.startStation(guild, station, owner)"));
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
        String player = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));

        assertFalse(station.contains("AudioTrack"));
        assertFalse(station.contains("loadItem"));
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
        assertTrue(station.contains("RadioStrategy.DISCOVERY"));
        assertTrue(ranker.contains("strategy.hardNovelty() && known"));
    }

    @Test
    void manualRadioAndCuratedMixesRemainDistinguishable() throws Exception {
        String player = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));

        assertTrue(player.contains("PersonalizedStation.CUSTOM"));
        assertTrue(player.contains("public PersonalizedStation activeStation(long guildId)"));
        assertTrue(player.contains("new RadioState(mode, recommendationStrategy, requester, selectedStation)"));
    }
}
