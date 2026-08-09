package ru.flawden.BascovDiscordBot.interactions;

import ru.flawden.BascovDiscordBot.library.StoredPlaylist;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Объединяет недавние пользовательские запросы и persistent library в
 * Discord autocomplete. Никаких сетевых запросов autocomplete не делает.
 */
public final class DiscoverySuggestions {

    static final int MAX_SUGGESTIONS = 25;
    private static final int MAX_QUERY_LENGTH = 100;

    private DiscoverySuggestions() {
    }

    public static List<String> suggest(
            String input,
            List<String> recentQueries,
            List<StoredTrack> favorites,
            List<StoredTrack> history,
            List<StoredPlaylist> playlists) {
        return suggest(input, recentQueries, favorites, List.of(), history, playlists);
    }

    public static List<String> suggest(
            String input,
            List<String> recentQueries,
            List<StoredTrack> favorites,
            List<StoredTrack> personalHistory,
            List<StoredTrack> history,
            List<StoredPlaylist> playlists) {
        String needle = normalize(input);
        Map<String, String> ordered = new LinkedHashMap<>();

        addAll(ordered, recentQueries, needle);
        addTracks(ordered, favorites, needle);
        addTracks(ordered, personalHistory, needle);
        addTracks(ordered, history, needle);
        if (playlists != null) {
            for (StoredPlaylist playlist : playlists) {
                if (playlist != null) {
                    addTracks(ordered, playlist.tracks(), needle);
                }
                if (ordered.size() >= MAX_SUGGESTIONS) {
                    break;
                }
            }
        }

        return List.copyOf(new ArrayList<>(ordered.values()));
    }

    public static String discoveryQuery(String author, String title) {
        String safeAuthor = clean(author);
        String safeTitle = clean(title);
        if (safeTitle.isBlank()) {
            throw new IllegalArgumentException("У трека нет названия для нового поиска.");
        }
        String query = safeAuthor.isBlank() || "unknown artist".equalsIgnoreCase(safeAuthor)
                ? safeTitle
                : safeAuthor + " " + safeTitle;
        return query.length() <= MAX_QUERY_LENGTH
                ? query
                : query.substring(0, MAX_QUERY_LENGTH).trim();
    }

    private static void addTracks(
            Map<String, String> ordered,
            List<StoredTrack> tracks,
            String needle) {
        if (tracks == null) {
            return;
        }
        for (StoredTrack track : tracks) {
            if (track == null) {
                continue;
            }
            add(ordered, discoveryQuery(track.author(), track.title()), needle);
            if (ordered.size() >= MAX_SUGGESTIONS) {
                return;
            }
        }
    }

    private static void addAll(
            Map<String, String> ordered,
            List<String> values,
            String needle) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            add(ordered, value, needle);
            if (ordered.size() >= MAX_SUGGESTIONS) {
                return;
            }
        }
    }

    private static void add(Map<String, String> ordered, String value, String needle) {
        String candidate = clean(value);
        if (candidate.isBlank() || candidate.length() > MAX_QUERY_LENGTH) {
            return;
        }
        String normalized = candidate.toLowerCase(Locale.ROOT);
        if (!needle.isBlank() && !normalized.contains(needle)) {
            return;
        }
        ordered.putIfAbsent(normalized, candidate);
    }

    private static String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
