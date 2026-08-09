package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationEmbeddingsFoundationContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void embeddingLayerIsProviderNeutralAndDependencyFreeFromPlayback() throws Exception {
        String provider = read("recommendation/RecommendationEmbeddingProvider.java");
        String local = read("recommendation/FeatureHashRecommendationEmbeddingProvider.java");

        assertTrue(provider.contains("RecommendationEmbedding embed("));
        assertTrue(local.contains("DIMENSIONS = 64"));
        assertTrue(!provider.contains("lavaplayer"));
        assertTrue(!local.contains("lavaplayer"));
        assertTrue(!local.contains("HttpClient"));
    }

    @Test
    void personalTasteVectorIsRebuiltFromExistingFeedbackInsteadOfNewPersistence() throws Exception {
        String vectorModel = read("recommendation/PersonalTasteVectorModel.java");
        String feedback = read("recommendation/FileRecommendationFeedbackRepository.java");

        assertTrue(vectorModel.contains("trackAffinity()"));
        assertTrue(vectorModel.contains("artistAffinity()"));
        assertTrue(vectorModel.contains("tagAffinity()"));
        assertTrue(feedback.contains("BASKOV_RECOMMENDATION_FEEDBACK_V2"));
        assertTrue(!vectorModel.contains("Files."));
    }

    @Test
    void rankerUsesCosineAsBoundedAdditionalSignalWithoutBypassingNovelty() throws Exception {
        String ranker = read("recommendation/RecommendationRanker.java");

        assertTrue(ranker.contains("RecommendationVectorMath.cosine"));
        assertTrue(ranker.contains("vectorContribution"));
        assertTrue(ranker.contains("strategy.hardNovelty() && known"));
        assertTrue(ranker.indexOf("strategy.hardNovelty() && known") < ranker.indexOf("vectorSimilarity"));
    }

    @Test
    void explainabilityAndModelStatusExposeVectorContribution() throws Exception {
        String engine = read("recommendation/SmartDiscoveryEngine.java");
        String interactions = read("interactions/ModernInteractions.java");

        assertTrue(engine.contains("vector "));
        assertTrue(engine.contains("vectorConfidence"));
        assertTrue(interactions.contains("Vector model"));
        assertTrue(interactions.contains("feature-hash-v1") || interactions.contains("vector.provider()"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }
}
