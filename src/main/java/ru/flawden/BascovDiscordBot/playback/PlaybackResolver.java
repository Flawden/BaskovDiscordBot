package ru.flawden.BascovDiscordBot.playback;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Decides which provider-specific transports may represent a logical track for a client.
 *
 * <p>v1.26 resolves and orders candidates only. Automatic retry, health scoring and circuit
 * breaking belong to the provider-resilience layer introduced after this abstraction.</p>
 */
@Component
public class PlaybackResolver {

    private final List<PlaybackSourceProvider> providers;

    public PlaybackResolver(List<PlaybackSourceProvider> providers) {
        this.providers = providers == null
                ? List.of()
                : providers.stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingInt(PlaybackSourceProvider::priority)
                                .thenComparing(provider -> provider.provider().name()))
                        .toList();
    }

    public PlaybackResolution resolve(
            TrackIdentity track,
            PlaybackClientCapabilities capabilities) {
        Objects.requireNonNull(track, "track");
        PlaybackClientCapabilities safeCapabilities = Objects.requireNonNull(
                capabilities,
                "capabilities");

        List<PlaybackSourceReference> candidates = providers.stream()
                .filter(provider -> safeCapabilities.supports(provider.provider()))
                .map(provider -> provider.resolve(track, safeCapabilities))
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparingInt(PlaybackSourceReference::priority)
                        .thenComparing(reference -> reference.provider().name()))
                .toList();

        return new PlaybackResolution(track, safeCapabilities.client(), candidates);
    }
}
