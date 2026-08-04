package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AudioPlayerSendHandlerTest {

    @Test
    void tracksActualJdaFrameDemandUsingMonotonicTime() {
        AudioPlayer player = mock(AudioPlayer.class);
        when(player.provide(any())).thenReturn(false);
        AtomicLong now = new AtomicLong(Duration.ofSeconds(10).toNanos());
        AudioPlayerSendHandler handler = new AudioPlayerSendHandler(player, now::get);

        assertFalse(handler.hasRecentFrameRequest(Duration.ofSeconds(5)));

        handler.canProvide();
        assertTrue(handler.hasRecentFrameRequest(Duration.ofSeconds(5)));

        now.addAndGet(Duration.ofSeconds(6).toNanos());
        assertFalse(handler.hasRecentFrameRequest(Duration.ofSeconds(5)));

        handler.resetFrameTelemetry();
        assertFalse(handler.hasRecentFrameRequest(Duration.ofMinutes(1)));
    }
}
