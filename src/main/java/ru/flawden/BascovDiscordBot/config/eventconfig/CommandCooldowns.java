package ru.flawden.BascovDiscordBot.config.eventconfig;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Потокобезопасное хранилище cooldown-команд в памяти процесса.
 */
public final class CommandCooldowns {

    private final Clock clock;
    private final Map<CooldownKey, Instant> expiresAt = new HashMap<>();

    public CommandCooldowns(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized Acquisition tryAcquire(
            long guildId,
            long userId,
            String commandName,
            Duration cooldown
    ) {
        Objects.requireNonNull(commandName, "commandName");
        Objects.requireNonNull(cooldown, "cooldown");

        if (cooldown.isZero() || cooldown.isNegative()) {
            return Acquisition.granted();
        }

        Instant now = clock.instant();
        CooldownKey key = new CooldownKey(guildId, userId, commandName.toLowerCase(Locale.ROOT));
        Instant existingExpiration = expiresAt.get(key);

        if (existingExpiration != null && existingExpiration.isAfter(now)) {
            return Acquisition.blocked(Duration.between(now, existingExpiration));
        }

        expiresAt.put(key, now.plus(cooldown));
        return Acquisition.granted();
    }

    public record Acquisition(boolean allowed, Duration remaining) {
        public static Acquisition granted() {
            return new Acquisition(true, Duration.ZERO);
        }

        public static Acquisition blocked(Duration remaining) {
            return new Acquisition(false, remaining.isNegative() ? Duration.ZERO : remaining);
        }
    }

    private record CooldownKey(long guildId, long userId, String commandName) {
    }
}
