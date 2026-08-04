package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Защищает изолированную source-миграцию JDA 5 -> 6.
 */
class JdaSixSourceMigrationContractTest {

    @Test
    void messageComponentsUseTheJdaSixPackages() throws Exception {
        String controls = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/MusicControls.java"));

        assertTrue(controls.contains("net.dv8tion.jda.api.components.ActionRow"));
        assertTrue(controls.contains("net.dv8tion.jda.api.components.LayoutComponent"));
        assertTrue(controls.contains("net.dv8tion.jda.api.components.buttons.Button"));
        assertFalse(controls.contains("net.dv8tion.jda.api.interactions.components"),
                "JDA 5 component packages must not return after the JDA 6 migration");
    }

    @Test
    void migrationDoesNotPullSpringBootOrLavaPlayerForward() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<version>6.5.0</version>"));
        assertTrue(pom.contains("<version>3.4.3</version>"));
        assertTrue(pom.contains("<version>2.2.3</version>"));
        assertFalse(pom.contains("<version>4.1.0</version>"));
        assertFalse(pom.contains("<version>2.2.7</version>"));
    }

    @Test
    void playbackSuccessStillDependsOnDiscordFramePolling() throws Exception {
        String manager = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));
        String policy = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlaybackReadinessPolicy.java"));
        String embeds = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/commands/music/MusicEmbeds.java"));

        assertTrue(manager.contains("awaitPlaybackReady"));
        assertTrue(compact(policy).contains("currentFrameRequests>baselineFrameRequests"));
        assertFalse(embeds.contains("Воспроизведение началось"));
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", "");
    }
}
