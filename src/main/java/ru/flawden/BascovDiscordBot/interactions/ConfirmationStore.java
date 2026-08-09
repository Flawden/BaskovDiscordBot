package ru.flawden.BascovDiscordBot.interactions;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Короткоживущие одноразовые подтверждения опасных Discord-действий.
 */
@Component
public class ConfirmationStore {

    static final Duration DEFAULT_TTL = Duration.ofMinutes(2);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, PendingConfirmation> pending = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public ConfirmationStore() {
        this(Clock.systemUTC(), DEFAULT_TTL);
    }

    ConfirmationStore(Clock clock, Duration ttl) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Confirmation TTL must be positive");
        }
    }

    public PendingConfirmation create(
            Action action,
            long guildId,
            long userId,
            String payload) {
        Objects.requireNonNull(action, "action");
        purgeExpired();
        String token = newToken();
        PendingConfirmation confirmation = new PendingConfirmation(
                token,
                action,
                guildId,
                userId,
                payload == null ? "" : payload,
                clock.instant().plus(ttl));
        pending.put(token, confirmation);
        return confirmation;
    }

    public ClaimResult claim(String token, long guildId, long userId) {
        PendingConfirmation confirmation = pending.get(token);
        if (confirmation == null) {
            return new ClaimResult(ClaimStatus.MISSING, null);
        }
        if (!confirmation.expiresAt().isAfter(clock.instant())) {
            pending.remove(token, confirmation);
            return new ClaimResult(ClaimStatus.EXPIRED, confirmation);
        }
        if (confirmation.guildId() != guildId || confirmation.userId() != userId) {
            return new ClaimResult(ClaimStatus.FORBIDDEN, confirmation);
        }
        if (!pending.remove(token, confirmation)) {
            return new ClaimResult(ClaimStatus.MISSING, null);
        }
        return new ClaimResult(ClaimStatus.CLAIMED, confirmation);
    }

    public ClaimStatus cancel(String token, long guildId, long userId) {
        PendingConfirmation confirmation = pending.get(token);
        if (confirmation == null) {
            return ClaimStatus.MISSING;
        }
        if (!confirmation.expiresAt().isAfter(clock.instant())) {
            pending.remove(token, confirmation);
            return ClaimStatus.EXPIRED;
        }
        if (confirmation.guildId() != guildId || confirmation.userId() != userId) {
            return ClaimStatus.FORBIDDEN;
        }
        return pending.remove(token, confirmation) ? ClaimStatus.CANCELLED : ClaimStatus.MISSING;
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        pending.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private static String newToken() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public enum Action {
        STOP,
        CLEAR_QUEUE,
        DELETE_PLAYLIST,
        RESET_SETTINGS
    }

    public enum ClaimStatus {
        CLAIMED,
        CANCELLED,
        FORBIDDEN,
        EXPIRED,
        MISSING
    }

    public record PendingConfirmation(
            String token,
            Action action,
            long guildId,
            long userId,
            String payload,
            Instant expiresAt) {
    }

    public record ClaimResult(ClaimStatus status, PendingConfirmation confirmation) {
        public boolean claimed() {
            return status == ClaimStatus.CLAIMED && confirmation != null;
        }
    }
}
