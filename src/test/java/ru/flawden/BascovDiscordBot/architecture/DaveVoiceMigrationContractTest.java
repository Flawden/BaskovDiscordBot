package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaveVoiceMigrationContractTest {

    @Test
    void jdaMovesToPinnedDaveCapableFiveLineWithoutFrameworkDrift() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<version>5.6.1</version>"));
        assertFalse(pom.contains("<version>5.3.0</version>"));
        assertTrue(pom.contains("<version>3.4.3</version>"),
                "Spring Boot must not move during the DAVE migration");
        assertTrue(pom.contains("<version>2.2.3</version>"),
                "LavaPlayer must remain unchanged during the DAVE migration");
    }

    @Test
    void playResponseWaitsForActualDiscordFramePolling() throws Exception {
        String manager = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));
        String interactions = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernInteractions.java"));
        String embeds = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/commands/music/MusicEmbeds.java"));

        assertTrue(manager.contains("awaitPlaybackReady"));
        assertTrue(manager.contains("currentFrameRequests > baselineFrameRequests"));
        assertTrue(interactions.contains("playbackConfirmed"));
        assertTrue(interactions.contains("playbackReadinessFailure"));
        assertFalse(embeds.contains("Воспроизведение началось"),
                "The bot must not claim playback before media transport is confirmed");
    }

    @Test
    void statusAndStartupExposeTheLoadedJdaVersion() throws Exception {
        String formatter = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/StatusMessageFormatter.java"));
        String botConfig = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/config/BotConfig.java"));

        assertTrue(formatter.contains("JDA: `"));
        assertTrue(botConfig.contains("JdaRuntimeInfo.version()"));
    }
}
