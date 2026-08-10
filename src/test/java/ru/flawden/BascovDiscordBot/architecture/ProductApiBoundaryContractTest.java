package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductApiBoundaryContractTest {

    @Test
    void applicationBoundaryIsClientNeutralAndSharedByDiscord() throws Exception {
        String service = read("product/MusicProductService.java");
        String interactions = read("interactions/ModernInteractions.java");

        assertFalse(service.contains("net.dv8tion"));
        assertFalse(service.contains("org.springframework.web"));
        assertFalse(service.contains("PlayerManager"));
        assertTrue(interactions.contains("MusicProductService"));
        assertTrue(interactions.contains("musicProductService.home"));
    }

    @Test
    void httpAdapterIsVersionedAndDoesNotReachIntoRuntimeInternals() throws Exception {
        String controller = read("product/api/ProductApiController.java");

        assertTrue(controller.contains("@RequestMapping(\"/api/v1\")"));
        assertTrue(controller.contains("MusicProductService product"));
        assertFalse(controller.contains("PlayerManager"));
        assertFalse(controller.contains("MusicLibraryRepository"));
        assertFalse(controller.contains("JDA"));
    }

    @Test
    void v128HttpSurfaceIsReadOnlyUntilAuthenticationExists() throws Exception {
        String controller = read("product/api/ProductApiController.java");
        String capabilities = read("product/ProductCapabilities.java");

        assertTrue(controller.contains("@GetMapping(\"/home\")"));
        assertTrue(controller.contains("@GetMapping(\"/mixes\")"));
        assertTrue(controller.contains("@GetMapping(\"/player\")"));
        assertTrue(controller.contains("@GetMapping(\"/library\")"));
        assertFalse(controller.contains("@PostMapping"));
        assertTrue(capabilities.contains("false,"));
        assertTrue(capabilities.contains("authenticationRequiredForMutations"));
    }

    @Test
    void apiIsDisabledAndNonWebByDefault() throws Exception {
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));
        String compose = Files.readString(Path.of("deploy/docker-compose.yml"));

        assertTrue(properties.contains("BASKOV_PRODUCT_API_ENABLED:false"));
        assertTrue(properties.contains("BASKOV_PRODUCT_API_WEB_APPLICATION_TYPE:none"));
        assertTrue(properties.contains("BASKOV_PRODUCT_API_BIND_ADDRESS:127.0.0.1"));
        assertTrue(compose.contains("BASKOV_PRODUCT_API_ENABLED: ${BASKOV_PRODUCT_API_ENABLED:-false}"));
        assertFalse(compose.contains("18080:18080"));
    }

    @Test
    void wireDtosAreSeparatedFromInternalDomainRecords() throws Exception {
        String response = read("product/api/ProductApiResponse.java");
        String mapper = read("product/api/ProductApiMapper.java");

        assertTrue(response.contains("Versioned wire DTOs"));
        assertTrue(mapper.contains("Explicit mapping"));
        assertFalse(response.contains("HomeSnapshot"));
        assertFalse(response.contains("ProductPlaybackSnapshot"));
    }

    @Test
    void productPlayerUsesStableTrackIdentityNotProviderIdentifiers() throws Exception {
        String adapter = read("product/RuntimeMusicProductReadAdapter.java");
        String snapshot = read("product/ProductPlaybackSnapshot.java");

        assertTrue(adapter.contains("TrackIdentity.of"));
        assertTrue(adapter.contains("identity.stableKey()"));
        assertFalse(snapshot.contains("youtube"));
        assertFalse(snapshot.contains("soundcloud"));
        assertFalse(snapshot.contains("playbackIdentifier"));
    }

    @Test
    void readOnlyGuildLookupDoesNotCreateMusicManager() throws Exception {
        String player = read("lavaplayer/PlayerManager.java");
        String method = method(player, "public Optional<GuildMusicManager> findMusicManager(long guildId)", "public CompletableFuture<VoiceConnectionResult>");

        assertTrue(method.contains("musicManagers.get(guildId)"));
        assertFalse(method.contains("computeIfAbsent"));
        assertFalse(method.contains("getMusicManager("));
    }

    @Test
    void v128AddsWebAdapterWithoutPublishingContainerPort() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertTrue(pom.contains("spring-boot-starter-web"));
        assertFalse(compose.contains("ports:"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/flawden/BascovDiscordBot").resolve(relative));
    }

    private static String method(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        return start >= 0 && end > start ? source.substring(start, end) : "";
    }
}
