package ru.flawden.BascovDiscordBot.settings;

import java.time.Instant;
import java.util.Objects;

/**
 * Одна сохраняемая запись аудита изменения guild settings.
 */
public record GuildSettingsAuditEntry(Instant occurredAt, long actorUserId, String action) {

    public GuildSettingsAuditEntry {
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (actorUserId <= 0) {
            throw new IllegalArgumentException("actorUserId must be positive");
        }
        Objects.requireNonNull(action, "action");
        action = action.trim();
        if (action.isEmpty() || action.length() > 160 || action.indexOf('\n') >= 0 || action.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("action must contain 1..160 single-line characters");
        }
    }
}
