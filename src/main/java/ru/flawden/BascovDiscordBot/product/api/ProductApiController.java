package ru.flawden.BascovDiscordBot.product.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.flawden.BascovDiscordBot.product.MusicProductService;
import ru.flawden.BascovDiscordBot.product.ProductPlaybackStreamService;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Objects;

/**
 * Authenticated read-only v1 product API. Disabled by default; the optional remote profile
 * is host-loopback-published for a TLS reverse proxy. Music mutations remain unavailable.
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "baskov.product-api.enabled", havingValue = "true")
public class ProductApiController {

    private final MusicProductService product;
    private final ProductApiMapper mapper;
    private final ProductApiAccessGuard access;
    private final ProductPlaybackStreamService playbackStreams;

    public ProductApiController(
            MusicProductService product,
            ProductApiMapper mapper,
            ProductApiAccessGuard access,
            ProductPlaybackStreamService playbackStreams) {
        this.product = Objects.requireNonNull(product, "product");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.access = Objects.requireNonNull(access, "access");
        this.playbackStreams = Objects.requireNonNull(playbackStreams, "playbackStreams");
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
}
