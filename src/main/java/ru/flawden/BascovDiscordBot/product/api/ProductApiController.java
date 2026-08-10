package ru.flawden.BascovDiscordBot.product.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.flawden.BascovDiscordBot.product.MusicProductService;

import java.util.Objects;

/**
 * Read-only v1 HTTP preview. Disabled by default and loopback-bound until v1.29
 * introduces Baskov users, authentication and authorized mutation use cases.
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "baskov.product-api.enabled", havingValue = "true")
public class ProductApiController {

    private final MusicProductService product;
    private final ProductApiMapper mapper;

    public ProductApiController(MusicProductService product, ProductApiMapper mapper) {
        this.product = Objects.requireNonNull(product, "product");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @GetMapping("/capabilities")
    public ProductApiResponse.Capabilities capabilities() {
        return mapper.capabilities(product.capabilities());
    }

    @GetMapping("/home")
    public ProductApiResponse.Home home(
            @RequestParam long guildId,
            @RequestParam long userId) {
        return mapper.home(product.home(guildId, userId));
    }

    @GetMapping("/mixes")
    public ProductApiResponse.Mixes mixes(
            @RequestParam long guildId,
            @RequestParam long userId) {
        return mapper.mixes(product.mixes(guildId, userId));
    }

    @GetMapping("/player")
    public ProductApiResponse.Player player(@RequestParam long guildId) {
        return mapper.player(product.player(guildId));
    }

    @GetMapping("/library")
    public ProductApiResponse.Library library(
            @RequestParam long guildId,
            @RequestParam long userId) {
        return mapper.library(product.library(guildId, userId));
    }
}
