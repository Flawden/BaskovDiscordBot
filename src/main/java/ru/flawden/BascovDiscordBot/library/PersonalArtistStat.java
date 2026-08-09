package ru.flawden.BascovDiscordBot.library;

/**
 * Количество записей личной истории для исполнителя.
 */
public record PersonalArtistStat(String artist, int plays) {

    public PersonalArtistStat {
        if (artist == null || artist.isBlank()) {
            throw new IllegalArgumentException("artist cannot be blank");
        }
        artist = artist.trim();
        if (plays <= 0) {
            throw new IllegalArgumentException("plays must be positive");
        }
    }
}
