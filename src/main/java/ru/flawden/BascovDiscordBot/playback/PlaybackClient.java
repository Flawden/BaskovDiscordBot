package ru.flawden.BascovDiscordBot.playback;

/**
 * Logical client asking Baskov Music to resolve a track into a playable transport source.
 */
public enum PlaybackClient {
    DISCORD,
    ANDROID,
    WEB,
    UNKNOWN
}
