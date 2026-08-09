package ru.flawden.BascovDiscordBot.library;

/**
 * Совпадение поиска по личному избранному с устойчивой 1-based позицией.
 */
public record FavoriteSearchHit(int position, StoredTrack track) {
    public FavoriteSearchHit {
        if (position < 1) {
            throw new IllegalArgumentException("position must be positive");
        }
        if (track == null) {
            throw new IllegalArgumentException("track cannot be null");
        }
    }
}
