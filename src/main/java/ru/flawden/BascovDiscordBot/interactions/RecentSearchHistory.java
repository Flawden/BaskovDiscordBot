package ru.flawden.BascovDiscordBot.interactions;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Небольшая in-memory история запросов для Discord autocomplete.
 */
@Component
public class RecentSearchHistory {

    private static final int MAX_PER_USER = 20;
    private static final int MAX_SUGGESTIONS = 25;

    private final Map<Long, Deque<String>> queriesByUser = new ConcurrentHashMap<>();

    public void remember(long userId, String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        String normalized = query.trim();
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (lowered.startsWith("http://") || lowered.startsWith("https://")) {
            return;
        }
        Deque<String> queries = queriesByUser.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (queries) {
            queries.removeIf(existing -> existing.equalsIgnoreCase(normalized));
            queries.addFirst(normalized);
            while (queries.size() > MAX_PER_USER) {
                queries.removeLast();
            }
        }
    }

    public List<String> recent(long userId, int limit) {
        if (limit < 1) {
            return List.of();
        }
        Deque<String> queries = queriesByUser.get(userId);
        if (queries == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        synchronized (queries) {
            for (String query : queries) {
                result.add(query);
                if (result.size() == limit) {
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    public Optional<String> last(long userId) {
        Deque<String> queries = queriesByUser.get(userId);
        if (queries == null) {
            return Optional.empty();
        }
        synchronized (queries) {
            return Optional.ofNullable(queries.peekFirst());
        }
    }

    public List<String> suggest(long userId, String input) {
        Deque<String> queries = queriesByUser.get(userId);
        if (queries == null) {
            return List.of();
        }
        String needle = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        synchronized (queries) {
            for (String query : queries) {
                if ((needle.isEmpty() || query.toLowerCase(Locale.ROOT).contains(needle))
                        && query.length() <= 100) {
                    result.add(query);
                    if (result.size() == MAX_SUGGESTIONS) {
                        break;
                    }
                }
            }
        }
        return List.copyOf(result);
    }
}
