package ru.flawden.BascovDiscordBot.recommendation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ephemeral mix-level context used only to diversify curated stations.
 *
 * <p>It is intentionally derived from the active radio state and is never persisted.
 * Manual /radio sessions keep this layer disabled.</p>
 */
public record MixDiversityProfile(
        boolean enabled,
        String themeFocus,
        List<String> recentArtists,
        List<Set<String>> recentTagSets) {

    public MixDiversityProfile {
        themeFocus = normalizeTheme(themeFocus);
        recentArtists = immutableArtists(recentArtists);
        recentTagSets = immutableTagSets(recentTagSets);
    }

    public static MixDiversityProfile disabled() {
        return new MixDiversityProfile(false, "", List.of(), List.of());
    }

    public boolean themed() {
        return enabled && !themeFocus.isBlank();
    }

    public int recentArtistOccurrences(String artist) {
        if (!enabled || artist == null || artist.isBlank()) {
            return 0;
        }
        String normalized = RecommendationIdentity.normalizeArtist(artist);
        if ("unknown".equals(normalized)) {
            return 0;
        }
        int count = 0;
        for (String recent : recentArtists) {
            if (normalized.equals(recent)) {
                count++;
            }
        }
        return count;
    }

    public boolean repeatsImmediateArtist(String artist) {
        if (!enabled || recentArtists.isEmpty()) {
            return false;
        }
        String normalized = RecommendationIdentity.normalizeArtist(artist);
        return !"unknown".equals(normalized) && recentArtists.get(0).equals(normalized);
    }

    public double recentTagShare(String tag) {
        String normalized = normalizeTheme(tag);
        if (!enabled || normalized.isBlank() || recentTagSets.isEmpty()) {
            return 0.0d;
        }
        int matches = 0;
        for (Set<String> tags : recentTagSets) {
            if (tags.contains(normalized)) {
                matches++;
            }
        }
        return (double) matches / (double) recentTagSets.size();
    }

    public static String normalizeTheme(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String safe = value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return safe.length() <= 48 ? safe : safe.substring(0, 48).trim();
    }

    private static List<String> immutableArtists(List<String> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        ArrayList<String> values = new ArrayList<>();
        for (String artist : input) {
            if (artist == null || artist.isBlank()) {
                continue;
            }
            values.add(RecommendationIdentity.normalizeArtist(artist));
            if (values.size() >= 6) {
                break;
            }
        }
        return List.copyOf(values);
    }

    private static List<Set<String>> immutableTagSets(List<Set<String>> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        ArrayList<Set<String>> values = new ArrayList<>();
        for (Set<String> tags : input) {
            if (tags == null || tags.isEmpty()) {
                continue;
            }
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String tag : tags) {
                String safe = normalizeTheme(tag);
                if (!safe.isBlank()) {
                    normalized.add(safe);
                }
                if (normalized.size() >= 8) {
                    break;
                }
            }
            if (!normalized.isEmpty()) {
                values.add(Set.copyOf(normalized));
            }
            if (values.size() >= 6) {
                break;
            }
        }
        return List.copyOf(values);
    }
}
