package ru.flawden.BascovDiscordBot.commands.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaProviderTest {

    @Test
    void recognisesYoutubeSearchAndUrls() {
        assertEquals(MediaProvider.YOUTUBE, MediaProvider.fromIdentifier("ytsearch:test"));
        assertEquals(MediaProvider.YOUTUBE,
                MediaProvider.fromUri("https://www.youtube.com/watch?v=test"));
        assertEquals(MediaProvider.YOUTUBE,
                MediaProvider.fromUri("https://music.youtube.com/watch?v=test"));
        assertEquals(MediaProvider.YOUTUBE,
                MediaProvider.fromUri("https://youtu.be/test"));
    }

    @Test
    void recognisesSoundCloudSearchAndUrls() {
        assertEquals(MediaProvider.SOUNDCLOUD, MediaProvider.fromIdentifier("scsearch:test"));
        assertEquals(MediaProvider.SOUNDCLOUD,
                MediaProvider.fromUri("https://soundcloud.com/example/test"));
        assertEquals(MediaProvider.SOUNDCLOUD,
                MediaProvider.fromUri("https://on.soundcloud.com/example"));
    }

    @Test
    void keepsGenericHttpDistinctFromKnownProviders() {
        assertEquals(MediaProvider.HTTP,
                MediaProvider.fromUri("https://cdn.example.com/audio.mp3"));
    }

    @Test
    void malformedAndBlankValuesRemainUnknown() {
        assertEquals(MediaProvider.UNKNOWN, MediaProvider.fromIdentifier(""));
        assertEquals(MediaProvider.UNKNOWN, MediaProvider.fromUri("not a url"));
    }
}
