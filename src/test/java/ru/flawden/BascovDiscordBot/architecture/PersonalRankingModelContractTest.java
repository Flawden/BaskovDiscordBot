package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalRankingModelContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void rankingUsesPersistentFeedbackWithoutTouchingPlaybackTransport() throws Exception {
        String player = read("lavaplayer/PlayerManager.java");
        String model = read("recommendation/PersonalRankingModel.java");
        String engine = read("recommendation/SmartDiscoveryEngine.java");

        assertTrue(player.contains("recommendationFeedback.tasteProfile("));
        assertTrue(model.contains("trackAffinity"));
        assertTrue(model.contains("artistAffinity"));
        assertTrue(model.contains("tagAffinity"));
        assertTrue(engine.contains("RecommendationRanker.best"));
        assertTrue(player.contains("MediaQueryResolver.YOUTUBE_SEARCH_PREFIX"));
    }

    @Test
    void modelHasAdaptiveExplorationAndExplainableScoreComponents() throws Exception {
        String model = read("recommendation/PersonalRankingModel.java");
        String engine = read("recommendation/SmartDiscoveryEngine.java");

        assertTrue(model.contains("explorationRate("));
        assertTrue(model.contains("negativeRatio"));
        assertTrue(engine.contains("personal "));
        assertTrue(engine.contains("artist "));
        assertTrue(engine.contains("tags "));
        assertTrue(engine.contains("final score"));
    }

    @Test
    void feedbackV2KeepsLegacyV1ReadableAndStoresTags() throws Exception {
        String repository = read("recommendation/FileRecommendationFeedbackRepository.java");
        String entry = read("recommendation/RecommendationFeedbackEntry.java");

        assertTrue(repository.contains("BASKOV_RECOMMENDATION_FEEDBACK_V2"));
        assertTrue(repository.contains("BASKOV_RECOMMENDATION_FEEDBACK_V1"));
        assertTrue(repository.contains("encodeTags(entry.tags())"));
        assertTrue(entry.contains("Set<String> tags"));
    }

    @Test
    void lastfmTagEnrichmentIsBoundedAndCannotReplaceTransport() throws Exception {
        String provider = read("recommendation/LastFmRecommendationProvider.java");
        String player = read("lavaplayer/PlayerManager.java");

        assertTrue(provider.contains("TAG_ENRICH_LIMIT = 5"));
        assertTrue(provider.contains("track.gettoptags"));
        assertTrue(provider.contains("metadataExecutor"));
        assertTrue(player.contains("MediaQueryResolver.YOUTUBE_SEARCH_PREFIX"));
    }

    @Test
    void radioExposesReadOnlyPersonalModel() throws Exception {
        String catalog = read("interactions/ModernCommandCatalog.java");
        String interactions = read("interactions/ModernInteractions.java");

        assertTrue(catalog.contains("new SubcommandData(\"model\""));
        assertTrue(interactions.contains("recommendationModelEmbed("));
        assertTrue(interactions.contains("Personal ranking model"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }
}
