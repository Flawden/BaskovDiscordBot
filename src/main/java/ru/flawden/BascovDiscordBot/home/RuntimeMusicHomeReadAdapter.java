package ru.flawden.BascovDiscordBot.home;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;
import ru.flawden.BascovDiscordBot.lavaplayer.RadioSnapshot;
import ru.flawden.BascovDiscordBot.library.MusicLibraryRepository;
import ru.flawden.BascovDiscordBot.library.StoredTrack;
import ru.flawden.BascovDiscordBot.recommendation.PersonalTasteProfile;
import ru.flawden.BascovDiscordBot.recommendation.PersonalizedStation;
import ru.flawden.BascovDiscordBot.recommendation.RecommendationFeedbackService;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bridges the client-neutral home read port to the current runtime and persistent repositories. */
@Component
public class RuntimeMusicHomeReadAdapter implements MusicHomeReadPort {

    private final PlayerManager playerManager;
    private final MusicLibraryRepository library;
    private final RecommendationFeedbackService feedback;

    public RuntimeMusicHomeReadAdapter(
            PlayerManager playerManager,
            MusicLibraryRepository library,
            RecommendationFeedbackService feedback) {
        this.playerManager = Objects.requireNonNull(playerManager, "playerManager");
        this.library = Objects.requireNonNull(library, "library");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
    }

    @Override
    public List<StoredTrack> favorites(long guildId, long userId) {
        return library.favorites(guildId, userId);
    }

    @Override
    public List<StoredTrack> personalHistory(long guildId, long userId) {
        return library.personalHistory(guildId, userId);
    }

    @Override
    public PersonalTasteProfile tasteProfile(long guildId, long userId) {
        return feedback.tasteProfile(guildId, userId);
    }

    @Override
    public boolean hasStationSeeds(long guildId, long userId, PersonalizedStation station) {
        return playerManager.hasStationSeeds(guildId, station, userId);
    }

    @Override
    public Optional<StationState> activeStation(long guildId, long userId) {
        RadioSnapshot radio = playerManager.radioSnapshot(guildId);
        PersonalizedStation station = playerManager.activeStation(guildId);
        if (!radio.enabled() || radio.ownerUserId() != userId || !station.curated()) {
            return Optional.empty();
        }
        return Optional.of(new StationState(
                station,
                playerManager.activeStationTheme(guildId).orElse(""),
                playerManager.activeStationSeedDate(guildId).orElse(null),
                radio.generatedTracks()));
    }

    @Override
    public Optional<StationState> resumableStation(long guildId, long userId) {
        return playerManager.stationContinuation(guildId, userId)
                .map(saved -> new StationState(
                        saved.station(),
                        saved.themeFocus(),
                        saved.seedDate(),
                        saved.generatedTracks()));
    }
}
