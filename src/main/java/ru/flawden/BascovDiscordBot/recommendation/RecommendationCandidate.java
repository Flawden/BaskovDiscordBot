package ru.flawden.BascovDiscordBot.recommendation;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Provider-neutral кандидат. Playback identifier появляется только после ytsearch.
 */
public record RecommendationCandidate(
        String artist,
        String title,
        double similarity,
        String source,
        String reason,
        Set<String> tags) {

    public RecommendationCandidate(String artist, String title, double similarity, String source, String reason) {
        this(artist, title, similarity, source, reason, Set.of());
    }

    public RecommendationCandidate {
        artist = sanitize(artist, "Неизвестно");
        title = sanitize(title, "Неизвестный трек");
        similarity = Math.max(0.0d, Math.min(1.0d, similarity));
        source = sanitize(source, "local");
        reason = sanitize(reason, "Локальное продолжение");
        tags = normalizeTags(tags);
    }

    public String query() {
        return "Неизвестно".equalsIgnoreCase(artist) ? title : artist + " " + title;
    }

    public String identity() {
        return RecommendationIdentity.of(artist, title);
    }

    public RecommendationCandidate withTags(Set<String> newTags) {
        return new RecommendationCandidate(artist, title, similarity, source, reason, newTags);
    }

    private static Set<String> normalizeTags(Set<String> input) {
        if (input == null || input.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : input) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String safe = tag.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (safe.length() > 48) {
                safe = safe.substring(0, 48).trim();
            }
            if (!safe.isBlank()) {
                normalized.add(safe);
            }
            if (normalized.size() >= 8) {
                break;
            }
        }
        return Set.copyOf(normalized);
    }

    private static String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160).trim();
    }
}
