package ru.flawden.BascovDiscordBot.catalog;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * Provider-independent logical identity of a musical recording inside Baskov Music.
 *
 * <p>The stable key deliberately contains no playback URL, video id or provider name.
 * Provider-specific playback references belong to the playback resolution layer.</p>
 */
public record TrackIdentity(
        String artist,
        String title,
        String normalizedArtist,
        String normalizedTitle) {

    private static final int MAX_DISPLAY_LENGTH = 180;

    public TrackIdentity {
        artist = sanitizeDisplay(artist, "Неизвестно");
        title = sanitizeDisplay(title, "Неизвестный трек");
        normalizedArtist = requireNormalized(normalizedArtist, "normalizedArtist");
        normalizedTitle = requireNormalized(normalizedTitle, "normalizedTitle");
    }

    public static TrackIdentity of(String artist, String title) {
        return new TrackIdentity(
                artist,
                title,
                normalize(artist),
                normalize(title));
    }

    /**
     * Stable application-level identity used by novelty, feedback and catalog joins.
     * It is not claimed to be a globally authoritative recording identifier.
     */
    public String stableKey() {
        return normalizedArtist + "::" + normalizedTitle;
    }

    public boolean sameLogicalTrack(TrackIdentity other) {
        return other != null && stableKey().equals(other.stableKey());
    }

    public static String normalizeArtist(String artist) {
        return normalize(artist);
    }

    public static String normalizeTitle(String title) {
        return normalize(title);
    }

    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return ascii.isBlank() ? "unknown" : ascii;
    }

    private static String sanitizeDisplay(String value, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim().replaceAll("\\s+", " ");
        return safe.length() <= MAX_DISPLAY_LENGTH ? safe : safe.substring(0, MAX_DISPLAY_LENGTH).trim();
    }

    private static String requireNormalized(String value, String field) {
        Objects.requireNonNull(value, field);
        String safe = value.trim();
        if (safe.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return safe;
    }
}
