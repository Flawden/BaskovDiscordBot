package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.flawden.BascovDiscordBot.config.RecommendationFeedbackProperties;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileRecommendationFeedbackRepositoryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void persistsRecommendationAndOutcomeAcrossReload() {
        Path file = tempDirectory.resolve("recommendation-feedback.tsv");
        RecommendationFeedbackProperties properties = new RecommendationFeedbackProperties();
        properties.setFile(file);
        FileRecommendationFeedbackRepository repository = new FileRecommendationFeedbackRepository(properties);
        repository.load();

        RecommendationFeedbackEntry pending = FileRecommendationFeedbackRepository.pending(
                42L,
                7L,
                "Linkin Park",
                "Numb",
                "Breaking Benjamin",
                "Breath",
                RecommendationIdentity.of("Breaking Benjamin", "Breath"),
                RadioStrategy.DISCOVERY,
                "last.fm",
                0.82d);
        repository.recordRecommendation(pending);
        repository.recordUserOutcome(
                42L,
                7L,
                pending.trackIdentity(),
                RecommendationOutcome.FAVORITED,
                1.0d);

        assertTrue(Files.isRegularFile(file));
        FileRecommendationFeedbackRepository reloaded = new FileRecommendationFeedbackRepository(properties);
        reloaded.load();
        RecommendationFeedbackEntry restored = reloaded.history(42L, 7L, 10).get(0);

        assertEquals(RecommendationOutcome.FAVORITED, restored.lastOutcome());
        assertEquals(1, restored.positiveSignals());
        assertEquals(0, restored.negativeSignals());
        assertEquals(3.0d, restored.signalScore(), 0.001d);
    }

    @Test
    void latestTrackOutcomeUpdatesNewestMatchingRecommendation() {
        RecommendationFeedbackProperties properties = new RecommendationFeedbackProperties();
        properties.setFile(tempDirectory.resolve("latest.tsv"));
        FileRecommendationFeedbackRepository repository = new FileRecommendationFeedbackRepository(properties);
        repository.load();

        String identity = RecommendationIdentity.of("Artist", "Track");
        repository.recordRecommendation(FileRecommendationFeedbackRepository.pending(
                42L, 1L, "Seed", "One", "Artist", "Track", identity,
                RadioStrategy.SIMILAR, "last.fm", 0.5d));
        repository.recordRecommendation(FileRecommendationFeedbackRepository.pending(
                42L, 2L, "Seed", "Two", "Artist", "Track", identity,
                RadioStrategy.DISCOVERY, "last.fm", 0.9d));

        RecommendationFeedbackEntry updated = repository.recordLatestOutcome(
                42L,
                identity,
                RecommendationOutcome.COMPLETED,
                1.0d).orElseThrow();

        assertEquals(2L, updated.userId());
        assertEquals(RecommendationOutcome.COMPLETED, updated.lastOutcome());
    }
    @Test
    void keepsOnlyTwoHundredNewestEntriesPerUser() {
        RecommendationFeedbackProperties properties = new RecommendationFeedbackProperties();
        properties.setFile(tempDirectory.resolve("bounded.tsv"));
        FileRecommendationFeedbackRepository repository = new FileRecommendationFeedbackRepository(properties);
        repository.load();

        for (int index = 0; index < 205; index++) {
            repository.recordRecommendation(FileRecommendationFeedbackRepository.pending(
                    42L, 7L, "Seed", "Seed " + index, "Artist", "Track " + index,
                    RecommendationIdentity.of("Artist", "Track " + index),
                    RadioStrategy.DISCOVERY, "last.fm", 0.7d));
        }

        assertEquals(200, repository.history(42L, 7L, 500).size());
        assertEquals("Track 204", repository.history(42L, 7L, 1).get(0).trackTitle());
    }

}
