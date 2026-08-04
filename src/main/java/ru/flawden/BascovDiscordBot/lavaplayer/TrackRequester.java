package ru.flawden.BascovDiscordBot.lavaplayer;

/**
 * Пользователь, добавивший трек в музыкальную сессию.
 */
public record TrackRequester(long userId, String displayName) {

    private static final TrackRequester UNKNOWN = new TrackRequester(0L, "Неизвестно");

    public TrackRequester {
        displayName = displayName == null || displayName.isBlank() ? "Неизвестно" : displayName.trim();
    }

    public static TrackRequester unknown() {
        return UNKNOWN;
    }

    public String discordLabel() {
        return userId > 0 ? "<@" + userId + ">" : displayName;
    }
}
