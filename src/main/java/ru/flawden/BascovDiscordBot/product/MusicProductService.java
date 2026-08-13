package ru.flawden.BascovDiscordBot.product;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.home.HomeSnapshot;
import ru.flawden.BascovDiscordBot.home.MusicHomeService;
import ru.flawden.BascovDiscordBot.library.StoredTrack;
import ru.flawden.BascovDiscordBot.recommendation.PersonalizedStation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Client-neutral product application boundary.
 *
 * <p>Discord and the external HTTP adapter must call these use cases instead of
 * reaching into runtime/repositories independently. v1.36 adds playlist mutations through
 * a separate owner-scoped ProductPlaylistService; voice/player mutations remain outside this read service.</p>
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

    public ProductMixDetailSnapshot mix(long guildId, long userId, String stationSlug) {
        PersonalizedStation station = curatedStation(stationSlug);
        List<HomeSnapshot.TrackPreview> seeds = previews(readPort.stationSeeds(guildId, userId, station));
        return new ProductMixDetailSnapshot(
                guildId,
                userId,
                station.slug(),
                station.label(),
                station.description(),
                !seeds.isEmpty(),
                station.dailySeeded(),
                seeds);
    }

    public ProductLibrarySnapshot library(long guildId, long userId) {
        HomeSnapshot home = home(guildId, userId);
        List<HomeSnapshot.TrackPreview> favorites = previews(readPort.favorites(guildId, userId));
        List<HomeSnapshot.TrackPreview> history = previews(readPort.personalHistory(guildId, userId));
        return new ProductLibrarySnapshot(
                home.guildId(),
                home.userId(),
                favorites.size(),
                history.size(),
                home.recent(),
                favorites,
                history);
    }

    public ProductPlaybackSnapshot player(long guildId) {
        if (guildId <= 0L) {
            throw new IllegalArgumentException("guildId must be positive");
        }
        return readPort.playback(guildId);
    }

    public ProductSearchSnapshot search(long guildId, long userId, String query, int maxResults) {
        if (guildId <= 0L) {
            throw new IllegalArgumentException("guildId must be positive");
        }
        if (userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("query cannot be blank");
        }
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("query is too long");
        }
        if (maxResults < 1 || maxResults > 10) {
            throw new IllegalArgumentException("limit must be between 1 and 10");
        }
        return new ProductSearchSnapshot(
                guildId,
                userId,
                normalized,
                readPort.search(guildId, normalized, maxResults));
    }

    public ProductCapabilities capabilities() {
        return ProductCapabilities.authenticatedRead();
    }

    private static PersonalizedStation curatedStation(String stationSlug) {
        if (stationSlug == null || stationSlug.isBlank()) {
            throw new IllegalArgumentException("stationSlug cannot be blank");
        }
        String normalized = stationSlug.trim().toLowerCase(Locale.ROOT);
        return PersonalizedStation.curatedStations().stream()
                .filter(station -> station.slug().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown curated station: " + stationSlug));
    }

    private static List<HomeSnapshot.TrackPreview> previews(List<StoredTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return List.of();
        }
        return tracks.stream()
                .filter(Objects::nonNull)
                .map(track -> new HomeSnapshot.TrackPreview(track.title(), track.author()))
                .toList();
    }
}
