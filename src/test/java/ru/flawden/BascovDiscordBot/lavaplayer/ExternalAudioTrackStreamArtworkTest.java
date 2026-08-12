package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalAudioTrackStreamArtworkTest {

    @Test
    void exposesResolvedHttpsArtworkUrl() {
        AudioPlayer player = mock(AudioPlayer.class);
        AudioTrack track = mock(AudioTrack.class);
        when(track.getDuration()).thenReturn(123_456L);
        when(track.getInfo()).thenReturn(new AudioTrackInfo(
                "Monster",
                "Skillet",
                123_456L,
                "track-id",
                false,
                "https://www.youtube.com/watch?v=example",
                "https://i.ytimg.com/vi/example/hqdefault.jpg",
                null));

        ExternalAudioTrackStream stream = new ExternalAudioTrackStream(player, track);

        assertEquals("https://i.ytimg.com/vi/example/hqdefault.jpg", stream.artworkUrl());
    }

    @Test
    void acceptsHttpAndHttpsArtworkUrls() {
        assertEquals(
                "http://example.test/cover.jpg",
                ExternalAudioTrackStream.normalizeArtworkUrl("http://example.test/cover.jpg"));
        assertEquals(
                "https://example.test/cover.jpg",
                ExternalAudioTrackStream.normalizeArtworkUrl("https://example.test/cover.jpg"));
    }

    @Test
    void rejectsUnsafeOrInvalidArtworkUrls() {
        assertEquals("", ExternalAudioTrackStream.normalizeArtworkUrl("file:///tmp/cover.jpg"));
        assertEquals("", ExternalAudioTrackStream.normalizeArtworkUrl("/relative/cover.jpg"));
        assertEquals("", ExternalAudioTrackStream.normalizeArtworkUrl("not a url"));
        assertEquals("", ExternalAudioTrackStream.normalizeArtworkUrl("   "));
        assertEquals("", ExternalAudioTrackStream.normalizeArtworkUrl(null));
    }
}
