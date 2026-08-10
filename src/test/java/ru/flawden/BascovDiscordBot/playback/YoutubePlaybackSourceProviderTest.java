package ru.flawden.BascovDiscordBot.playback;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YoutubePlaybackSourceProviderTest {

    private final YoutubePlaybackSourceProvider provider = new YoutubePlaybackSourceProvider();

    @Test
    void resolvesLogicalTrackIntoYoutubeSearchTransport() {
        PlaybackSourceReference reference = provider.resolve(
                TrackIdentity.of("Linkin Park", "Numb"),
                PlaybackClientCapabilities.discord()).orElseThrow();

        assertEquals(MediaProvider.YOUTUBE, reference.provider());
        assertEquals("ytsearch:Linkin Park Numb", reference.identifier());
        assertEquals(PlaybackSourceReference.Kind.SEARCH, reference.kind());
    }

    @Test
    void doesNotResolveWhenClientDoesNotSupportYoutube() {
        PlaybackClientCapabilities capabilities = new PlaybackClientCapabilities(
                PlaybackClient.ANDROID,
                Set.of(MediaProvider.SOUNDCLOUD),
                true);

        assertTrue(provider.resolve(TrackIdentity.of("A", "B"), capabilities).isEmpty());
    }

    @Test
    void doesNotResolveWhenSearchIdentifiersAreUnavailable() {
        PlaybackClientCapabilities capabilities = new PlaybackClientCapabilities(
                PlaybackClient.WEB,
                Set.of(MediaProvider.YOUTUBE),
                false);

        assertTrue(provider.resolve(TrackIdentity.of("A", "B"), capabilities).isEmpty());
    }
}
