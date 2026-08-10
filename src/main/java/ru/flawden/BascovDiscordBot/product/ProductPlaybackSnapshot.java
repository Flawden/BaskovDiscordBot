package ru.flawden.BascovDiscordBot.product;

import java.util.Objects;
import java.util.Optional;

/** Client-neutral read model for the current playback state of one guild. */
public record ProductPlaybackSnapshot(
        long guildId,
        boolean sessionActive,
        boolean playing,
        boolean paused,
        int volume,
        String repeatMode,
        int queueSize,
        long positionMillis,
        long durationMillis,
        Optional<Track> current,
        Radio radio) {

    public ProductPlaybackSnapshot {
        if (guildId <= 0L) {
            throw new IllegalArgumentException("guildId must be positive");
        }
        volume = Math.max(0, volume);
        repeatMode = safe(repeatMode, "OFF");
        queueSize = Math.max(0, queueSize);
        positionMillis = Math.max(0L, positionMillis);
        durationMillis = Math.max(0L, durationMillis);
        current = current == null ? Optional.empty() : current;
        radio = radio == null ? Radio.off() : radio;
    }

    public static ProductPlaybackSnapshot idle(long guildId) {
        return new ProductPlaybackSnapshot(
                guildId,
                false,
                false,
                false,
                0,
                "OFF",
                0,
                0L,
                0L,
                Optional.empty(),
                Radio.off());
    }

    public record Track(String stableKey, String title, String artist) {
        public Track {
            stableKey = safe(stableKey, "unknown::unknown");
            title = safe(title, "Неизвестный трек");
            artist = safe(artist, "Неизвестно");
        }
    }

    public record Radio(
            boolean enabled,
            String stationSlug,
            String theme,
            String strategy,
            long generatedTracks) {
        public Radio {
            stationSlug = safe(stationSlug, "custom");
            theme = safe(theme, "");
            strategy = safe(strategy, "familiar");
            generatedTracks = Math.max(0L, generatedTracks);
        }

        public static Radio off() {
            return new Radio(false, "custom", "", "familiar", 0L);
        }
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNull(fallback) : value.trim();
    }
}
