package ru.flawden.BascovDiscordBot.product.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.flawden.BascovDiscordBot.product.MusicProductService;
import ru.flawden.BascovDiscordBot.product.ProductPlaylistService;
import ru.flawden.BascovDiscordBot.library.PlaylistOperationResult;
import org.springframework.http.HttpStatus;
import ru.flawden.BascovDiscordBot.product.ProductPlaybackStreamService;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Objects;

/**
 * Authenticated v1 product API. Disabled by default; the optional remote profile is
 * host-loopback-published for a TLS reverse proxy. v1.36 enables owner-scoped playlist
 * mutations only; Discord voice/player mutations remain unavailable.
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "baskov.product-api.enabled", havingValue = "true")
public class ProductApiController {

    private final MusicProductService product;
    private final ProductApiMapper mapper;
    private final ProductApiAccessGuard access;
    private final ProductPlaybackStreamService playbackStreams;
    private final ProductPlaylistService playlists;

    public ProductApiController(
            MusicProductService product,
            ProductApiMapper mapper,
            ProductApiAccessGuard access,
            ProductPlaybackStreamService playbackStreams,
            ProductPlaylistService playlists) {
        this.product = Objects.requireNonNull(product, "product");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.access = Objects.requireNonNull(access, "access");
        this.playbackStreams = Objects.requireNonNull(playbackStreams, "playbackStreams");
        this.playlists = Objects.requireNonNull(playlists, "playlists");
    }

    @GetMapping("/capabilities")
    public ProductApiResponse.Capabilities capabilities() {
        return mapper.capabilities(product.capabilities());
    }

    @GetMapping("/guilds")
    public ProductApiResponse.Guilds guilds(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        var guildAccess = access.requireGuilds(authorization);
        return mapper.guilds(guildAccess.principal().userId(), guildAccess.guilds());
    }

    @GetMapping("/home")
    public ProductApiResponse.Home home(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam long guildId) {
        var principal = access.requireGuild(authorization, guildId);
        return mapper.home(product.home(guildId, principal.discordUserId()), principal.userId());
    }

    @GetMapping("/mixes")
    public ProductApiResponse.Mixes mixes(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam long guildId) {
        var principal = access.requireGuild(authorization, guildId);
        return mapper.mixes(product.mixes(guildId, principal.discordUserId()), principal.userId());
    }

    @GetMapping("/mixes/{stationSlug}")
    public ProductApiResponse.MixDetail mix(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String stationSlug,
            @RequestParam long guildId) {
        var principal = access.requireGuild(authorization, guildId);
        return mapper.mix(product.mix(guildId, principal.discordUserId(), stationSlug), principal.userId());
    }

    @GetMapping("/search")
    public ProductApiResponse.Search search(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam long guildId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit) {
        var principal = access.requireGuild(authorization, guildId);
        return mapper.search(
                product.search(guildId, principal.discordUserId(), query, limit),
                principal.userId());
    }

    @GetMapping("/player")
    public ProductApiResponse.Player player(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam long guildId) {
        access.requireGuild(authorization, guildId);
        return mapper.player(product.player(guildId));
    }

    @GetMapping("/library")
    public ProductApiResponse.Library library(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam long guildId) {
        var principal = access.requireGuild(authorization, guildId);
        return mapper.library(product.library(guildId, principal.discordUserId()), principal.userId());
    }

    @GetMapping("/playlists")
    public ProductApiResponse.Playlists playlists(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam long guildId) {
        var principal = access.requireGuild(authorization, guildId);
        return mapper.playlists(
                guildId,
                principal.userId(),
                principal.discordUserId(),
                playlists.playlists(guildId, principal.discordUserId()));
    }

    @GetMapping("/playlists/{name}")
    public ProductApiResponse.PlaylistDetail playlist(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String name,
            @RequestParam long guildId) {
        var principal = access.requireGuild(authorization, guildId);
        var playlist = playlists.playlist(guildId, principal.discordUserId(), name)
                .orElseThrow(() -> mutationError(
                        PlaylistOperationResult.Status.NOT_FOUND,
                        "Playlist not found"));
        return mapper.playlist(guildId, principal.userId(), principal.discordUserId(), playlist);
    }

    @PostMapping("/playlists")
    public ProductApiResponse.PlaylistDetail createPlaylist(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam long guildId,
            @RequestBody ProductPlaylistApiRequest.Create request) {
        var principal = access.requireGuild(authorization, guildId);
        var result = requireMutation(playlists.create(
                guildId,
                principal.discordUserId(),
                request == null ? null : request.name()));
        return mapper.playlist(guildId, principal.userId(), principal.discordUserId(), result.playlist());
    }

    @PostMapping("/playlists/{name}/tracks")
    public ProductApiResponse.PlaylistDetail addPlaylistTrack(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String name,
            @RequestParam long guildId,
            @RequestBody ProductPlaylistApiRequest.AddTrack request) {
        var principal = access.requireGuild(authorization, guildId);
        if (request == null) {
            throw new IllegalArgumentException("track request cannot be null");
        }
        var result = requireMutation(playlists.addTrack(
                guildId,
                principal.discordUserId(),
                principal.displayName(),
                name,
                request.artist(),
                request.title()));
        return mapper.playlist(guildId, principal.userId(), principal.discordUserId(), result.playlist());
    }

    @DeleteMapping("/playlists/{name}/tracks/{position}")
    public ProductApiResponse.PlaylistDetail removePlaylistTrack(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String name,
            @PathVariable int position,
            @RequestParam long guildId) {
        var principal = access.requireGuild(authorization, guildId);
        var result = requireMutation(playlists.removeTrack(
                guildId,
                principal.discordUserId(),
                name,
                position));
        return mapper.playlist(guildId, principal.userId(), principal.discordUserId(), result.playlist());
    }

    @PostMapping("/playlists/{name}/move")
    public ProductApiResponse.PlaylistDetail movePlaylistTrack(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String name,
            @RequestParam long guildId,
            @RequestBody ProductPlaylistApiRequest.MoveTrack request) {
        var principal = access.requireGuild(authorization, guildId);
        if (request == null) {
            throw new IllegalArgumentException("move request cannot be null");
        }
        var result = requireMutation(playlists.moveTrack(
                guildId,
                principal.discordUserId(),
                name,
                request.from(),
                request.to()));
        return mapper.playlist(guildId, principal.userId(), principal.discordUserId(), result.playlist());
    }

    @PostMapping("/playlists/{name}/rename")
    public ProductApiResponse.PlaylistDetail renamePlaylist(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String name,
            @RequestParam long guildId,
            @RequestBody ProductPlaylistApiRequest.Rename request) {
        var principal = access.requireGuild(authorization, guildId);
        var result = requireMutation(playlists.rename(
                guildId,
                principal.discordUserId(),
                name,
                request == null ? null : request.newName()));
        return mapper.playlist(guildId, principal.userId(), principal.discordUserId(), result.playlist());
    }

    @DeleteMapping("/playlists/{name}")
    public ProductApiResponse.PlaylistDetail deletePlaylist(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String name,
            @RequestParam long guildId) {
        var principal = access.requireGuild(authorization, guildId);
        var result = requireMutation(playlists.delete(guildId, principal.discordUserId(), name));
        return mapper.playlist(guildId, principal.userId(), principal.discordUserId(), result.playlist());
    }

    @GetMapping(value = "/playback/stream", produces = "audio/ogg")
    public void playbackStream(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam long guildId,
            @RequestParam String artist,
            @RequestParam String title,
            @RequestParam(defaultValue = "0") long startMillis,
            HttpServletResponse response) throws IOException {
        var principal = access.requireGuild(authorization, guildId);
        try (var session = playbackStreams.open(
                guildId,
                principal.discordUserId(),
                artist,
                title,
                startMillis)) {
            long effectiveStartMillis = Math.min(
                    startMillis,
                    Math.max(0L, session.durationMillis() - 1L));
            String artworkUrl = session.artworkUrl();
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("audio/ogg");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader("Accept-Ranges", "none");
            response.setHeader("X-Accel-Buffering", "no");
            response.setHeader("X-Baskov-Playback-Duration-Millis", Long.toString(session.durationMillis()));
            response.setHeader("X-Baskov-Playback-Start-Millis", Long.toString(effectiveStartMillis));
            if (!artworkUrl.isBlank()) {
                response.setHeader("X-Baskov-Playback-Artwork-Url", artworkUrl);
            }
            session.writeOgg(response.getOutputStream());
        }
    }
    private static PlaylistOperationResult requireMutation(PlaylistOperationResult result) {
        if (result == null) {
            throw new IllegalStateException("Playlist mutation returned no result");
        }
        return switch (result.status()) {
            case CREATED, ADDED, REMOVED, MOVED, RENAMED, DELETED -> result;
            default -> throw mutationError(result.status(), playlistMutationMessage(result.status()));
        };
    }

    private static ProductPlaylistMutationException mutationError(
            PlaylistOperationResult.Status status,
            String message) {
        return switch (status) {
            case NOT_FOUND -> new ProductPlaylistMutationException("PLAYLIST_NOT_FOUND", HttpStatus.NOT_FOUND, message);
            case FORBIDDEN -> new ProductPlaylistMutationException("PLAYLIST_FORBIDDEN", HttpStatus.FORBIDDEN, message);
            case ALREADY_EXISTS -> new ProductPlaylistMutationException("PLAYLIST_ALREADY_EXISTS", HttpStatus.CONFLICT, message);
            case PLAYLIST_LIMIT_REACHED -> new ProductPlaylistMutationException("PLAYLIST_LIMIT_REACHED", HttpStatus.CONFLICT, message);
            case TRACK_LIMIT_REACHED -> new ProductPlaylistMutationException("PLAYLIST_TRACK_LIMIT_REACHED", HttpStatus.CONFLICT, message);
            case INVALID_POSITION -> new ProductPlaylistMutationException("PLAYLIST_INVALID_POSITION", HttpStatus.BAD_REQUEST, message);
            case UNREPLAYABLE_TRACK -> new ProductPlaylistMutationException("PLAYLIST_TRACK_UNREPLAYABLE", HttpStatus.BAD_REQUEST, message);
            default -> new ProductPlaylistMutationException("PLAYLIST_MUTATION_FAILED", HttpStatus.BAD_REQUEST, message);
        };
    }

    private static String playlistMutationMessage(PlaylistOperationResult.Status status) {
        return switch (status) {
            case NOT_FOUND -> "Playlist not found";
            case FORBIDDEN -> "Only the playlist owner can modify it from this device";
            case ALREADY_EXISTS -> "A playlist with this name already exists";
            case PLAYLIST_LIMIT_REACHED -> "Guild playlist limit reached";
            case TRACK_LIMIT_REACHED -> "Playlist track limit reached";
            case INVALID_POSITION -> "Track position is outside the playlist";
            case UNREPLAYABLE_TRACK -> "Track cannot be persisted for later playback";
            default -> "Playlist mutation failed: " + status;
        };
    }

}
