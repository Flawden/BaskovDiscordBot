package ru.flawden.BascovDiscordBot.playback;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.commands.music.MediaQueryResolver;

import java.util.Optional;

/**
 * Current primary Discord search transport. It owns YouTube search syntax, not track identity.
 */
@Component
public class YoutubePlaybackSourceProvider implements PlaybackSourceProvider {

    static final int PRIORITY = 100;

    @Override
    public MediaProvider provider() {
        return MediaProvider.YOUTUBE;
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
                MediaQueryResolver.YOUTUBE_SEARCH_PREFIX + query(track),
                PlaybackSourceReference.Kind.SEARCH,
                priority(),
                "primary YouTube search transport"));
    }

    private static String query(TrackIdentity track) {
        String value = "Неизвестно".equalsIgnoreCase(track.artist())
                ? track.title()
                : track.artist() + " " + track.title();
        String safe = value.trim().replaceAll("\\s+", " ");
        return safe.length() <= 100 ? safe : safe.substring(0, 100).trim();
    }
}
