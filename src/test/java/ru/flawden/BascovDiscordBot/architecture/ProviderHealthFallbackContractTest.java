package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderHealthFallbackContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void healthRegistryIsRuntimeOnlyAndBounded() throws Exception {
        String registry = read("playback/PlaybackProviderHealthRegistry.java");
        String properties = read("config/PlaybackResilienceProperties.java");

        assertTrue(registry.contains("consecutiveFailures"));
        assertTrue(registry.contains("cooldownUntil"));
        assertTrue(registry.contains("PlaybackProviderStatus.PROBE"));
        assertTrue(properties.contains("failureThreshold = 3"));
        assertTrue(properties.contains("Duration.ofSeconds(90)"));
        assertFalse(registry.contains("Repository"));
        assertFalse(registry.contains("Path.of"));
    }

    @Test
    void resolverOmitsCoolingProviderAndReturnsRetryDelayWhenAllAreOpen() throws Exception {
        String resolver = read("playback/PlaybackResolver.java");
        String resolution = read("playback/PlaybackResolution.java");

        assertTrue(resolver.contains("providerHealth.isAvailable"));
        assertTrue(resolver.contains("providerHealth.retryAfter"));
        assertTrue(resolution.contains("waitingForProviderRecovery"));
    }

    @Test
    void smartRadioFallsBackSequentiallyWithoutChangingRecommendation() throws Exception {
        String player = read("lavaplayer/PlayerManager.java");
        int start = player.indexOf("private void tryRadioTransportCandidate(");
        int end = player.indexOf("private void finishRadioSearch(", start);
        String fallbackLayer = player.substring(start, end);

        assertTrue(fallbackLayer.contains("resolution.candidates().get(index)"));
        assertTrue(fallbackLayer.contains("playbackResolver.recordFailure(source"));
        assertTrue(fallbackLayer.contains("playbackResolver.recordFallback"));
        assertTrue(fallbackLayer.contains("nextIndex"));
        assertFalse(fallbackLayer.contains("discoveryEngine.recommend"));
    }

    @Test
    void noMatchTriggersFallbackButDoesNotPoisonProviderHealth() throws Exception {
        String player = read("lavaplayer/PlayerManager.java");
        String registry = read("playback/PlaybackProviderHealthRegistry.java");

        assertTrue(player.contains("playbackResolver.recordMiss(source)"));
        assertTrue(registry.contains("void miss()"));
        assertFalse(registry.substring(registry.indexOf("void miss()"), registry.indexOf("void fallback(", registry.indexOf("void miss()")))
                .contains("consecutiveFailures++"));
    }

    @Test
    void explicitUserPlaybackPathDoesNotUseAutomaticResolverFallback() throws Exception {
        String player = read("lavaplayer/PlayerManager.java");
        int loadStart = player.indexOf("public void loadAndPlay(");
        int radioStart = player.indexOf("private void startRadioTransportSearch(");
        String explicitPlayback = player.substring(loadStart, radioStart);

        assertFalse(explicitPlayback.contains("playbackResolver.resolve"));
        assertFalse(explicitPlayback.contains("tryRadioTransportCandidate"));
    }

    @Test
    void resilienceConfigurationIsWiredThroughDeliveryWithoutSecrets() throws Exception {
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));
        String compose = Files.readString(Path.of("deploy/docker-compose.yml"));
        String delivery = Files.readString(Path.of(".github/workflows/delivery.yml"));
        String remote = Files.readString(Path.of("deploy/remote-deploy.sh"));

        assertTrue(properties.contains("DISCORD_BOT_PLAYBACK_PROVIDER_FAILURE_THRESHOLD:3"));
        assertTrue(properties.contains("DISCORD_BOT_PLAYBACK_PROVIDER_COOLDOWN:90s"));
        assertTrue(compose.contains("DISCORD_BOT_PLAYBACK_PROVIDER_FAILURE_THRESHOLD"));
        assertTrue(compose.contains("DISCORD_BOT_PLAYBACK_PROVIDER_COOLDOWN"));
        assertTrue(delivery.contains("vars.DISCORD_BOT_PLAYBACK_PROVIDER_FAILURE_THRESHOLD"));
        assertTrue(delivery.contains("vars.DISCORD_BOT_PLAYBACK_PROVIDER_COOLDOWN"));
        assertTrue(remote.contains("Playback provider failure threshold must be between 1 and 10"));
        assertTrue(remote.contains("Playback provider cooldown must be a positive duration"));
        assertFalse(delivery.contains("secrets.DISCORD_BOT_PLAYBACK_PROVIDER"));
    }

    @Test
    void doctorSourceReadsProviderHealthWithoutLiveNetworkProbe() throws Exception {
        String doctor = read("operations/SystemDoctor.java");

        assertTrue(doctor.contains("playbackProviderHealthSnapshots"));
        assertTrue(doctor.contains("PlaybackProviderStatus.COOLDOWN"));
        assertTrue(doctor.contains("health основан на runtime load/fallback событиях"));
        assertFalse(doctor.contains("HttpClient.newHttpClient"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }
}
