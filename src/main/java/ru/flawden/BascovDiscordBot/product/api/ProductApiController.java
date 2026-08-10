package ru.flawden.BascovDiscordBot.product.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.flawden.BascovDiscordBot.product.MusicProductService;

import java.util.Objects;

/**
 * Authenticated read-only v1 product API. Disabled by default and loopback-bound;
 * music mutations remain intentionally unavailable until a later authenticated API release.
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "baskov.product-api.enabled", havingValue = "true")
public class ProductApiController {

    private final MusicProductService product;
    private final ProductApiMapper mapper;
    private final ProductApiAccessGuard access;

    public ProductApiController(MusicProductService product, ProductApiMapper mapper, ProductApiAccessGuard access) {
        this.product = Objects.requireNonNull(product, "product");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.access = Objects.requireNonNull(access, "access");
    }

    @GetMapping("/capabilities")
    public ProductApiResponse.Capabilities capabilities() {
        return mapper.capabilities(product.capabilities());
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
}
