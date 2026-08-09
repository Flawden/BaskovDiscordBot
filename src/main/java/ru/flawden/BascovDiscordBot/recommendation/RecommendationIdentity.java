package ru.flawden.BascovDiscordBot.recommendation;

import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Provider-independent identity для novelty filtering.
 */
public final class RecommendationIdentity {

    private RecommendationIdentity() {
    }

    public static String of(StoredTrack track) {
        return track == null ? "unknown" : of(track.author(), track.title());
    }

    public static String of(String artist, String title) {
        return normalize(artist) + "::" + normalize(title);
    }

    public static String normalizeArtist(String artist) {
        return normalize(artist);
    }

    private static String normalize(String value) {
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
}
