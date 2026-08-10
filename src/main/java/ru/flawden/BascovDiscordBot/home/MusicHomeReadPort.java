package ru.flawden.BascovDiscordBot.home;

import ru.flawden.BascovDiscordBot.library.StoredTrack;
import ru.flawden.BascovDiscordBot.recommendation.PersonalTasteProfile;
import ru.flawden.BascovDiscordBot.recommendation.PersonalizedStation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Read-only boundary between the product-home model and current runtime/storage implementations.
 */
public interface MusicHomeReadPort {

    List<StoredTrack> favorites(long guildId, long userId);

    List<StoredTrack> personalHistory(long guildId, long userId);

    PersonalTasteProfile tasteProfile(long guildId, long userId);

    boolean hasStationSeeds(long guildId, long userId, PersonalizedStation station);

    Optional<StationState> activeStation(long guildId, long userId);

    Optional<StationState> resumableStation(long guildId, long userId);

    record StationState(
            PersonalizedStation station,
            String theme,
            LocalDate releaseDate,
            long generatedTracks) {
        public StationState {
            station = station == null ? PersonalizedStation.CUSTOM : station;
            theme = theme == null ? "" : theme.trim();
            generatedTracks = Math.max(0L, generatedTracks);
        }
    }
}
