package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixGenerationDiversityContractTest {

    @Test
    void curatedMixDiversityLivesInsideRecommendationContextNotPlaybackEngine() throws Exception {
        String context = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/RecommendationContext.java"));
        String policy = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/MixDiversityPolicy.java"));
        String ranker = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/RecommendationRanker.java"));

        assertTrue(context.contains("MixDiversityProfile mixDiversity"));
        assertTrue(ranker.contains("MixDiversityPolicy.evaluate"));
        assertFalse(policy.contains("AudioTrack"));
        assertFalse(policy.contains("loadItem"));
        assertFalse(policy.contains("AudioManager"));
    }

    @Test
    void immediateArtistRepeatIsBlockedAtRankingAndFinalTransportBoundary() throws Exception {
        String policy = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/MixDiversityPolicy.java"));
        String player = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));

        assertTrue(policy.contains("immediateArtistRepeat"));
        assertTrue(player.contains("blocksArtistForMix"));
        assertTrue(player.contains("MixSeedDiversityPlanner.spreadArtists"));
    }

    @Test
    void themeMixIsDynamicAndUsesExistingFeedbackTags() throws Exception {
        String station = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/PersonalizedStation.java"));
        String interactions = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernInteractions.java"));
        String feedback = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/RecommendationFeedbackService.java"));

        assertTrue(station.contains("THEME("));
        assertTrue(interactions.contains("positiveMixThemes"));
        assertTrue(interactions.contains("recommendationFeedback.tasteProfile"));
        assertTrue(interactions.contains("/mix themes"));
        assertTrue(feedback.contains("tasteProfile"));
    }

    @Test
    void themeAndDiversityRemainBelowHardNovelty() throws Exception {
        String ranker = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/RecommendationRanker.java"));
        int novelty = ranker.indexOf("strategy.hardNovelty() && known");
        int diversity = ranker.indexOf("MixDiversityPolicy.evaluate");

        assertTrue(novelty >= 0);
        assertTrue(diversity > novelty);
    }

    @Test
    void releaseDoesNotAddMixPersistenceOrNewPlaybackTransport() throws Exception {
        String player = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));
        String interactions = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernInteractions.java"));

        assertFalse(player.contains("mix-diversity.tsv"));
        assertFalse(player.contains("themes.tsv"));
        assertFalse(player.contains("MixDiversityRepository"));
        assertTrue(player.contains("MediaQueryResolver.YOUTUBE_SEARCH_PREFIX"));
        assertTrue(player.contains("audioPlayerManager.loadItemOrdered"));
        assertFalse(interactions.contains("new DefaultAudioPlayerManager"));
    }
}
