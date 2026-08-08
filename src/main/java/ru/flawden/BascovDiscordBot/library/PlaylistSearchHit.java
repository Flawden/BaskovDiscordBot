package ru.flawden.BascovDiscordBot.library;

import java.util.List;

/**
 * Результат поиска по библиотеке: совпавший плейлист и позиции его треков.
 */
public record PlaylistSearchHit(StoredPlaylist playlist, List<Integer> matchingPositions) {

    public PlaylistSearchHit {
        if (playlist == null) {
            throw new IllegalArgumentException("playlist is required");
        }
        matchingPositions = matchingPositions == null ? List.of() : List.copyOf(matchingPositions);
    }
}
