package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceConnectionStabilityContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void voiceConnectionIsCentralizedAndAutomaticReconnectIsDisabled() throws IOException {
        String coordinator = read("lavaplayer/VoiceConnectionCoordinator.java");
        String modern = read("interactions/ModernInteractions.java");
        String legacy = read("commands/music/SearchEvent.java");

        assertTrue(coordinator.contains("setAutoReconnect(false)"));
        assertTrue(coordinator.contains("voiceConnectTimeout"));
        assertTrue(coordinator.contains("voiceFailureCooldown"));
        assertTrue(modern.contains("ensureVoiceConnection"));
        assertTrue(legacy.contains("ensureVoiceConnection"));
        assertFalse(modern.contains("openAudioConnection"));
        assertFalse(legacy.contains("openAudioConnection"));
    }

    @Test
    void playbackStartsOnlyAfterVoiceReadinessCompletes() throws IOException {
        String modern = compact(read("interactions/ModernInteractions.java"));
        String legacy = compact(read("commands/music/SearchEvent.java"));

        assertTrue(modern.contains("if(!connection.connected())"));
        assertTrue(modern.indexOf("ensureVoiceConnection") < modern.indexOf("loadAndPlay"));
        assertTrue(legacy.contains("if(!connection.connected())"));
        assertTrue(legacy.indexOf("ensureVoiceConnection") < legacy.indexOf("loadAndPlay"));
    }

    @Test
    void lavaplayerCleanupClosesBrokenVoiceSession() throws IOException {
        String scheduler = read("lavaplayer/TrackScheduler.java");
        String player = read("lavaplayer/PlayerManager.java");

        assertTrue(scheduler.contains("AudioTrackEndReason.CLEANUP"));
        assertTrue(scheduler.contains("onPlaybackCleanup.run()"));
        assertTrue(player.contains("recordTransportFailure"));
        assertTrue(player.contains("safeCloseAudio"));
    }

    @Test
    void shutdownToleratesAlreadyStoppedJdaExecutors() throws IOException {
        String player = read("lavaplayer/PlayerManager.java");

        assertTrue(player.contains("catch (RuntimeException exception)"));
        assertTrue(player.contains("Voice connection already unavailable"));
    }


    @Test
    void failedDeployDoesNotRestartAContainerThatWasIntentionallyStopped() throws IOException {
        String deploy = Files.readString(Path.of("deploy/remote-deploy.sh"));

        assertTrue(deploy.contains("PREVIOUS_CONTAINER_RUNNING"));
        assertTrue(deploy.contains("kept the bot stopped"));
        assertTrue(deploy.contains("stop bot || true"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", "");
    }
}
