package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackSourceResolverContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void resolverConsumesTrackIdentityAndClientCapabilities() throws Exception {
        String resolver = read("playback/PlaybackResolver.java");

        assertTrue(resolver.contains("TrackIdentity track"));
        assertTrue(resolver.contains("PlaybackClientCapabilities capabilities"));
        assertTrue(resolver.contains("List<PlaybackSourceProvider> providers"));
        assertFalse(resolver.contains("AudioTrack"));
        assertFalse(resolver.contains("net.dv8tion"));
    }

    @Test
    void providerSpecificIdentifiersExistOnlyAfterTrackSelection() throws Exception {
        String identity = read("catalog/TrackIdentity.java");
        String candidate = read("recommendation/RecommendationCandidate.java");
        String youtube = read("playback/YoutubePlaybackSourceProvider.java");
        String soundcloud = read("playback/SoundCloudPlaybackSourceProvider.java");

        assertFalse(identity.contains("ytsearch:"));
        assertFalse(identity.contains("scsearch:"));
        assertFalse(candidate.contains("ytsearch:"));
        assertFalse(candidate.contains("scsearch:"));
        assertTrue(youtube.contains("YOUTUBE_SEARCH_PREFIX"));
        assertTrue(soundcloud.contains("SOUNDCLOUD_SEARCH_PREFIX"));
    }

    @Test
    void smartRadioUsesResolverInsteadOfConstructingYoutubeSearchDirectly() throws Exception {
        String player = read("lavaplayer/PlayerManager.java");
        int methodStart = player.indexOf("private void startRadioTransportSearch(");
        int methodEnd = player.indexOf("private void finishRadioSearch(", methodStart);
        String method = player.substring(methodStart, methodEnd);

        assertTrue(method.contains("playbackResolver.resolve"));
        assertTrue(method.contains("PlaybackClientCapabilities.discord()"));
        assertTrue(method.contains("source.identifier()"));
        assertFalse(method.contains("YOUTUBE_SEARCH_PREFIX +"));
    }

    @Test
    void discordPolicyKeepsYoutubePrimaryWithoutHardCodingItIntoRecommendationEngine() throws Exception {
        String youtube = read("playback/YoutubePlaybackSourceProvider.java");
        String soundcloud = read("playback/SoundCloudPlaybackSourceProvider.java");
        String engine = read("recommendation/SmartDiscoveryEngine.java");

        assertTrue(youtube.contains("PRIORITY = 100"));
        assertTrue(soundcloud.contains("PRIORITY = 200"));
        assertFalse(engine.contains("MediaProvider.YOUTUBE"));
        assertFalse(engine.contains("MediaProvider.SOUNDCLOUD"));
    }

    @Test
    void v126DoesNotMixProviderHealthOrNewPersistenceIntoResolverFoundation() throws Exception {
        String resolver = read("playback/PlaybackResolver.java");
        String readme = Files.readString(Path.of("Readme.md"));

        assertFalse(resolver.contains("circuitBreaker"));
        assertFalse(resolver.contains("failureRate"));
        assertFalse(Files.exists(MAIN.resolve("playback/ProviderHealthRegistry.java")));
        assertTrue(readme.contains("Playback Source Abstraction"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }
}
