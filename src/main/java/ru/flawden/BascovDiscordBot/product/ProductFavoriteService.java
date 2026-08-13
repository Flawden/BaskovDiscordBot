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

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;

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

    public Page page(long guildId, long userId, Integer offset, Integer limit) {
        validate(guildId, userId);
        List<StoredTrack> all = library.favorites(guildId, userId);
        if (offset == null && limit == null) {
            return new Page(all.size(), 0, all.size(), false, all);
        }
        int safeOffset = Math.max(0, offset == null ? 0 : offset);
        int safeLimit = Math.max(1, Math.min(MAX_PAGE_SIZE, limit == null ? DEFAULT_PAGE_SIZE : limit));
        int from = Math.min(safeOffset, all.size());
        int to = Math.min(all.size(), from + safeLimit);
        return new Page(all.size(), from, safeLimit, to < all.size(), all.subList(from, to));
    }

    public List<String> stableKeys(long guildId, long userId) {
        validate(guildId, userId);
        return library.favorites(guildId, userId).stream()
                .map(track -> track.trackIdentity().stableKey())
                .toList();
    }

    public boolean contains(long guildId, long userId, String stableKey) {
        validate(guildId, userId);
        return library.favoriteByStableKey(guildId, userId, stableKey).isPresent();
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

    public FavoriteOperationResult removeByStableKey(long guildId, long userId, String stableKey) {
        validate(guildId, userId);
        return library.removeFavoriteByStableKey(guildId, userId, stableKey);
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

    public record Page(
            int total,
            int offset,
            int limit,
            boolean hasMore,
            List<StoredTrack> tracks) {
        public Page {
            tracks = List.copyOf(tracks == null ? List.of() : tracks);
        }
    }

    private static String safeMessage(Throwable cause) {
        String message = cause == null ? null : cause.getMessage();
        return message == null || message.isBlank() ? "source unavailable" : message;
    }
}
