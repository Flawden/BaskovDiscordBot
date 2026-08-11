package ru.flawden.BascovDiscordBot.product;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.lavaplayer.GuildMusicManager;
import ru.flawden.BascovDiscordBot.library.MusicLibraryRepository;
import ru.flawden.BascovDiscordBot.library.StoredTrack;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;
import ru.flawden.BascovDiscordBot.lavaplayer.RadioSnapshot;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequest;
import ru.flawden.BascovDiscordBot.recommendation.PersonalizedStation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bridges product read use cases to the current LavaPlayer runtime. */
@Component
public class RuntimeMusicProductReadAdapter implements MusicProductReadPort {

    private final PlayerManager playerManager;
    private final MusicLibraryRepository libraryRepository;

    public RuntimeMusicProductReadAdapter(PlayerManager playerManager, MusicLibraryRepository libraryRepository) {
        this.playerManager = Objects.requireNonNull(playerManager, "playerManager");
        this.libraryRepository = Objects.requireNonNull(libraryRepository, "libraryRepository");
    }

    @Override
    public List<StoredTrack> favorites(long guildId, long userId) {
        return libraryRepository.favorites(guildId, userId);
    }

    @Override
    public List<StoredTrack> personalHistory(long guildId, long userId) {
        return libraryRepository.personalHistory(guildId, userId);
    }

    @Override
    public List<StoredTrack> stationSeeds(long guildId, long userId, PersonalizedStation station) {
        return playerManager.stationSeedPreview(guildId, station, userId);
    }

    @Override
    public ProductPlaybackSnapshot playback(long guildId) {
        if (guildId <= 0L) {
            throw new IllegalArgumentException("guildId must be positive");
        }

        Optional<GuildMusicManager> optionalManager = playerManager.findMusicManager(guildId);
        RadioSnapshot radio = playerManager.radioSnapshot(guildId);
        PersonalizedStation station = playerManager.activeStation(guildId);
        String theme = playerManager.activeStationTheme(guildId).orElse("");

        if (optionalManager.isEmpty()) {
            return new ProductPlaybackSnapshot(
                    guildId,
                    false,
                    false,
                    false,
                    0,
                    "OFF",
                    0,
                    0L,
                    0L,
                    Optional.empty(),
                    productRadio(radio, station, theme));
        }

        GuildMusicManager manager = optionalManager.orElseThrow();
        TrackRequest currentRequest = manager.getScheduler().getCurrentRequest();
        Optional<ProductPlaybackSnapshot.Track> current = currentRequest == null
                ? Optional.empty()
                : Optional.of(productTrack(currentRequest));
        long position = currentRequest == null ? 0L : Math.max(0L, currentRequest.track().getPosition());
        long duration = currentRequest == null ? 0L : Math.max(0L, currentRequest.track().getDuration());

        return new ProductPlaybackSnapshot(
                guildId,
                true,
                currentRequest != null,
                manager.getAudioPlayer().isPaused(),
                manager.getAudioPlayer().getVolume(),
                manager.getScheduler().getRepeatMode().name(),
                manager.getScheduler().queueSize(),
                position,
                duration,
                current,
                productRadio(radio, station, theme));
    }

    private static ProductPlaybackSnapshot.Track productTrack(TrackRequest request) {
        String title = request.track().getInfo().title;
        String artist = request.track().getInfo().author;
        TrackIdentity identity = TrackIdentity.of(artist, title);
        return new ProductPlaybackSnapshot.Track(identity.stableKey(), title, artist);
    }

    private static ProductPlaybackSnapshot.Radio productRadio(
            RadioSnapshot radio,
            PersonalizedStation station,
            String theme) {
        return new ProductPlaybackSnapshot.Radio(
                radio.enabled(),
                station == null ? PersonalizedStation.CUSTOM.slug() : station.slug(),
                theme,
                radio.strategy().name().toLowerCase(java.util.Locale.ROOT),
                radio.generatedTracks());
    }
}
