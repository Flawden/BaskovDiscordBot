package ru.flawden.BascovDiscordBot.product;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.lavaplayer.ExternalAudioTrackStream;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;
import ru.flawden.BascovDiscordBot.library.StoredTrack;
import ru.flawden.BascovDiscordBot.playback.PlaybackClientCapabilities;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;

/** Resolves provider-neutral mobile selections into the same durable StoredTrack used by Discord playlists. */
@Component
public class ProductPlaylistTrackResolver {

    private final PlayerManager playerManager;

    public ProductPlaylistTrackResolver(PlayerManager playerManager) {
        this.playerManager = Objects.requireNonNull(playerManager, "playerManager");
    }

    public StoredTrack resolve(
            TrackIdentity identity,
            long requesterUserId,
            String requesterDisplayName) {
        try (ExternalAudioTrackStream stream = playerManager.openExternalPlayback(
                        Objects.requireNonNull(identity, "identity"),
                        PlaybackClientCapabilities.android(Set.of(
                                MediaProvider.YOUTUBE,
                                MediaProvider.SOUNDCLOUD)))
                .join()) {
            return stream.storedTrack(requesterUserId, requesterDisplayName)
                    .orElseThrow(() -> new ProductPlaylistTrackUnavailableException(
                            "Resolved source cannot be persisted in a shared playlist"));
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new ProductPlaylistTrackUnavailableException(
                    "Unable to resolve playlist track: " + safeMessage(cause), cause);
        } catch (ProductPlaylistTrackUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ProductPlaylistTrackUnavailableException(
                    "Unable to resolve playlist track: " + safeMessage(exception), exception);
        }
    }

    private static String safeMessage(Throwable cause) {
        String message = cause == null ? null : cause.getMessage();
        return message == null || message.isBlank() ? "source unavailable" : message;
    }
}
