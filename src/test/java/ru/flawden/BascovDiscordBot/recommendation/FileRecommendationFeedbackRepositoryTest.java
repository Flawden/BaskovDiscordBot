package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.flawden.BascovDiscordBot.config.RecommendationFeedbackProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileRecommendationFeedbackRepositoryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void persistsRecommendationAndOutcomeAcrossReload() throws Exception {
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
                Set.of("alternative rock", "post-grunge"),
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
        assertTrue(restored.tags().contains("alternative rock"));
        assertEquals("BASKOV_RECOMMENDATION_FEEDBACK_V2", Files.readAllLines(file).get(0));
    }

    @Test
    void latestTrackOutcomeUpdatesNewestMatchingRecommendation() {
        RecommendationFeedbackProperties properties = new RecommendationFeedbackProperties();
        properties.setFile(tempDirectory.resolve("latest.tsv"));
        FileRecommendationFeedbackRepository repository = new FileRecommendationFeedbackRepository(properties);
        repository.load();

        String identity = RecommendationIdentity.of("Artist", "Track");
        long sameMillisecond = System.currentTimeMillis();
        RecommendationFeedbackEntry first = FileRecommendationFeedbackRepository.pending(
                42L, 1L, "Seed", "One", "Artist", "Track", identity,
                RadioStrategy.SIMILAR, "last.fm", 0.5d)
                .withRecommendedAtEpochMillis(sameMillisecond);
        RecommendationFeedbackEntry second = FileRecommendationFeedbackRepository.pending(
                42L, 2L, "Seed", "Two", "Artist", "Track", identity,
                RadioStrategy.DISCOVERY, "last.fm", 0.9d)
                .withRecommendedAtEpochMillis(sameMillisecond);
        repository.recordRecommendation(first);
        RecommendationFeedbackEntry recordedSecond = repository.recordRecommendation(second);

        assertTrue(recordedSecond.recommendedAtEpochMillis() > first.recommendedAtEpochMillis());

        RecommendationFeedbackEntry updated = repository.recordLatestOutcome(
                42L,
                identity,
                RecommendationOutcome.COMPLETED,
                1.0d).orElseThrow();

        assertEquals(2L, updated.userId());
        assertEquals(RecommendationOutcome.COMPLETED, updated.lastOutcome());
    }
    @Test
    void observedOutcomeCreatesEvidenceWithoutPriorRecommendationAndReusesSameTrack() {
        RecommendationFeedbackProperties properties = new RecommendationFeedbackProperties();
        properties.setFile(tempDirectory.resolve("observed.tsv"));
        FileRecommendationFeedbackRepository repository = new FileRecommendationFeedbackRepository(properties);
        repository.load();

        RecommendationFeedbackEntry observed = FileRecommendationFeedbackRepository.pending(
                42L, 7L, "Skillet", "Monster", "Skillet", "Monster",
                RecommendationIdentity.of("Skillet", "Monster"),
                RadioStrategy.FAMILIAR, "android-local", 1.0d);

        RecommendationFeedbackEntry played = repository.recordObservedOutcome(
                observed, RecommendationOutcome.PLAYED, 0.0d);
        RecommendationFeedbackEntry completed = repository.recordObservedOutcome(
                observed, RecommendationOutcome.COMPLETED, 1.0d);

        assertEquals(1, repository.history(42L, 7L, 10).size());
        assertEquals(RecommendationOutcome.COMPLETED, completed.lastOutcome());
        assertEquals(2, completed.positiveSignals());
        assertEquals(1.25d, completed.signalScore(), 0.001d);
        assertEquals(played.id(), completed.id());
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

    @Test
    void readsLegacyV1AndUpgradesOnNextWrite() throws Exception {
        Path file = tempDirectory.resolve("legacy-v1.tsv");
        String identity = RecommendationIdentity.of("Legacy Artist", "Legacy Song");
        String line = String.join("\t",
                "R",
                b64("legacy-id"),
                "42",
                "7",
                b64("Seed Artist"),
                b64("Seed Song"),
                b64("Legacy Artist"),
                b64("Legacy Song"),
                b64(identity),
                RadioStrategy.SIMILAR.name(),
                b64("Last.fm"),
                "0.75",
                "1700000000000",
                RecommendationOutcome.COMPLETED.name(),
                "1700000001000",
                "1",
                "0",
                "1.0",
                "1.0");
        Files.writeString(file, "BASKOV_RECOMMENDATION_FEEDBACK_V1\n" + line + "\n", StandardCharsets.UTF_8);

        RecommendationFeedbackProperties properties = new RecommendationFeedbackProperties();
        properties.setFile(file);
        FileRecommendationFeedbackRepository repository = new FileRecommendationFeedbackRepository(properties);
        repository.load();

        RecommendationFeedbackEntry restored = repository.history(42L, 7L, 10).get(0);
        assertTrue(restored.tags().isEmpty());
        repository.recordUserOutcome(42L, 7L, identity, RecommendationOutcome.REPLAYED, 1.0d);
        assertEquals("BASKOV_RECOMMENDATION_FEEDBACK_V2", Files.readAllLines(file).get(0));
    }

    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

}
