package ru.flawden.BascovDiscordBot.playback;

import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;

import java.util.Objects;
import java.util.Set;

/**
 * Provider-neutral description of what a client is currently able to play.
 */
public record PlaybackClientCapabilities(
        PlaybackClient client,
        Set<MediaProvider> supportedProviders,
        boolean searchIdentifiersSupported) {

    public PlaybackClientCapabilities {
        client = Objects.requireNonNullElse(client, PlaybackClient.UNKNOWN);
        supportedProviders = supportedProviders == null ? Set.of() : Set.copyOf(supportedProviders);
    }

    public static PlaybackClientCapabilities discord() {
        return new PlaybackClientCapabilities(
                PlaybackClient.DISCORD,
                Set.of(MediaProvider.YOUTUBE, MediaProvider.SOUNDCLOUD),
                true);
    }

    public static PlaybackClientCapabilities android(Set<MediaProvider> supportedProviders) {
        return new PlaybackClientCapabilities(PlaybackClient.ANDROID, supportedProviders, true);
    }

    public boolean supports(MediaProvider provider) {
        return provider != null && supportedProviders.contains(provider);
    }
}
