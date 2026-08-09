package ru.flawden.BascovDiscordBot.interactions;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасные одноразовые голосования за пропуск текущего трека.
 * Голоса привязаны к конкретному объекту воспроизведения и не переносятся
 * на следующий трек, повтор или fallback.
 */
@Component
public class VoteSkipService {

    static final Duration SESSION_TTL = Duration.ofHours(6);

    private final Map<Long, VoteSession> sessions = new ConcurrentHashMap<>();

    public VoteResult vote(
            long guildId,
            String playbackKey,
            long voterUserId,
            int eligibleListeners,
            int thresholdPercent) {
        if (guildId <= 0 || voterUserId <= 0) {
            throw new IllegalArgumentException("guildId and voterUserId must be positive");
        }
        String normalizedKey = Objects.requireNonNull(playbackKey, "playbackKey").trim();
        if (normalizedKey.isEmpty()) {
            throw new IllegalArgumentException("playbackKey cannot be blank");
        }
        if (eligibleListeners < 1) {
            throw new IllegalArgumentException("eligibleListeners must be positive");
        }
        if (thresholdPercent < 1 || thresholdPercent > 100) {
            throw new IllegalArgumentException("thresholdPercent must be between 1 and 100");
        }

        cleanupExpired();
        int required = requiredVotes(eligibleListeners, thresholdPercent);
        Holder holder = new Holder();
        sessions.compute(guildId, (ignored, current) -> {
            VoteSession session = current;
            if (session == null || !session.playbackKey.equals(normalizedKey)) {
                session = new VoteSession(normalizedKey, System.nanoTime());
            }

            boolean added = session.voterUserIds.add(voterUserId);
            int votes = session.voterUserIds.size();
            holder.result = new VoteResult(
                    votes >= required ? VoteStatus.PASSED : added ? VoteStatus.ACCEPTED : VoteStatus.DUPLICATE,
                    votes,
                    required,
                    eligibleListeners);
            return votes >= required ? null : session;
        });
        return holder.result;
    }

    public VoteSnapshot snapshot(
            long guildId,
            String playbackKey,
            long viewerUserId,
            int eligibleListeners,
            int thresholdPercent) {
        if (guildId <= 0 || eligibleListeners < 1) {
            throw new IllegalArgumentException("guildId and eligibleListeners must be positive");
        }
        if (thresholdPercent < 1 || thresholdPercent > 100) {
            throw new IllegalArgumentException("thresholdPercent must be between 1 and 100");
        }
        String normalizedKey = Objects.requireNonNull(playbackKey, "playbackKey").trim();
        if (normalizedKey.isEmpty()) {
            throw new IllegalArgumentException("playbackKey cannot be blank");
        }
        cleanupExpired();
        VoteSession session = sessions.get(guildId);
        int votes = session != null && session.playbackKey.equals(normalizedKey)
                ? session.voterUserIds.size()
                : 0;
        boolean viewerVoted = viewerUserId > 0L
                && session != null
                && session.playbackKey.equals(normalizedKey)
                && session.voterUserIds.contains(viewerUserId);
        return new VoteSnapshot(
                votes,
                requiredVotes(eligibleListeners, thresholdPercent),
                eligibleListeners,
                thresholdPercent,
                viewerVoted);
    }

    public void reset(long guildId) {
        if (guildId > 0) {
            sessions.remove(guildId);
        }
    }

    public boolean hasActiveSession(long guildId) {
        cleanupExpired();
        return guildId > 0 && sessions.containsKey(guildId);
    }

    public int activeSessions() {
        cleanupExpired();
        return sessions.size();
    }

    static int requiredVotes(int eligibleListeners, int thresholdPercent) {
        if (eligibleListeners < 1) {
            throw new IllegalArgumentException("eligibleListeners must be positive");
        }
        if (thresholdPercent < 1 || thresholdPercent > 100) {
            throw new IllegalArgumentException("thresholdPercent must be between 1 and 100");
        }
        return Math.toIntExact(Math.max(
                1L,
                (eligibleListeners * (long) thresholdPercent + 99L) / 100L));
    }

    private void cleanupExpired() {
        long maxAgeNanos = SESSION_TTL.toNanos();
        long now = System.nanoTime();
        sessions.entrySet().removeIf(entry -> now - entry.getValue().createdAtNanos > maxAgeNanos);
    }

    public enum VoteStatus {
        ACCEPTED,
        DUPLICATE,
        PASSED
    }

    public record VoteResult(
            VoteStatus status,
            int votes,
            int requiredVotes,
            int eligibleListeners) {
    }

    public record VoteSnapshot(
            int votes,
            int requiredVotes,
            int eligibleListeners,
            int thresholdPercent,
            boolean viewerVoted) {
    }

    private static final class VoteSession {
        private final String playbackKey;
        private final long createdAtNanos;
        private final Set<Long> voterUserIds = new HashSet<>();

        private VoteSession(String playbackKey, long createdAtNanos) {
            this.playbackKey = playbackKey;
            this.createdAtNanos = createdAtNanos;
        }
    }

    private static final class Holder {
        private VoteResult result;
    }
}
