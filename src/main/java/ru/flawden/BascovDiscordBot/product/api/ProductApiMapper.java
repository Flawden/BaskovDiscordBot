package ru.flawden.BascovDiscordBot.product.api;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.home.HomeSnapshot;
import ru.flawden.BascovDiscordBot.product.ProductCapabilities;
import ru.flawden.BascovDiscordBot.product.ProductLibrarySnapshot;
import ru.flawden.BascovDiscordBot.product.ProductMixesSnapshot;
import ru.flawden.BascovDiscordBot.product.ProductMixDetailSnapshot;
import ru.flawden.BascovDiscordBot.product.ProductPlaybackSnapshot;
import ru.flawden.BascovDiscordBot.product.ProductSearchSnapshot;
import ru.flawden.BascovDiscordBot.library.StoredPlaylist;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.util.List;

/** Explicit mapping keeps the HTTP v1 contract independent from internal product records. */
@Component
public class ProductApiMapper {

    public ProductApiResponse.Capabilities capabilities(ProductCapabilities source) {
        return new ProductApiResponse.Capabilities(
                source.apiVersion(),
                source.mode(),
                source.authenticationRequiredForReads(),
                source.mutationsEnabled(),
                source.authenticationRequiredForMutations(),
                source.resources());
    }

    public ProductApiResponse.Guilds guilds(String productUserId, List<ProductGuildAccessPort.GuildSummary> source) {
        return new ProductApiResponse.Guilds(
                productUserId,
                source.stream()
                        .map(guild -> new ProductApiResponse.Guild(snowflake(guild.guildId()), guild.name()))
                        .toList());
    }

    public ProductApiResponse.Home home(HomeSnapshot source, String productUserId) {
        return new ProductApiResponse.Home(
                snowflake(source.guildId()),
                productUserId,
                source.date(),
                source.continuation().map(this::continuation).orElse(null),
                mixes(source.today()),
                mixes(source.forYou()),
                themes(source.themes()),
                new ProductApiResponse.Library(
                        snowflake(source.guildId()),
                        productUserId,
                        source.library().favorites(),
                        source.library().personalHistory(),
                        tracks(source.recent()),
                        List.of(),
                        List.of()),
                tracks(source.recent()),
                new ProductApiResponse.Taste(
                        source.taste().evidenceSignals(),
                        source.taste().confidence(),
                        source.taste().recommendations()));
    }

    public ProductApiResponse.Mixes mixes(ProductMixesSnapshot source, String productUserId) {
        return new ProductApiResponse.Mixes(
                snowflake(source.guildId()),
                productUserId,
                source.date(),
                source.continuation().map(this::continuation).orElse(null),
                mixes(source.today()),
                mixes(source.forYou()),
                themes(source.themes()));
    }

    public ProductApiResponse.Library library(ProductLibrarySnapshot source, String productUserId) {
        return new ProductApiResponse.Library(
                snowflake(source.guildId()),
                productUserId,
                source.favorites(),
                source.personalHistory(),
                tracks(source.recent()),
                tracks(source.favoriteTracks()),
                tracks(source.historyTracks()));
    }

    public ProductApiResponse.MixDetail mix(ProductMixDetailSnapshot source, String productUserId) {
        return new ProductApiResponse.MixDetail(
                snowflake(source.guildId()),
                productUserId,
                source.stationSlug(),
                source.label(),
                source.description(),
                source.available(),
                source.daily(),
                tracks(source.seedPreview()));
    }

    public ProductApiResponse.Search search(ProductSearchSnapshot source, String productUserId) {
        return new ProductApiResponse.Search(
                snowflake(source.guildId()),
                productUserId,
                source.query(),
                tracks(source.tracks()));
    }

    public ProductApiResponse.Favorites favorites(
            long guildId,
            String productUserId,
            List<StoredTrack> source) {
        return new ProductApiResponse.Favorites(
                snowflake(guildId),
                productUserId,
                ru.flawden.BascovDiscordBot.library.MusicLibraryRepository.MAX_FAVORITES_PER_USER,
                source.stream().map(ProductApiMapper::track).toList());
    }

    public ProductApiResponse.Playlists playlists(
            long guildId,
            String productUserId,
            long actorUserId,
            List<StoredPlaylist> source) {
        return new ProductApiResponse.Playlists(
                snowflake(guildId),
                productUserId,
                source.stream().map(playlist -> playlistSummary(playlist, actorUserId)).toList());
    }

    public ProductApiResponse.PlaylistDetail playlist(
            long guildId,
            String productUserId,
            long actorUserId,
            StoredPlaylist source) {
        return new ProductApiResponse.PlaylistDetail(
                snowflake(guildId),
                productUserId,
                source.name(),
                snowflake(source.ownerUserId()),
                source.ownerUserId() == actorUserId,
                source.createdAtEpochMillis(),
                source.tracks().stream().map(ProductApiMapper::track).toList());
    }

    private static ProductApiResponse.PlaylistSummary playlistSummary(
            StoredPlaylist source,
            long actorUserId) {
        return new ProductApiResponse.PlaylistSummary(
                source.name(),
                snowflake(source.ownerUserId()),
                source.ownerUserId() == actorUserId,
                source.tracks().size(),
                source.createdAtEpochMillis());
    }

    private static ProductApiResponse.Track track(StoredTrack source) {
        var identity = source.trackIdentity();
        return new ProductApiResponse.Track(identity.stableKey(), source.title(), source.author());
    }

    public ProductApiResponse.Player player(ProductPlaybackSnapshot source) {
        return new ProductApiResponse.Player(
                snowflake(source.guildId()),
                source.sessionActive(),
                source.playing(),
                source.paused(),
                source.volume(),
                source.repeatMode(),
                source.queueSize(),
                source.positionMillis(),
                source.durationMillis(),
                source.current().map(track -> new ProductApiResponse.Track(
                        track.stableKey(), track.title(), track.artist())).orElse(null),
                new ProductApiResponse.Radio(
                        source.radio().enabled(),
                        source.radio().stationSlug(),
                        source.radio().theme(),
                        source.radio().strategy(),
                        source.radio().generatedTracks()));
    }

    private static String snowflake(long value) {
        return Long.toUnsignedString(value);
    }

    private ProductApiResponse.Continuation continuation(HomeSnapshot.ContinuationCard source) {
        return new ProductApiResponse.Continuation(
                source.kind().name(),
                source.label(),
                source.stationSlug(),
                source.theme(),
                source.releaseDate(),
                source.generatedTracks());
    }

    private static List<ProductApiResponse.Mix> mixes(List<HomeSnapshot.MixCard> source) {
        return source.stream()
                .map(card -> new ProductApiResponse.Mix(
                        card.stationSlug(), card.label(), card.description(), card.available(), card.daily()))
                .toList();
    }

    private static List<ProductApiResponse.Theme> themes(List<HomeSnapshot.ThemeCard> source) {
        return source.stream()
                .map(theme -> new ProductApiResponse.Theme(theme.name(), theme.affinity()))
                .toList();
    }

    private static List<ProductApiResponse.Track> tracks(List<HomeSnapshot.TrackPreview> source) {
        return source.stream()
                .map(track -> new ProductApiResponse.Track(track.stableKey(), track.title(), track.artist()))
                .toList();
    }
}
