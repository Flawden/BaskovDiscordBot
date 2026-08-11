package ru.flawden.BascovDiscordBot.product;

import ru.flawden.BascovDiscordBot.library.StoredTrack;
import ru.flawden.BascovDiscordBot.recommendation.PersonalizedStation;

import java.util.List;

/** Runtime-only read port needed by client-neutral product use cases. */
@FunctionalInterface
public interface MusicProductReadPort {

    ProductPlaybackSnapshot playback(long guildId);

    default List<StoredTrack> favorites(long guildId, long userId) {
        return List.of();
    }

    default List<StoredTrack> personalHistory(long guildId, long userId) {
        return List.of();
    }

    default List<StoredTrack> stationSeeds(long guildId, long userId, PersonalizedStation station) {
        return List.of();
    }
}
