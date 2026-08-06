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
                "DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT",
                "DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT",
                "DISCORD_BOT_MUSIC_PLAYBACK_READY_TIMEOUT",
                "DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN",
                "DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE",
                "DISCORD_BOT_MUSIC_SESSION_CHECKPOINT_INTERVAL",
                "DISCORD_BOT_MUSIC_SESSION_MAX_AGE",
                "DISCORD_BOT_MUSIC_SESSION_RESTORE_ON_STARTUP",
                "DISCORD_BOT_MUSIC_SESSION_REQUIRE_HUMAN_LISTENER",
                "DISCORD_BOT_MUSIC_SESSION_VOICE_RECOVERY_ENABLED",
                "DISCORD_BOT_MUSIC_SESSION_MAX_RECOVERY_ATTEMPTS",
                "DISCORD_BOT_MUSIC_SESSION_RECOVERY_BACKOFF",
                "DISCORD_BOT_MUSIC_DEFAULT_VOLUME",
                "DISCORD_BOT_MUSIC_MAX_VOLUME"}) {
            assertTrue(workflow.contains(variable), variable);
            assertTrue(workflow.contains("printf '" + variable + "_B64=%s"), variable + " workflow B64");
            assertTrue(deployScript.contains(variable + "_B64"), variable + " deploy B64");
            assertTrue(deployScript.contains("printf '" + variable + "=%s"), variable + " protected env");
            assertTrue(compose.contains(variable), variable);
        }
    }
}
