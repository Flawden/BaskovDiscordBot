package ru.flawden.BascovDiscordBot.lavaplayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only requester-aware projection of the waiting queue.
 */
public final class QueueCollaboration {

    private QueueCollaboration() {
    }

    public static Summary summarize(List<TrackRequest> requests, long viewerUserId) {
        List<TrackRequest> snapshot = requests == null ? List.of() : List.copyOf(requests);
        Map<String, MutableContributor> contributors = new LinkedHashMap<>();
        List<OwnedTrack> owned = new ArrayList<>();
        long totalDurationMillis = 0L;

        for (int index = 0; index < snapshot.size(); index++) {
            TrackRequest request = snapshot.get(index);
            long duration = safeDuration(request);
            totalDurationMillis += duration;
            TrackRequester requester = request.requester();
            String key = requester.userId() > 0
                    ? "user:" + requester.userId()
                    : "name:" + requester.displayName();
            MutableContributor contributor = contributors.computeIfAbsent(
                    key,
                    ignored -> new MutableContributor(requester.userId(), requester.displayName()));
            contributor.trackCount++;
            contributor.durationMillis += duration;
            contributor.positions.add(index + 1);

            if (viewerUserId > 0L && requester.userId() == viewerUserId) {
                owned.add(new OwnedTrack(index + 1, request));
            }
        }

        List<Contributor> ranked = contributors.values().stream()
                .map(MutableContributor::snapshot)
                .sorted(Comparator.comparingInt(Contributor::trackCount).reversed()
                        .thenComparing(Comparator.comparingLong(Contributor::durationMillis).reversed())
                        .thenComparing(Contributor::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        long ownDurationMillis = owned.stream()
                .mapToLong(item -> safeDuration(item.request()))
                .sum();
        return new Summary(
                snapshot.size(),
                totalDurationMillis,
                ranked,
                owned,
                ownDurationMillis);
    }

    private static long safeDuration(TrackRequest request) {
        return request == null || request.track() == null ? 0L : Math.max(0L, request.track().getDuration());
    }

    public record Summary(
            int totalTracks,
            long totalDurationMillis,
            List<Contributor> contributors,
            List<OwnedTrack> ownedTracks,
            long ownDurationMillis) {
        public Summary {
            contributors = List.copyOf(contributors);
            ownedTracks = List.copyOf(ownedTracks);
        }
    }

    public record Contributor(
            long userId,
            String displayName,
            int trackCount,
            long durationMillis,
            List<Integer> positions) {
        public Contributor {
            displayName = displayName == null || displayName.isBlank() ? "Неизвестно" : displayName;
            positions = List.copyOf(positions);
        }

        public String discordLabel() {
            return userId > 0L ? "<@" + userId + ">" : displayName;
        }
    }

    public record OwnedTrack(int globalPosition, TrackRequest request) {
    }

    private static final class MutableContributor {
        private final long userId;
        private final String displayName;
        private int trackCount;
        private long durationMillis;
        private final List<Integer> positions = new ArrayList<>();

        private MutableContributor(long userId, String displayName) {
            this.userId = userId;
            this.displayName = displayName;
        }

        private Contributor snapshot() {
            return new Contributor(userId, displayName, trackCount, durationMillis, positions);
        }
    }
}
