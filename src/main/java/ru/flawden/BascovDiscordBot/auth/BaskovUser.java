package ru.flawden.BascovDiscordBot.auth;

/** Provider-neutral account owned by Baskov Music rather than by a client UI. */
public record BaskovUser(String userId, long createdAtEpochMillis, String displayName) {
    public BaskovUser {
        userId = required(userId, "userId");
        if (createdAtEpochMillis <= 0L) throw new IllegalArgumentException("createdAtEpochMillis must be positive");
        displayName = displayName == null ? "" : displayName.trim();
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " cannot be blank");
        return value.trim();
    }
}
