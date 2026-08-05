package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceFailureFormatterTest {

    @Test
    void keepsDeepestHttpCauseInsteadOfFriendlyWrapper() {
        RuntimeException wrapper = new RuntimeException(
                "Something broke when playing the track",
                new IOException("Invalid status code for soundcloud stream: 404"));

        String rendered = SourceFailureFormatter.describe("scsearch:aria", wrapper);

        assertTrue(rendered.contains("IOException: Invalid status code for soundcloud stream: 404"));
        assertTrue(rendered.contains("media=scsearch:aria"));
    }

    @Test
    void includesTrackPositionAndReference() {
        AudioTrack track = mock(AudioTrack.class);
        AudioTrackInfo info = mock(AudioTrackInfo.class);
        when(track.getInfo()).thenReturn(info);
        when(track.getPosition()).thenReturn(30_000L);
        when(track.getDuration()).thenReturn(193_000L);
        String rendered = SourceFailureFormatter.describe(track, new IOException("HTTP 404"));

        assertTrue(rendered.contains("position=30000/193000ms"));
    }

    @Test
    void keepsDiagnosticCompactForDiscordStatus() {
        String rendered = SourceFailureFormatter.describe(
                "x".repeat(400),
                new IOException("failure"));

        assertTrue(rendered.length() <= 240);
        assertTrue(rendered.endsWith("..."));
    }
}
