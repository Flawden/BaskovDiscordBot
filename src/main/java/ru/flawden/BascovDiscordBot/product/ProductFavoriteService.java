package ru.flawden.BascovDiscordBot.product;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.library.FavoriteOperationResult;
import ru.flawden.BascovDiscordBot.library.MusicLibraryRepository;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.util.List;
import java.util.Objects;

/** Client-neutral personal-favorite use cases shared by Android and Discord persistence. */
@Component
public class ProductFavoriteService {

    private final MusicLibraryRepository library;
    private final ProductPlaylistTrackResolver trackResolver;

    public ProductFavoriteService(
            MusicLibraryRepository library,
            ProductPlaylistTrackResolver trackResolver) {
        this.library = Objects.requireNonNull(library, "library");
        this.trackResolver = Objects.requireNonNull(trackResolver, "trackResolver");
    }

    public List<StoredTrack> favorites(long guildId, long userId) {
        validate(guildId, userId);
        return library.favorites(guildId, userId);
    }

    public FavoriteOperationResult add(
            long guildId,
            long userId,
            String displayName,
            String artist,
            String title) {
        validate(guildId, userId);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title cannot be blank");
        }
        StoredTrack track;
        try {
            track = trackResolver.resolve(
                    TrackIdentity.of(artist, title),
                    userId,
                    displayName);
        } catch (ProductPlaylistTrackUnavailableException exception) {
            throw new ProductFavoriteTrackUnavailableException(
                    "Unable to resolve favorite track: " + safeMessage(exception),
                    exception);
        }
        return library.addFavorite(guildId, userId, track);
    }

    public FavoriteOperationResult remove(long guildId, long userId, int oneBasedPosition) {
        validate(guildId, userId);
        return library.removeFavorite(guildId, userId, oneBasedPosition);
    }

    public FavoriteOperationResult clear(long guildId, long userId) {
        validate(guildId, userId);
        return library.clearFavorites(guildId, userId);
    }

    private static void validate(long guildId, long userId) {
        if (guildId <= 0L) {
            throw new IllegalArgumentException("guildId must be positive");
        }
        if (userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }

    private static String safeMessage(Throwable cause) {
        String message = cause == null ? null : cause.getMessage();
        return message == null || message.isBlank() ? "source unavailable" : message;
    }
}
