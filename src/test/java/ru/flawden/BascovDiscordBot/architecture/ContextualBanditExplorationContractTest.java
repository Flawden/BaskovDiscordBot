package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextualBanditExplorationContractTest {

    @Test
    void banditReusesExistingFeedbackInsteadOfAddingAnotherPersistenceFormat() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/RecommendationFeedbackService.java"));
        String feedback = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/RecommendationFeedbackEntry.java"));

        assertTrue(service.contains("ContextualBanditModel.build(repository.history"));
        assertFalse(feedback.contains("banditArm"));
        assertFalse(feedback.contains("explorationArm"));
    }

    @Test
    void hardNoveltyRunsBeforeBanditScoring() throws Exception {
        String ranker = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/RecommendationRanker.java"));
        int reject = ranker.indexOf("if (recent || (strategy.hardNovelty() && known))");
        int bandit = ranker.indexOf("ContextualBanditModel.decide(");

        assertTrue(reject >= 0);
        assertTrue(bandit > reject);
    }

    @Test
    void onlinePolicyIsBoundedAndVisibleThroughRadioCommand() throws Exception {
        String model = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/recommendation/ContextualBanditModel.java"));
        String catalog = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernCommandCatalog.java"));

        assertTrue(model.contains("clamp(learned + uncertaintyBonus + strategyPrior + sessionAdjustment, -0.12d, 0.12d)"));
        assertTrue(catalog.contains("new SubcommandData(\"bandit\""));
    }
}
