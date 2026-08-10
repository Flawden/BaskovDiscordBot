package ru.flawden.BascovDiscordBot.playback;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundCloudPlaybackSourceProviderTest {

    private final SoundCloudPlaybackSourceProvider provider = new SoundCloudPlaybackSourceProvider();

    @Test
    void resolvesLogicalTrackIntoSoundCloudSearchTransport() {
        PlaybackSourceReference reference = provider.resolve(
                TrackIdentity.of("Sum 41", "Fat Lip"),
                PlaybackClientCapabilities.discord()).orElseThrow();

        assertEquals(MediaProvider.SOUNDCLOUD, reference.provider());
        assertEquals("scsearch:Sum 41 Fat Lip", reference.identifier());
    }

    @Test
    void respectsClientProviderCapabilities() {
        PlaybackClientCapabilities capabilities = new PlaybackClientCapabilities(
                PlaybackClient.ANDROID,
                Set.of(MediaProvider.YOUTUBE),
                true);

        assertTrue(provider.resolve(TrackIdentity.of("Sum 41", "Fat Lip"), capabilities).isEmpty());
    }
}
