package ru.flawden.BascovDiscordBot.session;

import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Ограниченный безопасный checkpoint одной Discord-гильдии.
 */
public record StoredMusicSession(
        long guildId,
        long voiceChannelId,
        long capturedAtEpochMillis,
        boolean paused,
        int volume,
        RepeatMode repeatMode,
        StoredSessionTrack currentTrack,
        List<StoredSessionTrack> queue) {

    public StoredMusicSession {
        if (guildId <= 0L) {
            throw new IllegalArgumentException("guildId must be positive");
        }
        if (voiceChannelId <= 0L) {
            throw new IllegalArgumentException("voiceChannelId must be positive");
        }
        if (capturedAtEpochMillis <= 0L) {
            throw new IllegalArgumentException("capturedAtEpochMillis must be positive");
        }
        if (volume < 0 || volume > 500) {
            throw new IllegalArgumentException("volume must be between 0 and 500");
        }
        repeatMode = Objects.requireNonNullElse(repeatMode, RepeatMode.OFF);
        queue = queue == null ? List.of() : List.copyOf(queue);
        if (queue.size() > 1_000) {
            throw new IllegalArgumentException("queue cannot contain more than 1000 tracks");
        }
        if (currentTrack == null && queue.isEmpty()) {
            throw new IllegalArgumentException("session must contain a current or queued track");
        }
    }

    public boolean expired(Instant now, Duration maxAge) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(maxAge, "maxAge");
        return Instant.ofEpochMilli(capturedAtEpochMillis).plus(maxAge).isBefore(now);
    }

    public int trackCount() {
        return (currentTrack == null ? 0 : 1) + queue.size();
    }
}
