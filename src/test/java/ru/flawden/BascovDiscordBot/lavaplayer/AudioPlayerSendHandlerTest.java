package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(1L, handler.frameRequestCount());
        assertEquals(Duration.ZERO, handler.lastFrameRequestAge());
        assertTrue(handler.hasRecentFrameRequest(Duration.ofSeconds(5)));

        now.addAndGet(Duration.ofSeconds(6).toNanos());
        assertEquals(Duration.ofSeconds(6), handler.lastFrameRequestAge());
        assertFalse(handler.hasRecentFrameRequest(Duration.ofSeconds(5)));

        handler.resetFrameTelemetry();
        assertEquals(0L, handler.frameRequestCount());
        assertEquals(null, handler.lastFrameRequestAge());
        assertFalse(handler.hasRecentFrameRequest(Duration.ofMinutes(1)));
    }
}
