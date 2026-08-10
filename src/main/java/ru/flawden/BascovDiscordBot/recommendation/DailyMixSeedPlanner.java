package ru.flawden.BascovDiscordBot.recommendation;

import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministically rotates a bounded personal seed pool for a calendar day.
 *
 * <p>No state is persisted: the same guild/user/station/day receives the same
 * ordering, while the next day naturally produces a different ordering. The
 * planner never owns playback and never mutates the source list.</p>
 */
public final class DailyMixSeedPlanner {

    public static final int DEFAULT_LIMIT = 8;

    private DailyMixSeedPlanner() {
    }

    public static List<StoredTrack> plan(
            List<StoredTrack> source,
            long guildId,
            long userId,
            PersonalizedStation station,
            LocalDate date,
            int limit) {
        if (source == null || source.isEmpty() || limit <= 0) {
            return List.of();
        }
        PersonalizedStation selected = station == null ? PersonalizedStation.DAILY_MIX : station;
        LocalDate selectedDate = Objects.requireNonNull(date, "date");
        String salt = guildId + ":" + userId + ":" + selected.slug() + ":" + selectedDate;

        List<StoredTrack> copy = new ArrayList<>(source);
        copy.sort((left, right) -> {
            long leftScore = score(salt, left);
            long rightScore = score(salt, right);
            int byScore = Long.compareUnsigned(leftScore, rightScore);
            if (byScore != 0) {
                return byScore;
            }
            return identifier(left).compareTo(identifier(right));
        });
        return List.copyOf(copy.subList(0, Math.min(limit, copy.size())));
    }

    private static long score(String salt, StoredTrack track) {
        String value = salt + ':' + identifier(track);
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return mix64(hash);
    }

    private static String identifier(StoredTrack track) {
        return track == null || track.playbackIdentifier() == null
                ? "unknown"
                : track.playbackIdentifier().trim().toLowerCase(Locale.ROOT);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
