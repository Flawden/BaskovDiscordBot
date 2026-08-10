package ru.flawden.BascovDiscordBot.product;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.home.HomeSnapshot;
import ru.flawden.BascovDiscordBot.home.MusicHomeService;

import java.util.Objects;

/**
 * Client-neutral product application boundary.
 *
 * <p>Discord and the external HTTP adapter must call these use cases instead of
 * reaching into runtime/repositories independently. Mutating use cases are
 * intentionally deferred until v1.29 authentication and device identity exist.</p>
 */
@Component
public class MusicProductService {

    private final MusicHomeService homeService;
    private final MusicProductReadPort readPort;

    public MusicProductService(MusicHomeService homeService, MusicProductReadPort readPort) {
        this.homeService = Objects.requireNonNull(homeService, "homeService");
        this.readPort = Objects.requireNonNull(readPort, "readPort");
    }

    public HomeSnapshot home(long guildId, long userId) {
        return homeService.snapshot(guildId, userId);
    }

    public ProductMixesSnapshot mixes(long guildId, long userId) {
        HomeSnapshot home = home(guildId, userId);
        return new ProductMixesSnapshot(
                home.guildId(),
                home.userId(),
                home.date(),
                home.continuation(),
                home.today(),
                home.forYou(),
                home.themes());
    }

    public ProductLibrarySnapshot library(long guildId, long userId) {
        HomeSnapshot home = home(guildId, userId);
        return new ProductLibrarySnapshot(
                home.guildId(),
                home.userId(),
                home.library().favorites(),
                home.library().personalHistory(),
                home.recent());
    }

    public ProductPlaybackSnapshot player(long guildId) {
        if (guildId <= 0L) {
            throw new IllegalArgumentException("guildId must be positive");
        }
        return readPort.playback(guildId);
    }

    public ProductCapabilities capabilities() {
        return ProductCapabilities.readOnlyPreview();
    }
}
