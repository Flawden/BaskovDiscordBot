package ru.flawden.BascovDiscordBot.lavaplayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YoutubeSourceRuntimeInfoTest {

    @Test
    void exposesPinnedEngineIdentity() {
        assertEquals("lavalink-devs/youtube-source", YoutubeSourceRuntimeInfo.ENGINE);
        assertEquals("1.18.2", YoutubeSourceRuntimeInfo.VERSION);
        assertEquals("youtube-source 1.18.2", YoutubeSourceRuntimeInfo.statusLabel());
    }

    @Test
    void documentsDefaultMultiClientFallbackOrder() {
        assertTrue(YoutubeSourceRuntimeInfo.CLIENTS.contains("MUSIC"));
        assertTrue(YoutubeSourceRuntimeInfo.CLIENTS.contains("ANDROID_VR"));
        assertTrue(YoutubeSourceRuntimeInfo.CLIENTS.contains("WEB"));
        assertTrue(YoutubeSourceRuntimeInfo.CLIENTS.contains("WEBEMBEDDED"));
    }
}
