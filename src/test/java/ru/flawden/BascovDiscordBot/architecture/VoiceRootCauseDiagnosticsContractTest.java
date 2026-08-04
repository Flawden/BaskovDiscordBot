package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceRootCauseDiagnosticsContractTest {

    @Test
    void watchdogDefaultsToObserveOnlyAndStatusExposesTransportSignals() throws Exception {
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));
        String manager = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));
        String formatter = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/StatusMessageFormatter.java"));

        assertTrue(properties.contains(
                "DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE:false"));
        assertTrue(manager.contains("if (!properties.isVoiceWatchdogEnforce())"));
        assertTrue(formatter.contains("Frame polling"));
        assertTrue(formatter.contains("Last source error"));
    }

    @Test
    void staleTrackCallbacksCannotKillReplacementFallback() throws Exception {
        String scheduler = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/TrackScheduler.java"));

        assertTrue(scheduler.contains("if (!isCurrentTrack(track))"));
        assertTrue(scheduler.contains("Ignoring stale track-end callback"));
        assertFalse(scheduler.contains("onPlaybackCleanup.run()"));
    }

    @Test
    void deploymentSupportsExplicitBridgeVsHostNetworkExperiment() throws Exception {
        String delivery = Files.readString(Path.of(".github/workflows/delivery.yml"));
        String deploy = Files.readString(Path.of("deploy/remote-deploy.sh"));
        String override = Files.readString(Path.of("deploy/docker-compose.host-network.yml"));

        assertTrue(delivery.contains("network_mode:"));
        assertTrue(delivery.contains("BOT_NETWORK_MODE_OVERRIDE"));
        assertTrue(delivery.contains("BOT_NETWORK_MODE"));
        assertTrue(deploy.contains("bridge|host"));
        assertTrue(override.contains("network_mode: host"));
    }
}
