package ru.flawden.BascovDiscordBot.library;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Детерминированная локальная аналитика поверх personal history/favorites.
 * Никаких сетевых рекомендаций и внешнего профилирования здесь нет.
 */
public final class PersonalListeningInsights {

    private PersonalListeningInsights() {
    }

    public static List<PersonalTrackStat> topTracks(List<StoredTrack> history, int limit) {
        if (limit <= 0 || history == null || history.isEmpty()) {
            return List.of();
        }
        Map<String, MutableTrackStat> stats = new LinkedHashMap<>();
        for (int index = 0; index < history.size(); index++) {
            StoredTrack track = history.get(index);
            if (track == null) {
                continue;
            }
            String identity = identity(track);
            MutableTrackStat stat = stats.get(identity);
            if (stat == null) {
                stats.put(identity, new MutableTrackStat(track, 1, index));
            } else {
                stat.plays++;
            }
        }
        return stats.values().stream()
                .sorted(Comparator.comparingInt((MutableTrackStat stat) -> stat.plays).reversed()
                        .thenComparingInt(stat -> stat.firstPosition))
                .limit(limit)
                .map(stat -> new PersonalTrackStat(stat.track, stat.plays))
                .toList();
    }

    public static List<PersonalArtistStat> topArtists(List<StoredTrack> history, int limit) {
        if (limit <= 0 || history == null || history.isEmpty()) {
            return List.of();
        }
        Map<String, MutableArtistStat> stats = new LinkedHashMap<>();
        for (int index = 0; index < history.size(); index++) {
            StoredTrack track = history.get(index);
            if (track == null) {
                continue;
            }
            String artist = cleanArtist(track.author());
            if (artist == null) {
                continue;
            }
            String key = artist.toLowerCase(Locale.ROOT);
            MutableArtistStat stat = stats.get(key);
            if (stat == null) {
                stats.put(key, new MutableArtistStat(artist, 1, index));
            } else {
                stat.plays++;
            }
        }
        return stats.values().stream()
                .sorted(Comparator.comparingInt((MutableArtistStat stat) -> stat.plays).reversed()
                        .thenComparingInt(stat -> stat.firstPosition))
                .limit(limit)
                .map(stat -> new PersonalArtistStat(stat.artist, stat.plays))
                .toList();
    }

    /**
     * Выбирает seed локально: явное favorite-сохранение весит сильнее обычного
     * history-hit, повторы history усиливают тот же трек, а newest-first порядок
     * служит tie-breaker.
     */
    public static Optional<StoredTrack> discoverySeed(
            List<StoredTrack> favorites,
            List<StoredTrack> history) {
        Map<String, SeedScore> scores = new LinkedHashMap<>();
        int order = 0;
        if (favorites != null) {
            for (StoredTrack track : favorites) {
                if (track == null) {
                    continue;
                }
                String identity = identity(track);
                SeedScore score = scores.get(identity);
                if (score == null) {
                    score = new SeedScore(track, order);
                    scores.put(identity, score);
                }
                score.score += 4;
                order++;
            }
        }
        if (history != null) {
            for (StoredTrack track : history) {
                if (track == null) {
                    continue;
                }
                SeedScore score = scores.get(identity(track));
                if (score == null) {
                    score = new SeedScore(track, order);
                    scores.put(identity(track), score);
                }
                score.score += 1;
                order++;
            }
        }
        return scores.values().stream()
                .sorted(Comparator.comparingInt((SeedScore score) -> score.score).reversed()
                        .thenComparingInt(score -> score.firstOrder))
                .map(score -> score.track)
                .findFirst();
    }

    public static int uniqueTrackCount(List<StoredTrack> history) {
        if (history == null || history.isEmpty()) {
            return 0;
        }
        return (int) history.stream().filter(track -> track != null).map(PersonalListeningInsights::identity).distinct().count();
    }

    private static String identity(StoredTrack track) {
        return track.provider().name() + "|" + track.playbackIdentifier().trim().toLowerCase(Locale.ROOT);
    }

    private static String cleanArtist(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String artist = value.trim();
        if ("unknown artist".equalsIgnoreCase(artist) || "неизвестно".equalsIgnoreCase(artist)) {
            return null;
        }
        return artist;
    }

    private static final class MutableTrackStat {
        private final StoredTrack track;
        private int plays;
        private final int firstPosition;

        private MutableTrackStat(StoredTrack track, int plays, int firstPosition) {
            this.track = track;
            this.plays = plays;
            this.firstPosition = firstPosition;
        }
    }

    private static final class MutableArtistStat {
        private final String artist;
        private int plays;
        private final int firstPosition;

        private MutableArtistStat(String artist, int plays, int firstPosition) {
            this.artist = artist;
            this.plays = plays;
            this.firstPosition = firstPosition;
        }
    }

    private static final class SeedScore {
        private final StoredTrack track;
        private final int firstOrder;
        private int score;

        private SeedScore(StoredTrack track, int firstOrder) {
            this.track = track;
            this.firstOrder = firstOrder;
        }
    }
}
