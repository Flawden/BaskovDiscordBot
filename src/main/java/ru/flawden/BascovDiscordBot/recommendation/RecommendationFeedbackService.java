package ru.flawden.BascovDiscordBot.recommendation;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.lavaplayer.PlaybackFeedbackEvent;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequest;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Converts existing user/playback behavior into bounded implicit recommendation feedback.
 */
@Slf4j
@Component
public class RecommendationFeedbackService {

    static final long QUICK_NEGATIVE_MAX_MILLIS = 30_000L;
    static final double QUICK_NEGATIVE_MAX_RATIO = 0.20d;

    private final RecommendationFeedbackRepository repository;

    public RecommendationFeedbackService(RecommendationFeedbackRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void recordRecommendation(
            long guildId,
            long userId,
            StoredTrack seed,
            AudioTrack selected,
            Set<String> tags,
            RadioStrategy strategy,
            String provider,
            double similarity) {
        if (guildId <= 0L || userId <= 0L || seed == null || selected == null || selected.getInfo() == null) {
            return;
        }
        String trackIdentity = TrackIdentity.of(selected.getInfo().author, selected.getInfo().title).stableKey();
        safely("record-recommendation", () -> repository.recordRecommendation(
                FileRecommendationFeedbackRepository.pending(
                        guildId,
                        userId,
                        seed.author(),
                        seed.title(),
                        selected.getInfo().author,
                        selected.getInfo().title,
                        trackIdentity,
                        tags,
                        strategy,
                        provider,
                        similarity)));
    }

    public void recordRecommendation(
            long guildId,
            long userId,
            StoredTrack seed,
            AudioTrack selected,
            RadioStrategy strategy,
            String provider,
            double similarity) {
        recordRecommendation(guildId, userId, seed, selected, Set.of(), strategy, provider, similarity);
    }

    public PersonalTasteProfile tasteProfile(long guildId, long userId) {
        return PersonalRankingModel.build(repository.history(
                guildId,
                userId,
                RecommendationFeedbackRepository.MAX_ENTRIES_PER_USER));
    }

    public SessionTasteProfile sessionTasteProfile(long guildId, long userId, long startedAtEpochMillis) {
        return AdaptiveSessionModel.build(
                repository.history(
                        guildId,
                        userId,
                        RecommendationFeedbackRepository.MAX_ENTRIES_PER_USER),
                startedAtEpochMillis);
    }

    public ContextualBanditProfile banditProfile(long guildId, long userId) {
        return ContextualBanditModel.build(repository.history(
                guildId,
                userId,
                RecommendationFeedbackRepository.MAX_ENTRIES_PER_USER));
    }

    public void recordPlayback(long guildId, PlaybackFeedbackEvent event) {
        if (guildId <= 0L || event == null || event.request() == null) {
            return;
        }
        String identity = trackIdentity(event.request());
        if (isRadioTrack(event.request())) {
            if (event.type() == PlaybackFeedbackEvent.Type.COMPLETED) {
                safely("completed", () -> repository.recordLatestOutcome(
                        guildId, identity, RecommendationOutcome.COMPLETED, 1.0d));
                return;
            }
            double ratio = event.completionRatio();
            boolean quick = event.elapsedMillis() <= QUICK_NEGATIVE_MAX_MILLIS
                    || ratio <= QUICK_NEGATIVE_MAX_RATIO;
            safely("skip", () -> repository.recordLatestOutcome(
                    guildId,
                    identity,
                    quick ? RecommendationOutcome.QUICK_SKIPPED : RecommendationOutcome.SKIPPED,
                    ratio));
            return;
        }

        long userId = requesterUserId(event.request());
        if (userId <= 0L) {
            return;
        }
        RecommendationOutcome outcome;
        double ratio = event.completionRatio();
        if (event.type() == PlaybackFeedbackEvent.Type.COMPLETED) {
            outcome = RecommendationOutcome.COMPLETED;
            ratio = 1.0d;
        } else {
            boolean quick = event.elapsedMillis() <= QUICK_NEGATIVE_MAX_MILLIS
                    || ratio <= QUICK_NEGATIVE_MAX_RATIO;
            outcome = quick ? RecommendationOutcome.QUICK_SKIPPED : RecommendationOutcome.SKIPPED;
        }
        double finalRatio = ratio;
        RecommendationOutcome finalOutcome = outcome;
        safely("manual-playback:" + finalOutcome.name(), () -> repository.recordObservedOutcome(
                observedEntry(guildId, userId, event.request(), "discord"),
                finalOutcome,
                finalRatio));
    }
    public void recordStop(long guildId, TrackRequest request) {
        if (guildId <= 0L || request == null || request.track() == null) {
            return;
        }
        AudioTrack track = request.track();
        long duration = Math.max(1L, track.getDuration());
        long elapsed = Math.max(0L, track.getPosition());
        double ratio = Math.max(0.0d, Math.min(1.0d, elapsed / (double) duration));
        boolean quick = elapsed <= QUICK_NEGATIVE_MAX_MILLIS || ratio <= QUICK_NEGATIVE_MAX_RATIO;
        RecommendationOutcome outcome = quick
                ? RecommendationOutcome.QUICK_STOPPED
                : RecommendationOutcome.STOPPED;

        if (isRadioTrack(request)) {
            safely("stop", () -> repository.recordLatestOutcome(
                    guildId,
                    trackIdentity(request),
                    outcome,
                    ratio));
            return;
        }

        long userId = requesterUserId(request);
        if (userId > 0L) {
            safely("manual-stop:" + outcome.name(), () -> repository.recordObservedOutcome(
                    observedEntry(guildId, userId, request, "discord"),
                    outcome,
                    ratio));
        }
    }
    public void recordFavorite(long guildId, long userId, StoredTrack track) {
        recordUserSignal(guildId, userId, track, RecommendationOutcome.FAVORITED);
    }

    public void recordUnfavorite(long guildId, long userId, StoredTrack track) {
        recordUserSignal(guildId, userId, track, RecommendationOutcome.UNFAVORITED);
    }

    public void recordReplay(long guildId, long userId, StoredTrack track) {
        recordUserSignal(guildId, userId, track, RecommendationOutcome.REPLAYED);
    }

    public List<RecommendationFeedbackEntry> history(long guildId, long userId, int limit) {
        return repository.history(guildId, userId, limit);
    }

    public FeedbackSummary summary(long guildId, long userId) {
        List<RecommendationFeedbackEntry> history = repository.history(
                guildId,
                userId,
                RecommendationFeedbackRepository.MAX_ENTRIES_PER_USER);
        int positive = history.stream().mapToInt(RecommendationFeedbackEntry::positiveSignals).sum();
        int negative = history.stream().mapToInt(RecommendationFeedbackEntry::negativeSignals).sum();
        double score = history.stream().mapToDouble(RecommendationFeedbackEntry::signalScore).sum();
        long pending = history.stream()
                .filter(entry -> entry.lastOutcome() == RecommendationOutcome.PENDING)
                .count();
        return new FeedbackSummary(history.size(), positive, negative, pending, score);
    }

    /**
     * Client-neutral ingestion boundary for authenticated Product API clients.
     * Authentication and guild authorization remain at the HTTP adapter.
     */
    public RecommendationFeedbackEntry recordExternalSignal(
            long guildId,
            long userId,
            String artist,
            String title,
            String stableKey,
            String source,
            RecommendationOutcome outcome,
            double completionRatio) {
        if (guildId <= 0L || userId <= 0L) {
            throw new IllegalArgumentException("guildId and userId must be positive");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title cannot be blank");
        }
        RecommendationOutcome safeOutcome = Objects.requireNonNull(outcome, "outcome");
        String identity = stableKey == null || stableKey.isBlank()
                ? TrackIdentity.of(artist, title).stableKey()
                : stableKey.trim().toLowerCase(java.util.Locale.ROOT);
        String safeSource = source == null || source.isBlank()
                ? "external"
                : source.trim().toLowerCase(java.util.Locale.ROOT);
        RecommendationFeedbackEntry observed = FileRecommendationFeedbackRepository.pending(
                guildId,
                userId,
                artist,
                title,
                artist,
                title,
                identity,
                Set.of(),
                RadioStrategy.FAMILIAR,
                safeSource,
                1.0d);
        return repository.recordObservedOutcome(observed, safeOutcome, completionRatio);
    }

    private void recordUserSignal(
            long guildId,
            long userId,
            StoredTrack track,
            RecommendationOutcome outcome) {
        if (guildId <= 0L || userId <= 0L || track == null) {
            return;
        }
        safely("user-signal:" + outcome.name(), () -> repository.recordObservedOutcome(
                FileRecommendationFeedbackRepository.pending(
                        guildId,
                        userId,
                        track.author(),
                        track.title(),
                        track.author(),
                        track.title(),
                        track.trackIdentity().stableKey(),
                        Set.of(),
                        RadioStrategy.FAMILIAR,
                        "library",
                        1.0d),
                outcome,
                1.0d));
    }

    private static RecommendationFeedbackEntry observedEntry(
            long guildId,
            long userId,
            TrackRequest request,
            String source) {
        AudioTrack track = request == null ? null : request.track();
        String artist = track == null || track.getInfo() == null ? null : track.getInfo().author;
        String title = track == null || track.getInfo() == null ? null : track.getInfo().title;
        return FileRecommendationFeedbackRepository.pending(
                guildId,
                userId,
                artist,
                title,
                artist,
                title,
                trackIdentity(request),
                Set.of(),
                RadioStrategy.FAMILIAR,
                source,
                1.0d);
    }

    private static long requesterUserId(TrackRequest request) {
        return request == null || request.requester() == null ? 0L : request.requester().userId();
    }

    private static void safely(String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.warn("Recommendation feedback write failed during {}; primary action will continue: {}",
                    operation,
                    safeMessage(exception));
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        String safe = message.replace('\n', ' ').replace('\r', ' ').replace('`', '\'').trim();
        return safe.length() <= 240 ? safe : safe.substring(0, 237) + "...";
    }

    private static boolean isRadioTrack(TrackRequest request) {
        return request != null
                && request.requester() != null
                && request.requester().userId() == 0L
                && request.requester().displayName() != null
                && request.requester().displayName().contains("Radio");
    }

    private static String trackIdentity(TrackRequest request) {
        if (request == null || request.track() == null || request.track().getInfo() == null) {
            return "unknown";
        }
        return TrackIdentity.of(
                request.track().getInfo().author,
                request.track().getInfo().title).stableKey();
    }

    public record FeedbackSummary(
            int recommendations,
            int positiveSignals,
            int negativeSignals,
            long pending,
            double signalScore) {
    }
}
