package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationsObservabilityContractTest {

    private static final Path ROOT = Path.of(".");
    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void statusCommandExposesRuntimeWithoutPersonalData() throws IOException {
        String catalog = readMain("interactions/ModernCommandCatalog.java");
        String interactions = readMain("interactions/ModernInteractions.java");
        String playerManager = readMain("lavaplayer/PlayerManager.java");

        assertTrue(catalog.contains("Commands.slash(\"status\""));
        assertTrue(interactions.contains("RuntimeHealthMonitor.Snapshot"));
        assertTrue(interactions.contains("OperationalMetrics.Snapshot"));
        assertTrue(interactions.contains("MusicRuntimeSnapshot"));
        assertTrue(playerManager.contains("runtimeSnapshot()"));
    }

    @Test
    void readinessIsAConnectedAndFreshHeartbeat() throws IOException {
        String monitor = readMain("operations/RuntimeHealthMonitor.java");
        String healthcheck = Files.readString(ROOT.resolve("deploy/healthcheck.sh"));
        String dockerfile = Files.readString(ROOT.resolve("Dockerfile"));

        assertTrue(monitor.contains("JDA.Status.CONNECTED"));
        assertTrue(monitor.contains("scheduleWithFixedDelay"));
        assertTrue(healthcheck.contains("status=CONNECTED"));
        assertTrue(healthcheck.contains("MAX_AGE_SECONDS"));
        assertTrue(dockerfile.contains("deploy/healthcheck.sh"));
        assertTrue(dockerfile.contains("CMD [\"/app/healthcheck.sh\"]"));
    }

    @Test
    void containerHasBoundedResourcesAndLogs() throws IOException {
        String compose = Files.readString(ROOT.resolve("deploy/docker-compose.yml"));

        assertTrue(compose.contains("mem_limit: 768m"));
        assertTrue(compose.contains("cpus: 1.0"));
        assertTrue(compose.contains("pids_limit: 256"));
        assertTrue(compose.contains("max-size: \"10m\""));
        assertTrue(compose.contains("max-file: \"3\""));
    }

    @Test
    void deploymentVerifiesImageRestartCountAndHeartbeat() throws IOException {
        String deploy = Files.readString(ROOT.resolve("deploy/remote-deploy.sh"));

        assertTrue(deploy.contains("verify_runtime"));
        assertTrue(deploy.contains("{{.Config.Image}}"));
        assertTrue(deploy.contains("{{.RestartCount}}"));
        assertTrue(deploy.contains("/app/healthcheck.sh"));
        assertTrue(deploy.contains("Runtime verification passed"));
    }

    private static String readMain(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
