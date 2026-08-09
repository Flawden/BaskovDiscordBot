package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartDiscoveryEngineContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void externalProviderReturnsMetadataOnlyAndPlaybackStillUsesYtsearch() throws IOException {
        String candidate = read("recommendation/RecommendationCandidate.java");
        String player = read("lavaplayer/PlayerManager.java");

        assertTrue(candidate.contains("String artist"));
        assertTrue(candidate.contains("String title"));
        assertFalse(candidate.contains("AudioTrack"));
        assertTrue(player.contains("MediaQueryResolver.YOUTUBE_SEARCH_PREFIX"));
        assertTrue(player.contains("discoveryEngine.recommend"));
    }

    @Test
    void discoveryHardRejectsKnownTracksAndUsesArtistCooldown() throws IOException {
        String ranker = read("recommendation/RecommendationRanker.java");
        String player = read("lavaplayer/PlayerManager.java");

        assertTrue(ranker.contains("strategy.hardNovelty() && known"));
        assertTrue(ranker.contains("recentArtists"));
        assertTrue(player.contains("knownTrackIdentities"));
        assertTrue(player.contains("ARTIST_COOLDOWN_LIMIT = 3"));
    }

    @Test
    void lastfmIsOptionalAndHasBoundedTimeout() throws IOException {
        String properties = read("config/DiscoveryProperties.java");
        String provider = read("recommendation/LastFmRecommendationProvider.java");

        assertTrue(properties.contains("lastfmEnabled"));
        assertTrue(properties.contains("Duration.ofSeconds(15)"));
        assertTrue(provider.contains("track.getsimilar"));
        assertTrue(provider.contains("HttpRequest.newBuilder"));
        assertTrue(provider.contains("connectTimeout(properties.getRequestTimeout())"));
        assertTrue(provider.contains(".timeout(properties.getRequestTimeout())"));
    }

    @Test
    void deliveryCarriesOptionalLastfmSecretWithoutMakingItRequired() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/delivery.yml"));
        String deploy = Files.readString(Path.of("deploy/remote-deploy.sh"));
        String compose = Files.readString(Path.of("deploy/docker-compose.yml"));

        assertTrue(workflow.contains("secrets.LASTFM_API_KEY"));
        assertTrue(workflow.contains("LASTFM_API_KEY_B64"));
        assertTrue(deploy.contains("LASTFM_API_KEY_B64:=}"));
        assertTrue(compose.contains("LASTFM_API_KEY: ${LASTFM_API_KEY:-}"));
    }

    @Test
    void radioWhyExplainsProviderAndReason() throws IOException {
        String interactions = read("interactions/ModernInteractions.java");
        String snapshot = read("lavaplayer/RadioSnapshot.java");

        assertTrue(interactions.contains("\"why\".equals(subcommand)"));
        assertTrue(interactions.contains("snapshot.lastReason()"));
        assertTrue(snapshot.contains("String provider"));
        assertTrue(snapshot.contains("String lastReason"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
