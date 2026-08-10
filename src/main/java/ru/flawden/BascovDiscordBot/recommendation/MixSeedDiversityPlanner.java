package ru.flawden.BascovDiscordBot.recommendation;

import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Greedy bounded seed ordering that spreads artists before reusing one.
 *
 * <p>It never drops a seed: when all unique artists are exhausted the remaining tracks are
 * appended in stable round-robin order.</p>
 */
public final class MixSeedDiversityPlanner {

    private MixSeedDiversityPlanner() {
    }

    public static List<StoredTrack> spreadArtists(List<StoredTrack> source) {
        if (source == null || source.size() < 2) {
            return source == null ? List.of() : List.copyOf(source);
        }
        LinkedHashMap<String, List<StoredTrack>> byArtist = new LinkedHashMap<>();
        for (StoredTrack track : source) {
            byArtist.computeIfAbsent(artist(track), ignored -> new ArrayList<>()).add(track);
        }
        if (byArtist.size() < 2) {
            return List.copyOf(source);
        }

        ArrayList<StoredTrack> result = new ArrayList<>(source.size());
        int round = 0;
        boolean added;
        do {
            added = false;
            for (Map.Entry<String, List<StoredTrack>> entry : byArtist.entrySet()) {
                if (round < entry.getValue().size()) {
                    result.add(entry.getValue().get(round));
                    added = true;
                }
            }
            round++;
        } while (added);
        return List.copyOf(result);
    }

    private static String artist(StoredTrack track) {
        if (track == null || track.author() == null || track.author().isBlank()) {
            return "unknown";
        }
        return track.author().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
