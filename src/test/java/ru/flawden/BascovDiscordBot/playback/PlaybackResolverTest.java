package ru.flawden.BascovDiscordBot.playback;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackResolverTest {

    private final PlaybackResolver resolver = new PlaybackResolver(List.of(
            new SoundCloudPlaybackSourceProvider(),
            new YoutubePlaybackSourceProvider()));

    @Test
    void ordersDiscordCandidatesByProviderPolicyNotInjectionOrder() {
        PlaybackResolution resolution = resolver.resolve(
                TrackIdentity.of("Green Day", "Holiday"),
                PlaybackClientCapabilities.discord());

        assertEquals(List.of(MediaProvider.YOUTUBE, MediaProvider.SOUNDCLOUD),
                resolution.candidates().stream().map(PlaybackSourceReference::provider).toList());
    }

    @Test
    void primaryDiscordCandidateRemainsYoutubeForCompatibility() {
        PlaybackSourceReference primary = resolver.resolve(
                TrackIdentity.of("Green Day", "Holiday"),
                PlaybackClientCapabilities.discord()).primary().orElseThrow();

        assertEquals(MediaProvider.YOUTUBE, primary.provider());
        assertTrue(primary.identifier().startsWith("ytsearch:"));
    }

    @Test
    void clientCapabilitiesCanSelectDifferentPrimaryProvider() {
        PlaybackClientCapabilities capabilities = new PlaybackClientCapabilities(
                PlaybackClient.ANDROID,
                Set.of(MediaProvider.SOUNDCLOUD),
                true);

        PlaybackResolution resolution = resolver.resolve(
                TrackIdentity.of("Green Day", "Holiday"),
                capabilities);

        assertEquals(MediaProvider.SOUNDCLOUD, resolution.primary().orElseThrow().provider());
    }

    @Test
    void resolutionCanBeEmptyWithoutInventingUnsupportedTransport() {
        PlaybackClientCapabilities capabilities = new PlaybackClientCapabilities(
                PlaybackClient.WEB,
                Set.of(MediaProvider.HTTP),
                true);

        PlaybackResolution resolution = resolver.resolve(
                TrackIdentity.of("Green Day", "Holiday"),
                capabilities);

        assertFalse(resolution.resolved());
        assertTrue(resolution.primary().isEmpty());
    }

    @Test
    void resolutionKeepsLogicalTrackSeparateFromProviderReference() {
        TrackIdentity identity = TrackIdentity.of("Beyoncé", "Halo");
        PlaybackResolution resolution = resolver.resolve(identity, PlaybackClientCapabilities.discord());

        assertEquals("beyonce::halo", resolution.track().stableKey());
        assertFalse(resolution.track().stableKey().contains("youtube"));
        assertFalse(resolution.track().stableKey().contains("soundcloud"));
    }
}
