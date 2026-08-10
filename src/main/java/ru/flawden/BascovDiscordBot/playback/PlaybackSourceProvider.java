package ru.flawden.BascovDiscordBot.playback;

import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;

import java.util.Optional;

/**
 * Converts a logical track into a provider-specific playback candidate when the client supports it.
 */
public interface PlaybackSourceProvider {

    MediaProvider provider();

    int priority();

    Optional<PlaybackSourceReference> resolve(
            TrackIdentity track,
            PlaybackClientCapabilities capabilities);
}
