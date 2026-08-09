package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceRecoverySessionRestorationContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void persistsBoundedReloadableCheckpointsWithoutSerializingAudioTrack() throws IOException {
        String repository = read("session/FileMusicSessionRepository.java");
        String stored = read("session/StoredMusicSession.java");

        assertTrue(repository.contains("BASKOV_MUSIC_SESSIONS_V2"));
        assertTrue(repository.contains("BASKOV_MUSIC_SESSIONS_V1"));
        assertTrue(repository.contains("ATOMIC_MOVE"));
        assertTrue(repository.contains("OWNER_READ"));
        assertTrue(repository.contains("OWNER_WRITE"));
        assertTrue(stored.contains("queue.size() > 1_000"));
        assertTrue(stored.contains("history.size() > 25"));
        assertTrue(repository.contains("legacyV1 ? List.of() : decodeTracks(parts[9])"));
        assertFalse(repository.contains("ObjectOutputStream"));
        assertFalse(repository.contains("AudioTrack track"));
    }

    @Test
    void startupRestorationRunsOnlyAfterJdaReadyAndRequiresLiveChannel() throws IOException {
        String bot = read("config/BotConfig.java");
        String player = read("lavaplayer/PlayerManager.java");

        assertTrue(bot.contains(".awaitReady()"));
        assertTrue(bot.contains("playerManager.restorePersistedSessions(jda)"));
        assertTrue(player.contains("isRequireHumanListener"));
        assertTrue(player.contains("hasHumanListener"));
        assertTrue(player.contains("stored.expired"));
        assertTrue(player.contains("safeResumePositionMillis"));
    }

    @Test
    void recoveryV2RestoresPreviousHistoryWithoutReplayingListeningTelemetry() throws IOException {
        String player = read("lavaplayer/PlayerManager.java");
        String scheduler = read("lavaplayer/TrackScheduler.java");
        String interactions = read("interactions/ModernInteractions.java");

        assertTrue(player.contains("restoreHistorySequentially"));
        assertTrue(player.contains("session-history:"));
        String restoreHistory = methodBody(
                scheduler,
                "public void restoreHistory(List<TrackRequest> restoredHistory)",
                "public int clearQueue()"
        );
        assertTrue(restoreHistory.contains("history.clear()"));
        assertTrue(restoreHistory.contains("history::addLast"));
        assertFalse(restoreHistory.contains("historyListener"));
        assertTrue(interactions.contains("/session recover"));
        assertTrue(interactions.contains("administrationPolicy.canManage(event.getMember())"));
    }

    @Test
    void transientVoiceRecoveryIsPausedBoundedAndCooldownAware() throws IOException {
        String player = read("lavaplayer/PlayerManager.java");
        String coordinator = read("lavaplayer/VoiceConnectionCoordinator.java");

        assertTrue(player.contains("setPaused(true)"));
        assertTrue(player.contains("getMaxRecoveryAttempts"));
        assertTrue(player.contains("getRecoveryBackoff"));
        assertTrue(player.contains("voiceRecoveries.putIfAbsent"));
        assertTrue(player.contains("releaseSession(guild, false)"));
        assertTrue(coordinator.contains("boolean bypassCooldown"));
        assertTrue(coordinator.contains("cooldownUntil.remove"));
    }

    @Test
    void deploymentKeepsCheckpointInExistingPersistentVolume() throws IOException {
        String app = Files.readString(Path.of("src/main/resources/application.properties"));
        String localCompose = Files.readString(Path.of("docker-compose.yml"));
        String deployCompose = Files.readString(Path.of("deploy/docker-compose.yml"));

        assertTrue(app.contains("discord-bot.music-session.file"));
        assertTrue(localCompose.contains("DISCORD_BOT_MUSIC_SESSION_FILE: /app/data/music-sessions.tsv"));
        assertTrue(deployCompose.contains("DISCORD_BOT_MUSIC_SESSION_FILE: /app/data/music-sessions.tsv"));
        assertTrue(deployCompose.contains("bot-data:/app/data"));
        String deployScript = Files.readString(Path.of("deploy/remote-deploy.sh"));
        assertTrue(deployScript.contains("Voice recovery initialized:"));
    }

    private static String methodBody(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0, "start marker not found: " + startMarker);
        assertTrue(end > start, "end marker not found after: " + startMarker);
        return source.substring(start, end);
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
