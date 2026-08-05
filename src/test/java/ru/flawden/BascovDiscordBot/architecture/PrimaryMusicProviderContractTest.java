package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimaryMusicProviderContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void plainTextSearchUsesYoutubeInsteadOfSoundCloud() throws Exception {
        String resolver = Files.readString(MAIN.resolve("commands/music/MediaQueryResolver.java"));

        assertTrue(resolver.contains("YOUTUBE_SEARCH_PREFIX = \"ytsearch:\""));
        assertTrue(resolver.contains("return YOUTUBE_SEARCH_PREFIX + trimmed"));
        assertFalse(resolver.contains("return \"scsearch:\" + trimmed"));
    }

    @Test
    void directYoutubeAndSoundCloudLinksRemainAllowed() throws Exception {
        String resolver = Files.readString(MAIN.resolve("commands/music/MediaQueryResolver.java"));
        String provider = Files.readString(MAIN.resolve("commands/music/MediaProvider.java"));

        assertTrue(resolver.contains("youtube.com"));
        assertTrue(resolver.contains("youtu.be"));
        assertTrue(resolver.contains("soundcloud.com"));
        assertTrue(provider.contains("YOUTUBE(\"YouTube\")"));
        assertTrue(provider.contains("SOUNDCLOUD(\"SoundCloud\")"));
    }

    @Test
    void hiddenFallbackPoolWorksForBothSearchProviders() throws Exception {
        String manager = Files.readString(MAIN.resolve("lavaplayer/PlayerManager.java"));

        assertTrue(manager.contains("MediaQueryResolver.YOUTUBE_SEARCH_PREFIX"));
        assertTrue(manager.contains("MediaQueryResolver.SOUNDCLOUD_SEARCH_PREFIX"));
        assertTrue(manager.contains(".limit(9)"));
    }

    @Test
    void userFacingViewsExposeActualTrackProvider() throws Exception {
        String embeds = Files.readString(MAIN.resolve("commands/music/MusicEmbeds.java"));
        String commands = Files.readString(MAIN.resolve("interactions/ModernCommandCatalog.java"));
        String status = Files.readString(MAIN.resolve("interactions/StatusMessageFormatter.java"));

        assertTrue(embeds.contains("**Источник:**"));
        assertTrue(embeds.contains("providerLabel"));
        assertTrue(commands.contains("поиск YouTube"));
        assertTrue(status.contains("Основной поиск: `YouTube`"));
        assertTrue(status.contains("YoutubeSourceRuntimeInfo.statusLabel()"));
    }
}
