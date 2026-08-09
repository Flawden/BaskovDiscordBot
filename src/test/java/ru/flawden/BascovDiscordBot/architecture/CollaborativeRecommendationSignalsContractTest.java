package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CollaborativeRecommendationSignalsContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void listenbrainzIsOptionalBoundedAndDoesNotOwnPlayback() throws Exception {
        String provider = read("recommendation/ListenBrainzCollaborativeProvider.java");
        String properties = read("config/DiscoveryProperties.java");

        assertTrue(properties.contains("listenbrainzEnabled"));
        assertTrue(provider.contains("metadata/lookup"));
        assertTrue(provider.contains("lb-radio/artist"));
        assertTrue(provider.contains("timeout(properties.getRequestTimeout())"));
        assertTrue(!provider.contains("AudioTrack"));
        assertTrue(!provider.contains("TrackScheduler"));
    }

    @Test
    void collaborativeSignalIsAnAdditionalRankerInputAfterNovelty() throws Exception {
        String ranker = read("recommendation/RecommendationRanker.java");

        assertTrue(ranker.contains("collaborativeContribution"));
        assertTrue(ranker.contains("collaborativeSignals().affinity"));
        assertTrue(ranker.indexOf("strategy.hardNovelty() && known")
                < ranker.indexOf("collaborativeAffinity"));
    }

    @Test
    void smartDiscoveryRunsCandidateAndCollaborativeSourcesFailOpen() throws Exception {
        String engine = read("recommendation/SmartDiscoveryEngine.java");

        assertTrue(engine.contains("candidatesFuture.thenCombine"));
        assertTrue(engine.contains("collaborativeFuture.exceptionally"));
        assertTrue(engine.contains("withCollaborativeSignals"));
    }

    @Test
    void youtubeRemainsPlaybackTransport() throws Exception {
        String player = read("lavaplayer/PlayerManager.java");
        String collaborative = read("recommendation/ListenBrainzCollaborativeProvider.java");

        assertTrue(player.contains("MediaQueryResolver.YOUTUBE_SEARCH_PREFIX"));
        assertTrue(player.contains("audioPlayerManager.loadItemOrdered"));
        assertTrue(!collaborative.contains("MediaQueryResolver.YOUTUBE_SEARCH_PREFIX"));
        assertTrue(!collaborative.contains("AudioTrack"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }
}
