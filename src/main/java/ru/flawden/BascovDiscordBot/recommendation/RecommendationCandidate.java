package ru.flawden.BascovDiscordBot.recommendation;

/**
 * Provider-neutral кандидат. Playback identifier появляется только после ytsearch.
 */
public record RecommendationCandidate(
        String artist,
        String title,
        double similarity,
        String source,
        String reason) {

    public RecommendationCandidate {
        artist = sanitize(artist, "Неизвестно");
        title = sanitize(title, "Неизвестный трек");
        similarity = Math.max(0.0d, Math.min(1.0d, similarity));
        source = sanitize(source, "local");
        reason = sanitize(reason, "Локальное продолжение");
    }

    public String query() {
        return "Неизвестно".equalsIgnoreCase(artist) ? title : artist + " " + title;
    }

    public String identity() {
        return RecommendationIdentity.of(artist, title);
    }

    private static String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160).trim();
    }
}
