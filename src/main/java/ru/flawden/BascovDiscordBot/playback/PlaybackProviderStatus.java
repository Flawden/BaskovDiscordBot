package ru.flawden.BascovDiscordBot.playback;

/**
 * Runtime state of one provider inside the transport circuit breaker.
 */
public enum PlaybackProviderStatus {
    HEALTHY,
    DEGRADED,
    COOLDOWN,
    PROBE
}
