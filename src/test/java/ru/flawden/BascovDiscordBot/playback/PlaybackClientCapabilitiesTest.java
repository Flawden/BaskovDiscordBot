package ru.flawden.BascovDiscordBot.playback;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackClientCapabilitiesTest {

    @Test
    void discordAllowsCurrentSearchProviders() {
        PlaybackClientCapabilities capabilities = PlaybackClientCapabilities.discord();

        assertTrue(capabilities.supports(MediaProvider.YOUTUBE));
        assertTrue(capabilities.supports(MediaProvider.SOUNDCLOUD));
        assertTrue(capabilities.searchIdentifiersSupported());
    }

    @Test
    void clientCanExposeProviderSpecificCapabilitySet() {
        PlaybackClientCapabilities capabilities = PlaybackClientCapabilities.android(Set.of(MediaProvider.HTTP));

        assertTrue(capabilities.supports(MediaProvider.HTTP));
        assertFalse(capabilities.supports(MediaProvider.YOUTUBE));
    }

    @Test
    void unknownProviderIsNeverSupportedImplicitly() {
        PlaybackClientCapabilities capabilities = new PlaybackClientCapabilities(
                PlaybackClient.WEB,
                Set.of(),
                true);

        assertFalse(capabilities.supports(MediaProvider.UNKNOWN));
    }
}
