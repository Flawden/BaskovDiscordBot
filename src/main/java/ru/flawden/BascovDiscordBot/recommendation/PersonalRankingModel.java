package ru.flawden.BascovDiscordBot.recommendation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic personal model: track + artist + tag affinity and an adaptive
 * exploration/exploitation balance. No network, playback or Discord dependency.
 */
public final class PersonalRankingModel {

    private PersonalRankingModel() {
    }

    public static PersonalTasteProfile build(List<RecommendationFeedbackEntry> history) {
        if (history == null || history.isEmpty()) {
            return PersonalTasteProfile.empty();
        }
        Map<String, Double> track = new LinkedHashMap<>();
        Map<String, Double> artist = new LinkedHashMap<>();
        Map<String, Double> tags = new LinkedHashMap<>();
        int positive = 0;
        int negative = 0;
        for (RecommendationFeedbackEntry entry : history) {
            if (entry == null) {
                continue;
            }
            double score = entry.signalScore();
            if (score != 0.0d) {
                track.merge(entry.trackIdentity(), score, Double::sum);
                artist.merge(RecommendationIdentity.normalizeArtist(entry.trackArtist()), score, Double::sum);
                for (String tag : entry.tags()) {
                    tags.merge(normalizeTag(tag), score, Double::sum);
                }
            }
            positive += entry.positiveSignals();
            negative += entry.negativeSignals();
        }
        return new PersonalTasteProfile(history.size(), positive, negative, track, artist, tags);
    }

    public static double explorationRate(PersonalTasteProfile profile, RadioStrategy strategy) {
        PersonalTasteProfile safe = profile == null ? PersonalTasteProfile.empty() : profile;
        RadioStrategy mode = strategy == null ? RadioStrategy.SIMILAR : strategy;
        double base = switch (mode) {
            case FAMILIAR -> 0.05d;
            case SIMILAR -> 0.20d;
            case DISCOVERY -> 0.36d;
        };
        int evidence = safe.evidenceSignals();
        double negativeRatio = evidence == 0 ? 0.35d : safe.negativeSignals() / (double) evidence;
        double adjusted = base
                + Math.max(-0.08d, Math.min(0.10d, (negativeRatio - 0.35d) * 0.22d))
                - safe.confidence() * 0.08d;
        return Math.max(0.05d, Math.min(0.45d, adjusted));
    }

    public static TasteScore score(RecommendationCandidate candidate, PersonalTasteProfile profile, RadioStrategy strategy) {
        PersonalTasteProfile safe = profile == null ? PersonalTasteProfile.empty() : profile;
        RecommendationCandidate value = candidate == null
                ? new RecommendationCandidate("Неизвестно", "Неизвестный трек", 0.0d, "local", "—")
                : candidate;
        double track = safe.trackScore(value.identity());
        double artist = safe.artistScore(value.artist());
        double tags = safe.tagScore(value.tags());
        double personal = track * 0.45d + artist * 0.35d + tags * 0.20d;
        double explorationRate = explorationRate(safe, strategy);
        boolean unexploredArtist = !safe.artistAffinity().containsKey(RecommendationIdentity.normalizeArtist(value.artist()));
        boolean unexploredTrack = !safe.trackAffinity().containsKey(value.identity());
        double explorationBonus = unexploredArtist && unexploredTrack ? explorationRate * 0.10d : 0.0d;
        return new TasteScore(track, artist, tags, personal, explorationRate, explorationBonus);
    }

    private static String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
    }

    public record TasteScore(
            double trackAffinity,
            double artistAffinity,
            double tagAffinity,
            double personalTaste,
            double explorationRate,
            double explorationBonus) {
    }
}
