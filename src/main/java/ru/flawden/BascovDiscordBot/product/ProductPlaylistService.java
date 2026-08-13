package ru.flawden.BascovDiscordBot.product;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.library.MusicLibraryRepository;
import ru.flawden.BascovDiscordBot.library.PlaylistOperationResult;
import ru.flawden.BascovDiscordBot.library.StoredPlaylist;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Client-neutral shared-playlist use cases used by Discord persistence and external clients. */
@Component
public class ProductPlaylistService {

    private final MusicLibraryRepository library;
    private final ProductPlaylistTrackResolver trackResolver;

    public ProductPlaylistService(
            MusicLibraryRepository library,
            ProductPlaylistTrackResolver trackResolver) {
        this.library = Objects.requireNonNull(library, "library");
        this.trackResolver = Objects.requireNonNull(trackResolver, "trackResolver");
    }

    public List<StoredPlaylist> playlists(long guildId, long actorUserId) {
        validate(guildId, actorUserId);
        return library.playlists(guildId);
    }

    public Optional<StoredPlaylist> playlist(long guildId, long actorUserId, String name) {
        validate(guildId, actorUserId);
        return library.playlist(guildId, name);
    }

    public PlaylistOperationResult create(long guildId, long actorUserId, String name) {
        validate(guildId, actorUserId);
        return library.createPlaylist(guildId, actorUserId, name);
    }

    public PlaylistOperationResult addTrack(
            long guildId,
            long actorUserId,
            String actorDisplayName,
            String playlistName,
            String artist,
            String title) {
        validate(guildId, actorUserId);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title cannot be blank");
        }
        StoredTrack track = trackResolver.resolve(
                TrackIdentity.of(artist, title),
                actorUserId,
                actorDisplayName);
        return library.addTrack(guildId, playlistName, actorUserId, false, track);
    }

    public PlaylistOperationResult removeTrack(
            long guildId,
            long actorUserId,
            String playlistName,
            int oneBasedPosition) {
        validate(guildId, actorUserId);
        return library.removeTrack(guildId, playlistName, actorUserId, false, oneBasedPosition);
    }

    public PlaylistOperationResult moveTrack(
            long guildId,
            long actorUserId,
            String playlistName,
            int fromOneBasedPosition,
            int toOneBasedPosition) {
        validate(guildId, actorUserId);
        return library.moveTrack(
                guildId,
                playlistName,
                actorUserId,
                false,
                fromOneBasedPosition,
                toOneBasedPosition);
    }

    public PlaylistOperationResult rename(
            long guildId,
            long actorUserId,
            String playlistName,
            String newName) {
        validate(guildId, actorUserId);
        return library.renamePlaylist(guildId, playlistName, newName, actorUserId, false);
    }

    public PlaylistOperationResult delete(long guildId, long actorUserId, String playlistName) {
        validate(guildId, actorUserId);
        return library.deletePlaylist(guildId, playlistName, actorUserId, false);
    }

    private static void validate(long guildId, long actorUserId) {
        if (guildId <= 0L) {
            throw new IllegalArgumentException("guildId must be positive");
        }
        if (actorUserId <= 0L) {
            throw new IllegalArgumentException("actorUserId must be positive");
        }
    }
}
