package ru.flawden.BascovDiscordBot.interactions;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Короткоживущие результаты /search. Сессии привязаны к серверу и пользователю,
 * а выбор можно использовать только один раз.
 */
@Component
public class SearchSelectionStore {

    static final Duration SESSION_TTL = Duration.ofMinutes(5);
    static final int MAX_ACTIVE_SESSIONS = 200;
    static final int MAX_CANDIDATES = 5;

    private final Map<String, SearchSession> sessions = new ConcurrentHashMap<>();

    public SearchSession create(long guildId, long userId, String query, List<AudioTrack> candidates) {
        List<AudioTrack> safeCandidates = candidates == null
                ? List.of()
                : candidates.stream().limit(MAX_CANDIDATES).toList();
        if (safeCandidates.isEmpty()) {
            throw new IllegalArgumentException("Search session requires at least one candidate");
        }

        cleanupExpired();
        trimToCapacity();

        Instant now = Instant.now();
        SearchSession session = new SearchSession(
                newToken(),
                guildId,
                userId,
                query == null ? "" : query,
                safeCandidates,
                now,
                now.plus(SESSION_TTL));
        sessions.put(session.token(), session);
        return session;
    }

    public ClaimResult claim(String token, int oneBasedIndex, long guildId, long userId) {
        SearchSession session = sessions.get(token);
        if (session == null) {
            return ClaimResult.status(ClaimStatus.NOT_FOUND);
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token, session);
            return ClaimResult.status(ClaimStatus.EXPIRED);
        }
        if (session.guildId() != guildId || session.userId() != userId) {
            return ClaimResult.status(ClaimStatus.FORBIDDEN);
        }
        int index = oneBasedIndex - 1;
        if (index < 0 || index >= session.candidates().size()) {
            return ClaimResult.status(ClaimStatus.INVALID_INDEX);
        }
        if (!sessions.remove(token, session)) {
            return ClaimResult.status(ClaimStatus.NOT_FOUND);
        }
        return new ClaimResult(
                ClaimStatus.CLAIMED,
                session,
                session.candidates().get(index).makeClone(),
                oneBasedIndex);
    }

    public ClaimStatus cancel(String token, long guildId, long userId) {
        SearchSession session = sessions.get(token);
        if (session == null) {
            return ClaimStatus.NOT_FOUND;
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token, session);
            return ClaimStatus.EXPIRED;
        }
        if (session.guildId() != guildId || session.userId() != userId) {
            return ClaimStatus.FORBIDDEN;
        }
        return sessions.remove(token, session) ? ClaimStatus.CANCELLED : ClaimStatus.NOT_FOUND;
    }

    int activeSessionCount() {
        cleanupExpired();
        return sessions.size();
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private void trimToCapacity() {
        while (sessions.size() >= MAX_ACTIVE_SESSIONS) {
            sessions.values().stream()
                    .min(Comparator.comparing(SearchSession::createdAt))
                    .ifPresent(oldest -> sessions.remove(oldest.token(), oldest));
        }
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public record SearchSession(
            String token,
            long guildId,
            long userId,
            String query,
            List<AudioTrack> candidates,
            Instant createdAt,
            Instant expiresAt) {

        public SearchSession {
            candidates = List.copyOf(candidates);
        }
    }

    public enum ClaimStatus {
        CLAIMED,
        CANCELLED,
        NOT_FOUND,
        EXPIRED,
        FORBIDDEN,
        INVALID_INDEX
    }

    public record ClaimResult(
            ClaimStatus status,
            SearchSession session,
            AudioTrack track,
            int oneBasedIndex) {

        static ClaimResult status(ClaimStatus status) {
            return new ClaimResult(status, null, null, 0);
        }

        public boolean claimed() {
            return status == ClaimStatus.CLAIMED && track != null;
        }
    }
}
