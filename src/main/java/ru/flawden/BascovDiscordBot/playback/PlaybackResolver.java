package ru.flawden.BascovDiscordBot.playback;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.config.PlaybackResilienceProperties;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Decides which provider-specific transports may represent a logical track for a client.
 *
 * <p>v1.27 keeps source identity separate while adding runtime provider health. Providers in an
 * active cooldown are omitted from new resolutions; after cooldown they re-enter as a probe and a
 * successful load closes the circuit.</p>
 */
@Component
public class PlaybackResolver {

    private final List<PlaybackSourceProvider> providers;
    private final PlaybackProviderHealthRegistry providerHealth;

    @Autowired
    public PlaybackResolver(
            List<PlaybackSourceProvider> providers,
            PlaybackProviderHealthRegistry providerHealth) {
        this.providers = providers == null
                ? List.of()
                : providers.stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingInt(PlaybackSourceProvider::priority)
                                .thenComparing(provider -> provider.provider().name()))
                        .toList();
        this.providerHealth = Objects.requireNonNull(providerHealth, "providerHealth");
    }

    /** Compatibility/testing constructor with default in-memory resilience settings. */
    public PlaybackResolver(List<PlaybackSourceProvider> providers) {
        this(providers, new PlaybackProviderHealthRegistry(new PlaybackResilienceProperties()));
    }

    public PlaybackResolution resolve(
            TrackIdentity track,
            PlaybackClientCapabilities capabilities) {
        Objects.requireNonNull(track, "track");
        PlaybackClientCapabilities safeCapabilities = Objects.requireNonNull(
                capabilities,
                "capabilities");

        List<PlaybackSourceReference> supported = providers.stream()
                .filter(provider -> safeCapabilities.supports(provider.provider()))
                .map(provider -> provider.resolve(track, safeCapabilities))
                .flatMap(java.util.Optional::stream)
                .toList();

        List<PlaybackSourceReference> candidates = supported.stream()
                .filter(reference -> providerHealth.isAvailable(reference.provider()))
                .sorted(Comparator
                        .comparingInt((PlaybackSourceReference reference) ->
                                safePriority(reference.priority(), providerHealth.rankingPenalty(reference.provider())))
                        .thenComparing(reference -> reference.provider().name()))
                .toList();

        Duration retryAfter = candidates.isEmpty() && !supported.isEmpty()
                ? providerHealth.retryAfter(supported.stream().map(PlaybackSourceReference::provider).toList())
                : Duration.ZERO;

        return new PlaybackResolution(track, safeCapabilities.client(), candidates, retryAfter);
    }

    public void recordSuccess(PlaybackSourceReference source) {
        if (source != null) {
            providerHealth.recordSuccess(source.provider());
        }
    }

    public void recordFailure(PlaybackSourceReference source, String reason) {
        if (source != null) {
            providerHealth.recordFailure(source.provider(), reason);
        }
    }

    public void recordMiss(PlaybackSourceReference source) {
        if (source != null) {
            providerHealth.recordMiss(source.provider());
        }
    }

    public void recordFallback(
            PlaybackSourceReference from,
            PlaybackSourceReference to,
            String reason) {
        if (from != null) {
            providerHealth.recordFallback(from.provider(), to == null ? MediaProvider.UNKNOWN : to.provider(), reason);
        }
    }

    public List<PlaybackProviderHealthSnapshot> healthSnapshots() {
        return providerHealth.snapshots();
    }

    private static int safePriority(int base, int penalty) {
        long value = (long) base + Math.max(0, penalty);
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
