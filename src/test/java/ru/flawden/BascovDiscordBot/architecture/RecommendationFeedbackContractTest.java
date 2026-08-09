package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationFeedbackContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void feedbackHasStandaloneAtomicBoundedPersistentStore() throws Exception {
        String repository = read("recommendation/FileRecommendationFeedbackRepository.java");
        String contract = read("recommendation/RecommendationFeedbackRepository.java");

        assertTrue(repository.contains("BASKOV_RECOMMENDATION_FEEDBACK_V2"));
        assertTrue(repository.contains("BASKOV_RECOMMENDATION_FEEDBACK_V1"));
        assertTrue(repository.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(repository.contains("MAX_ENTRIES_PER_USER"));
        assertTrue(contract.contains("MAX_ENTRIES_PER_USER = 200"));
    }

    @Test
    void radioGenerationAndImplicitSignalsFeedTheJournal() throws Exception {
        String player = read("lavaplayer/PlayerManager.java");
        String interactions = read("interactions/ModernInteractions.java");
        String stop = read("commands/music/StopEvent.java");

        assertTrue(player.contains("recommendationFeedback.recordRecommendation("));
        assertTrue(interactions.contains("recommendationFeedback.recordFavorite("));
        assertTrue(interactions.contains("recommendationFeedback.recordUnfavorite("));
        assertTrue(interactions.contains("recommendationFeedback.recordReplay("));
        assertTrue(interactions.contains("playerManager.recordExplicitStopFeedback(guild)"));
        assertTrue(stop.contains("playerManager.recordExplicitStopFeedback(event.getGuild())"));
    }

    @Test
    void playbackFeedbackIsFailOpenAndQuickNegativeIsBounded() throws Exception {
        String scheduler = read("lavaplayer/TrackScheduler.java");
        String service = read("recommendation/RecommendationFeedbackService.java");

        assertTrue(scheduler.contains("PlaybackFeedbackEvent.Type.SKIPPED"));
        assertTrue(scheduler.contains("PlaybackFeedbackEvent.Type.COMPLETED"));
        assertTrue(scheduler.contains("Playback feedback listener failed"));
        assertTrue(service.contains("QUICK_NEGATIVE_MAX_MILLIS = 30_000L"));
        assertTrue(service.contains("QUICK_NEGATIVE_MAX_RATIO = 0.20d"));
        assertTrue(service.contains("primary action will continue"));
    }

    @Test
    void feedbackIsVisibleAndIncludedInOperationalPersistence() throws Exception {
        String catalog = read("interactions/ModernCommandCatalog.java");
        String readiness = read("operations/PersistenceReadiness.java");
        String backups = read("operations/PersistenceBackupService.java");

        assertTrue(catalog.contains("new SubcommandData(\"feedback\""));
        assertTrue(readiness.contains("recommendationFeedbackProperties.getFile()"));
        assertTrue(backups.contains("recommendation-feedback.tsv"));
    }

    @Test
    void nowExplainsStateDisabledPreviousAndShuffleControls() throws Exception {
        String embeds = read("commands/music/MusicEmbeds.java");

        assertTrue(embeds.contains("`Предыдущий` — история пуста"));
        assertTrue(embeds.contains("`Перемешать` — нужно минимум 2 ожидающих трека"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }
}
