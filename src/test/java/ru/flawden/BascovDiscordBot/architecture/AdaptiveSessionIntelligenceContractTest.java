package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveSessionIntelligenceContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void sessionModelIsEphemeralAndDerivedFromExistingFeedback() throws Exception {
        String model = read("recommendation/AdaptiveSessionModel.java");
        String player = read("lavaplayer/PlayerManager.java");

        assertTrue(model.contains("recommendedAtEpochMillis() < startedAtEpochMillis"));
        assertTrue(player.contains("startedAtEpochMillis = System.currentTimeMillis()"));
        assertTrue(!model.contains("Files."));
    }

    @Test
    void sessionSignalIsAppliedAfterHardNoveltyAndBeforePlaybackTransport() throws Exception {
        String ranker = read("recommendation/RecommendationRanker.java");
        String player = read("lavaplayer/PlayerManager.java");

        assertTrue(ranker.contains("sessionContribution"));
        assertTrue(ranker.indexOf("strategy.hardNovelty() && known") < ranker.indexOf("sessionContribution"));
        assertTrue(player.contains("MediaQueryResolver.YOUTUBE_SEARCH_PREFIX"));
    }

    @Test
    void serverRadioDoesNotBorrowPersonalSessionTaste() throws Exception {
        String player = read("lavaplayer/PlayerManager.java");

        assertTrue(player.contains("state.mode() == RadioMode.PERSONAL"));
        assertTrue(player.contains("SessionTasteProfile.empty(0L)"));
    }

    @Test
    void discordUxExposesSessionStateWithoutPersistingIt() throws Exception {
        String catalog = read("interactions/ModernCommandCatalog.java");
        String interactions = read("interactions/ModernInteractions.java");

        assertTrue(catalog.contains("new SubcommandData(\"session\""));
        assertTrue(interactions.contains("Adaptive session intelligence"));
        assertTrue(interactions.contains("reset при /radio start и restart/deploy"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }
}
