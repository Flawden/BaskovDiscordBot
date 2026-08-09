package ru.flawden.BascovDiscordBot.library;

/**
 * Агрегат по одному replayable треку в личной истории пользователя.
 */
public record PersonalTrackStat(StoredTrack track, int plays) {

    public PersonalTrackStat {
        if (track == null) {
            throw new IllegalArgumentException("track cannot be null");
        }
        if (plays <= 0) {
            throw new IllegalArgumentException("plays must be positive");
        }
    }
}
