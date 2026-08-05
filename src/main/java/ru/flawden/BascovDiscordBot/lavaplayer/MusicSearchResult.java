package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

/**
 * Результат поиска треков без автоматического добавления в очередь.
 */
public record MusicSearchResult(Status status, String query, List<AudioTrack> tracks) {

    public MusicSearchResult {
        query = query == null ? "" : query;
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }

    public enum Status {
        FOUND,
        NO_MATCHES,
        LOAD_FAILED
    }

    public static MusicSearchResult found(String query, List<AudioTrack> tracks) {
        return new MusicSearchResult(Status.FOUND, query, tracks);
    }

    public static MusicSearchResult empty(Status status, String query) {
        return new MusicSearchResult(status, query, List.of());
    }
}
