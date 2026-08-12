package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModernYoutubeSourceContractTest {

    private static final Path ROOT = Path.of(".");

    @Test
    void pomPinsReleasedYoutubeSourceForLavaplayerTwo() throws Exception {
        String pom = Files.readString(ROOT.resolve("pom.xml"));

        assertTrue(pom.contains("<youtube-source.version>1.18.2</youtube-source.version>"));
        assertTrue(pom.contains("<groupId>dev.lavalink.youtube</groupId>"));
        assertTrue(pom.contains("<artifactId>v2</artifactId>"));
        assertTrue(pom.contains("<version>${youtube-source.version}</version>"));
        assertTrue(pom.contains("https://maven.lavalink.dev/releases"));
    }

    @Test
    void modernSourceReplacesEmbeddedLegacyYoutubeExtractor() throws Exception {
        String manager = Files.readString(ROOT.resolve(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));

        int modernRegistration = manager.indexOf(
                "this.audioPlayerManager.registerSourceManager(youtubeSourceManager)");
        int remainingSources = manager.indexOf("AudioSourceManagers.registerRemoteSources(");

        assertTrue(manager.contains("import dev.lavalink.youtube.YoutubeAudioSourceManager;"));
        assertTrue(manager.contains("new YoutubeAudioSourceManager("));
        assertTrue(modernRegistration >= 0);
        assertTrue(remainingSources > modernRegistration,
                "Modern YouTube source must be registered before the remaining remote sources");
        assertTrue(manager.contains("legacyYoutubeSourceClass()"));
        assertTrue(manager.contains("Class.forName("));
        assertTrue(manager.contains("Class<? extends AudioSourceManager> legacyYoutubeSourceClass()"));
        assertTrue(manager.contains(".asSubclass(AudioSourceManager.class)"));
        assertTrue(manager.contains(
                "com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager"));
        assertFalse(manager.contains(
                "com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class"));
        assertFalse(manager.contains(
                "AudioSourceManagers.registerRemoteSources(this.audioPlayerManager);"));
    }

    @Test
    void legacyYoutubeExclusionKeepsTheVarargsElementType() throws Exception {
        String manager = Files.readString(ROOT.resolve(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));

        assertTrue(manager.contains(
                "import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;"));
        assertTrue(manager.contains(
                "Class<? extends AudioSourceManager> legacyYoutubeSourceClass()"));
        assertTrue(manager.contains(".asSubclass(AudioSourceManager.class)"));
        assertFalse(manager.contains("private static Class<?> legacyYoutubeSourceClass()"));
    }

    @Test
    void startupAndDeploymentRequireModernYoutubeMarker() throws Exception {
        String runtime = Files.readString(ROOT.resolve(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/YoutubeSourceRuntimeInfo.java"));
        String manager = Files.readString(ROOT.resolve(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));
        String deploy = Files.readString(ROOT.resolve("deploy/remote-deploy.sh"));

        assertTrue(runtime.contains("Modern YouTube source ready:"));
        assertTrue(manager.contains("YoutubeSourceRuntimeInfo.STARTUP_MARKER"));
        assertTrue(manager.contains("legacyLavaplayerYoutube=disabled"));
        assertTrue(deploy.contains("Modern YouTube source startup marker is missing"));
    }

    @Test
    void statusExposesActualSourceEngineVersion() throws Exception {
        String status = Files.readString(ROOT.resolve(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/StatusMessageFormatter.java"));

        assertTrue(status.contains("YouTube engine:"));
        assertTrue(status.contains("YoutubeSourceRuntimeInfo.statusLabel()"));
    }

    @Test
    void expiredAutocompleteDoesNotUseDefaultStacktraceHandler() throws Exception {
        String interactions = Files.readString(ROOT.resolve(
                "src/main/java/ru/flawden/BascovDiscordBot/interactions/ModernInteractions.java"));

        assertTrue(interactions.contains("isExpiredAutocomplete(exception)"));
        assertTrue(interactions.contains("message.contains(\"10062\")"));
        assertTrue(interactions.contains("Autocomplete interaction expired before reply"));
        assertFalse(interactions.contains("event.replyChoices(choices).queue();"));
    }
}
