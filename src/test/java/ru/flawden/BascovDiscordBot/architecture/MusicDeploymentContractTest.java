package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicDeploymentContractTest {

    @Test
    void deliveryCarriesMusicSettingsIntoProtectedEnvironmentFile() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/delivery.yml"));
        String deployScript = Files.readString(Path.of("deploy/remote-deploy.sh"));
        String compose = Files.readString(Path.of("deploy/docker-compose.yml"));

        for (String variable : new String[]{
                "DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE",
                "DISCORD_BOT_MUSIC_MAX_TRACK_DURATION",
                "DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT"}) {
            assertTrue(workflow.contains(variable), variable);
            assertTrue(deployScript.contains(variable), variable);
            assertTrue(compose.contains(variable), variable);
        }
    }
}
