package ru.flawden.BascovDiscordBot.home;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-neutral read model for the personalized Baskov Music home surface.
 *
 * <p>No Discord/JDA types belong here. Discord, Android and future web clients
 * should render the same semantic snapshot in their own UI.</p>
 */
public record HomeSnapshot(
        long guildId,
        long userId,
        LocalDate date,
        Optional<ContinuationCard> continuation,
        List<MixCard> today,
        List<MixCard> forYou,
        List<ThemeCard> themes,
        LibraryCard library,
        List<TrackPreview> recent,
        TasteCard taste) {

    public HomeSnapshot {
        if (guildId <= 0L) {
            throw new IllegalArgumentException("guildId must be positive");
        }
        if (userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }
        date = Objects.requireNonNull(date, "date");
        continuation = continuation == null ? Optional.empty() : continuation;
        today = List.copyOf(today == null ? List.of() : today);
        forYou = List.copyOf(forYou == null ? List.of() : forYou);
        themes = List.copyOf(themes == null ? List.of() : themes);
        library = library == null ? new LibraryCard(0, 0) : library;
        recent = List.copyOf(recent == null ? List.of() : recent);
        taste = taste == null ? TasteCard.empty() : taste;
    }

    public record ContinuationCard(
            Kind kind,
            String label,
            String stationSlug,
            String theme,
            LocalDate releaseDate,
            long generatedTracks) {
        public ContinuationCard {
            kind = kind == null ? Kind.RESUMABLE : kind;
            label = safe(label, "Микс");
            stationSlug = safe(stationSlug, "my-mix");
            theme = safe(theme, "");
            generatedTracks = Math.max(0L, generatedTracks);
        }

        public enum Kind {
            ACTIVE,
            RESUMABLE
        }
    }

    public record MixCard(
            String stationSlug,
            String label,
            String description,
            boolean available,
            boolean daily) {
        public MixCard {
            stationSlug = safe(stationSlug, "my-mix");
            label = safe(label, "Микс");
            description = safe(description, "");
        }
    }

    public record ThemeCard(String name, double affinity) {
        public ThemeCard {
            name = safe(name, "");
            if (!Double.isFinite(affinity)) {
                affinity = 0.0d;
            }
        }
    }

    public record LibraryCard(int favorites, int personalHistory) {
        public LibraryCard {
            favorites = Math.max(0, favorites);
            personalHistory = Math.max(0, personalHistory);
        }
    }

    public record TrackPreview(String title, String artist) {
        public TrackPreview {
            title = safe(title, "Неизвестный трек");
            artist = safe(artist, "Неизвестно");
        }
    }

    public record TasteCard(int evidenceSignals, double confidence, int recommendations) {
        public TasteCard {
            evidenceSignals = Math.max(0, evidenceSignals);
            recommendations = Math.max(0, recommendations);
            if (!Double.isFinite(confidence)) {
                confidence = 0.0d;
            }
            confidence = Math.max(0.0d, Math.min(1.0d, confidence));
        }

        public static TasteCard empty() {
            return new TasteCard(0, 0.0d, 0);
        }
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
