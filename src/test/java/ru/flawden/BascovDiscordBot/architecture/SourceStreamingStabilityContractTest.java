package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceStreamingStabilityContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void schedulerTreatsTruncatedFinishedStreamsAsSourceFailure() throws IOException {
        String scheduler = read("lavaplayer/TrackScheduler.java");
        String policy = read("lavaplayer/PrematureTrackEndPolicy.java");

        assertTrue(scheduler.contains("PrematureTrackEndPolicy.isPremature(track, elapsedMillis)"));
        assertTrue(scheduler.contains("startFallback(track, \"premature finish\")"));
        assertTrue(scheduler.contains("advanceToNext(false)"));
        assertTrue(policy.contains("MAX_PREVIEW_POSITION_MILLIS = 45_000L"));
        assertTrue(policy.contains("MIN_MISSING_TAIL_MILLIS = 60_000L"));
    }

    @Test
    void sourceErrorsKeepDeepHttpCauseAndPlaybackPosition() throws IOException {
        String scheduler = read("lavaplayer/TrackScheduler.java");
        String formatter = read("lavaplayer/SourceFailureFormatter.java");

        assertTrue(scheduler.contains("SourceFailureFormatter.describe(track, exception)"));
        assertTrue(formatter.contains("deepestMessage"));
        assertTrue(formatter.contains("position="));
        assertTrue(formatter.contains("media="));
    }

    @Test
    void providerSearchHasLargerDeduplicatedRecoveryPool() throws IOException {
        String manager = read("lavaplayer/PlayerManager.java");

        assertTrue(manager.contains("MediaQueryResolver.YOUTUBE_SEARCH_PREFIX"));
        assertTrue(manager.contains("MediaQueryResolver.SOUNDCLOUD_SEARCH_PREFIX"));
        assertTrue(manager.contains("Set<String> seen = ConcurrentHashMap.newKeySet()"));
        assertTrue(manager.contains("seen.add(trackKey(candidate))"));
        assertTrue(manager.contains(".limit(9)"));
        assertFalse(manager.contains(".limit(4)"));
    }

    @Test
    void staleCallbacksAndFallbacksDoNotOverwriteRootSourceError() throws IOException {
        String diagnostics = read("operations/VoiceDiagnostics.java");
        String snapshot = read("operations/VoiceDiagnosticSnapshot.java");
        String status = read("interactions/StatusMessageFormatter.java");

        assertTrue(diagnostics.contains("lastRecoveryEvent"));
        assertTrue(diagnostics.contains("lastStaleCallback"));
        assertTrue(snapshot.contains("String lastRecoveryEvent"));
        assertTrue(snapshot.contains("String lastStaleCallback"));
        assertTrue(status.contains("Last recovery:"));
        assertTrue(status.contains("Last stale callback:"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
