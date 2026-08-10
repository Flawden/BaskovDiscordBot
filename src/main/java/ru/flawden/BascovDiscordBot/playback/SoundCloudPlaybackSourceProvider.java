package ru.flawden.BascovDiscordBot.playback;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.commands.music.MediaQueryResolver;

import java.util.Optional;

/**
 * Secondary Discord search transport and automatic fallback candidate.
 */
@Component
public class SoundCloudPlaybackSourceProvider implements PlaybackSourceProvider {

    static final int PRIORITY = 200;

    @Override
    public MediaProvider provider() {
        return MediaProvider.SOUNDCLOUD;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public Optional<PlaybackSourceReference> resolve(
            TrackIdentity track,
            PlaybackClientCapabilities capabilities) {
        if (track == null
                || capabilities == null
                || !capabilities.searchIdentifiersSupported()
                || !capabilities.supports(provider())) {
            return Optional.empty();
        }
        return Optional.of(new PlaybackSourceReference(
                provider(),
                MediaQueryResolver.SOUNDCLOUD_SEARCH_PREFIX + query(track),
                PlaybackSourceReference.Kind.SEARCH,
                priority(),
                "secondary SoundCloud search transport"));
    }

    private static String query(TrackIdentity track) {
        String value = "Неизвестно".equalsIgnoreCase(track.artist())
                ? track.title()
                : track.artist() + " " + track.title();
        String safe = value.trim().replaceAll("\\s+", " ");
        return safe.length() <= 100 ? safe : safe.substring(0, 100).trim();
    }
}
