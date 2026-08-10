package ru.flawden.BascovDiscordBot.playback;

import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;

import java.util.Objects;

/**
 * Provider-specific transport candidate created only after a logical TrackIdentity is selected.
 */
public record PlaybackSourceReference(
        MediaProvider provider,
        String identifier,
        Kind kind,
        int priority,
        String reason) {

    public enum Kind {
        SEARCH,
        DIRECT
    }

    public PlaybackSourceReference {
        provider = Objects.requireNonNull(provider, "provider");
        identifier = requireText(identifier, "identifier", 2_048);
        kind = Objects.requireNonNull(kind, "kind");
        if (priority < 0) {
            throw new IllegalArgumentException("priority cannot be negative");
        }
        reason = requireText(reason, "reason", 160);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        String safe = value.trim();
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength).trim();
    }
}
