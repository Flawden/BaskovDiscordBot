package ru.flawden.BascovDiscordBot.recommendation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Short-horizon contextual model for one explicit smart-radio session.
 * Recent feedback receives more weight than older feedback from the same session.
 */
public final class AdaptiveSessionModel {

    static final int MAX_SESSION_ENTRIES = 20;
    private static final double RECENCY_DECAY = 0.86d;

    private AdaptiveSessionModel() {
    }

    public static SessionTasteProfile build(
            List<RecommendationFeedbackEntry> history,
            long startedAtEpochMillis) {
        if (startedAtEpochMillis <= 0L || history == null || history.isEmpty()) {
            return SessionTasteProfile.empty(startedAtEpochMillis);
        }

        Map<String, Double> track = new LinkedHashMap<>();
        Map<String, Double> artist = new LinkedHashMap<>();
        Map<String, Double> tags = new LinkedHashMap<>();
        int positive = 0;
        int negative = 0;
        int recommendations = 0;
        double weightedSignal = 0.0d;
        double weightedMagnitude = 0.0d;
        int index = 0;

        for (RecommendationFeedbackEntry entry : history) {
            if (entry == null || entry.recommendedAtEpochMillis() < startedAtEpochMillis) {
                continue;
            }
            if (recommendations >= MAX_SESSION_ENTRIES) {
                break;
            }
            double recencyWeight = Math.pow(RECENCY_DECAY, index++);
            double score = entry.signalScore() * recencyWeight;
            if (score != 0.0d) {
                track.merge(entry.trackIdentity(), score, Double::sum);
                artist.merge(RecommendationIdentity.normalizeArtist(entry.trackArtist()), score, Double::sum);
                for (String tag : entry.tags()) {
                    tags.merge(normalizeTag(tag), score, Double::sum);
                }
                weightedSignal += score;
                weightedMagnitude += Math.abs(score);
            }
            positive += entry.positiveSignals();
            negative += entry.negativeSignals();
            recommendations++;
        }

        if (recommendations == 0) {
            return SessionTasteProfile.empty(startedAtEpochMillis);
        }
        double momentum = weightedMagnitude <= 1.0e-9d
                ? 0.0d
                : Math.max(-1.0d, Math.min(1.0d, weightedSignal / weightedMagnitude));
        return new SessionTasteProfile(
                startedAtEpochMillis,
                recommendations,
                positive,
                negative,
                momentum,
                track,
                artist,
                tags);
    }

    public static SessionScore score(RecommendationCandidate candidate, SessionTasteProfile profile) {
        SessionTasteProfile safe = profile == null ? SessionTasteProfile.empty(0L) : profile;
        RecommendationCandidate value = candidate == null
                ? new RecommendationCandidate("Неизвестно", "Неизвестный трек", 0.0d, "local", "—")
                : candidate;
        double track = safe.trackScore(value.identity());
        double artist = safe.artistScore(value.artist());
        double tags = safe.tagScore(value.tags());
        double taste = track * 0.40d + artist * 0.40d + tags * 0.20d;
        return new SessionScore(track, artist, tags, taste, safe.confidence(), safe.momentum());
    }

    public static double explorationAdjustment(SessionTasteProfile profile, RecommendationCandidate candidate) {
        SessionTasteProfile safe = profile == null ? SessionTasteProfile.empty(0L) : profile;
        if (safe.confidence() <= 0.0d || safe.momentum() >= 0.0d || candidate == null) {
            return 0.0d;
        }
        boolean unexploredTrack = !safe.trackAffinity().containsKey(candidate.identity());
        boolean unexploredArtist = !safe.artistAffinity().containsKey(
                RecommendationIdentity.normalizeArtist(candidate.artist()));
        if (!unexploredTrack || !unexploredArtist) {
            return 0.0d;
        }
        return Math.min(0.06d, Math.abs(safe.momentum()) * safe.confidence() * 0.06d);
    }

    private static String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
    }

    public record SessionScore(
            double trackAffinity,
            double artistAffinity,
            double tagAffinity,
            double sessionTaste,
            double confidence,
            double momentum) {
    }
}
