package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AndroidGatewayFoundationContractTest {

    @Test
    void authenticatedClientsCanDiscoverOnlyGuildsBehindTheAccessPort() throws Exception {
        String controller = read("product/api/ProductApiController.java");
        String guard = read("product/api/ProductApiAccessGuard.java");
        String port = read("product/api/ProductGuildAccessPort.java");

        assertTrue(controller.contains("@GetMapping(\"/guilds\")"));
        assertTrue(controller.contains("access.requireGuilds"));
        assertFalse(controller.contains("net.dv8tion"));
        assertTrue(guard.contains("guildAccess.accessibleGuilds"));
        assertTrue(port.contains("List<GuildSummary> accessibleGuilds"));
    }

    @Test
    void wireSnowflakesAreStringsBeforeAndroidAndWebClientsDependOnThem() throws Exception {
        String response = read("product/api/ProductApiResponse.java");
        String mapper = read("product/api/ProductApiMapper.java");

        assertTrue(response.contains("record Guild(String guildId, String name)"));
        assertTrue(response.contains("record Home(\n            String guildId"));
        assertTrue(response.contains("record Player(\n            String guildId"));
        assertTrue(mapper.contains("Long.toUnsignedString"));
    }

    @Test
    void committedOpenApiContractCoversAndroidMvpReadsAndAuth() throws Exception {
        String openApi = Files.readString(Path.of("docs/openapi/baskov-product-api-v1.yaml"));
        for (String path : new String[]{
                "/api/v1/capabilities:",
                "/api/v1/guilds:",
                "/api/v1/home:",
                "/api/v1/mixes:",
                "/api/v1/search:",
                "/api/v1/player:",
                "/api/v1/library:",
                "/api/v1/auth/device/pair:",
                "/api/v1/auth/refresh:",
                "/api/v1/auth/logout:",
                "/api/v1/auth/me:",
                "/api/v1/auth/devices:",
                "/api/v1/auth/devices/{sessionId}:"}) {
            assertTrue(openApi.contains(path), path);
        }
        assertTrue(openApi.contains("type: string # Discord snowflake"));
        assertTrue(openApi.contains("bearerAuth:"));
    }

    @Test
    void remoteApiComposeProfilePublishesOnlyToHostLoopback() throws Exception {
        String compose = Files.readString(Path.of("deploy/docker-compose.product-api.yml"));
        String base = Files.readString(Path.of("deploy/docker-compose.yml"));

        assertTrue(compose.contains("BASKOV_PRODUCT_API_ENABLED: \"true\""));
        assertTrue(compose.contains("BASKOV_PRODUCT_API_WEB_APPLICATION_TYPE: servlet"));
        assertTrue(compose.contains("127.0.0.1:${BASKOV_PRODUCT_API_HOST_PORT:-18080}:18080"));
        assertFalse(compose.contains("0.0.0.0:${BASKOV_PRODUCT_API_HOST_PORT"));
        assertFalse(base.contains("ports:"));
    }

    @Test
    void deliveryKeepsRemoteApiOptInAndRejectsUnsafeHostNetworkCombination() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/delivery.yml"));
        String deploy = Files.readString(Path.of("deploy/remote-deploy.sh"));

        assertTrue(workflow.contains("BASKOV_PRODUCT_API_REMOTE_ENABLED"));
        assertTrue(workflow.contains("${BASKOV_PRODUCT_API_REMOTE_ENABLED:-false}"));
        assertTrue(deploy.contains("Remote Product API profile requires bridge network mode"));
        assertTrue(deploy.contains("docker-compose.product-api.yml"));
        assertTrue(deploy.contains("/dev/tcp/127.0.0.1/"));
        assertTrue(deploy.contains("GET /api/v1/capabilities HTTP/1.0"));
    }

    @Test
    void gatewayReleaseStillDoesNotExposeMusicMutations() throws Exception {
        String capabilities = read("product/ProductCapabilities.java");
        String controller = read("product/api/ProductApiController.java");

        assertTrue(capabilities.contains("false,\n                true,"));
        assertTrue(capabilities.contains("\"guilds\""));
        assertFalse(controller.contains("@PostMapping"));
        assertFalse(controller.contains("@PutMapping"));
        assertFalse(controller.contains("@DeleteMapping"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/flawden/BascovDiscordBot").resolve(relative))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
