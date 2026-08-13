package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YoutubeThumbnailClientContractTest {

    @Test
    void playerManagerUsesThumbnailAwareYoutubeClients() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/ru/flawden/BascovDiscordBot/lavaplayer/PlayerManager.java"));

        assertTrue(source.contains("MusicWithThumbnail()"));
        assertTrue(source.contains("AndroidVrWithThumbnail()"));
        assertTrue(source.contains("WebWithThumbnail()"));
        assertTrue(source.contains("WebEmbeddedWithThumbnail()"));
        assertTrue(source.contains("YoutubeSourceOptions"));
        assertTrue(source.contains(".setRemoteCipher("));
        assertTrue(source.contains("BASKOV_YOUTUBE_CIPHER_URL"));
        assertTrue(source.contains("BASKOV_YOUTUBE_CIPHER_PASSWORD"));
        assertFalse(source.contains(
                "YoutubeAudioSourceManager youtubeSourceManager = new YoutubeAudioSourceManager();"));
    }
}
