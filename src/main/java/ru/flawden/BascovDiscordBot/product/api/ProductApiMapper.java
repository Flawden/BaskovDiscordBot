package ru.flawden.BascovDiscordBot.product.api;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.home.HomeSnapshot;
import ru.flawden.BascovDiscordBot.product.ProductCapabilities;
import ru.flawden.BascovDiscordBot.product.ProductLibrarySnapshot;
import ru.flawden.BascovDiscordBot.product.ProductMixesSnapshot;
import ru.flawden.BascovDiscordBot.product.ProductPlaybackSnapshot;

import java.util.List;

/** Explicit mapping keeps the HTTP v1 contract independent from internal product records. */
@Component
public class ProductApiMapper {

    public ProductApiResponse.Capabilities capabilities(ProductCapabilities source) {
        return new ProductApiResponse.Capabilities(
                source.apiVersion(),
                source.mode(),
                source.mutationsEnabled(),
                source.authenticationRequiredForMutations(),
                source.resources());
    }

    public ProductApiResponse.Home home(HomeSnapshot source) {
        return new ProductApiResponse.Home(
                source.guildId(),
                source.userId(),
                source.date(),
                source.continuation().map(this::continuation).orElse(null),
                mixes(source.today()),
                mixes(source.forYou()),
                themes(source.themes()),
                new ProductApiResponse.Library(
                        source.guildId(),
                        source.userId(),
                        source.library().favorites(),
                        source.library().personalHistory(),
                        tracks(source.recent())),
                tracks(source.recent()),
                new ProductApiResponse.Taste(
                        source.taste().evidenceSignals(),
                        source.taste().confidence(),
                        source.taste().recommendations()));
    }

    public ProductApiResponse.Mixes mixes(ProductMixesSnapshot source) {
        return new ProductApiResponse.Mixes(
                source.guildId(),
                source.userId(),
                source.date(),
                source.continuation().map(this::continuation).orElse(null),
                mixes(source.today()),
                mixes(source.forYou()),
                themes(source.themes()));
    }

    public ProductApiResponse.Library library(ProductLibrarySnapshot source) {
        return new ProductApiResponse.Library(
                source.guildId(),
                source.userId(),
                source.favorites(),
                source.personalHistory(),
                tracks(source.recent()));
    }

    public ProductApiResponse.Player player(ProductPlaybackSnapshot source) {
        return new ProductApiResponse.Player(
                source.guildId(),
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
