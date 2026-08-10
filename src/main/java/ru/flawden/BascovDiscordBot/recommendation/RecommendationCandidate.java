package ru.flawden.BascovDiscordBot.recommendation;

import ru.flawden.BascovDiscordBot.catalog.TrackCatalogEntry;
import ru.flawden.BascovDiscordBot.catalog.TrackExternalId;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Provider-neutral candidate. Playback identifier appears only after transport resolution/search.
 */
public record RecommendationCandidate(
        String artist,
        String title,
        double similarity,
        String source,
        String reason,
        Set<String> tags,
        Set<TrackExternalId> externalIds) {

    public RecommendationCandidate(String artist, String title, double similarity, String source, String reason) {
        this(artist, title, similarity, source, reason, Set.of(), Set.of());
    }

    public RecommendationCandidate(
            String artist,
            String title,
            double similarity,
            String source,
            String reason,
            Set<String> tags) {
        this(artist, title, similarity, source, reason, tags, Set.of());
    }

    public RecommendationCandidate {
        artist = sanitize(artist, "Неизвестно");
        title = sanitize(title, "Неизвестный трек");
        similarity = Math.max(0.0d, Math.min(1.0d, similarity));
        source = sanitize(source, "local");
        reason = sanitize(reason, "Локальное продолжение");
        tags = normalizeTags(tags);
        externalIds = externalIds == null ? Set.of() : Set.copyOf(externalIds);
    }

    public String query() {
        return "Неизвестно".equalsIgnoreCase(artist) ? title : artist + " " + title;
    }

    public TrackIdentity trackIdentity() {
        return TrackIdentity.of(artist, title);
    }

    public String identity() {
        return trackIdentity().stableKey();
    }

    public TrackCatalogEntry catalogEntry() {
        return new TrackCatalogEntry(trackIdentity(), externalIds, tags);
    }

    public RecommendationCandidate withTags(Set<String> newTags) {
        return new RecommendationCandidate(artist, title, similarity, source, reason, newTags, externalIds);
    }

    public RecommendationCandidate withExternalId(TrackExternalId externalId) {
        if (externalId == null || externalIds.contains(externalId)) {
            return this;
        }
        LinkedHashSet<TrackExternalId> merged = new LinkedHashSet<>(externalIds);
        merged.add(externalId);
        return new RecommendationCandidate(artist, title, similarity, source, reason, tags, merged);
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
