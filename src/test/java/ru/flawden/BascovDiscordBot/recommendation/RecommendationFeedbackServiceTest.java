package ru.flawden.BascovDiscordBot.recommendation;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.lavaplayer.PlaybackFeedbackEvent;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequest;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequester;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

class RecommendationFeedbackServiceTest {

    @Test
    void quickRadioSkipBecomesNegativeSignal() {
        CapturingRepository repository = new CapturingRepository();
        RecommendationFeedbackService service = new RecommendationFeedbackService(repository);
        TrackRequest request = radioRequest("Artist", "Track", 180_000L);

        service.recordPlayback(42L, new PlaybackFeedbackEvent(
                PlaybackFeedbackEvent.Type.SKIPPED,
                request,
                10_000L,
                180_000L));

        assertEquals(RecommendationOutcome.QUICK_SKIPPED, repository.lastOutcome);
        assertEquals(10_000d / 180_000d, repository.lastRatio, 0.001d);
    }

    @Test
    void completedRadioTrackBecomesPositiveSignal() {
        CapturingRepository repository = new CapturingRepository();
        RecommendationFeedbackService service = new RecommendationFeedbackService(repository);
        TrackRequest request = radioRequest("Artist", "Track", 180_000L);

        service.recordPlayback(42L, new PlaybackFeedbackEvent(
                PlaybackFeedbackEvent.Type.COMPLETED,
                request,
                1L,
                180_000L));

        assertEquals(RecommendationOutcome.COMPLETED, repository.lastOutcome);
        assertEquals(1.0d, repository.lastRatio, 0.001d);
    }

    @Test
    void manualTrackDoesNotPolluteRecommendationFeedback() {
        CapturingRepository repository = new CapturingRepository();
        RecommendationFeedbackService service = new RecommendationFeedbackService(repository);
        AudioTrack track = track("Artist", "Manual", 180_000L);
        TrackRequest request = TrackRequest.create(track, new TrackRequester(77L, "Human"));

        service.recordPlayback(42L, new PlaybackFeedbackEvent(
                PlaybackFeedbackEvent.Type.SKIPPED,
                request,
                5_000L,
                180_000L));

        assertEquals(null, repository.lastOutcome);
    }

    @Test
    void quickExplicitStopBecomesNegativeSignal() {
        CapturingRepository repository = new CapturingRepository();
        RecommendationFeedbackService service = new RecommendationFeedbackService(repository);
        TrackRequest request = radioRequest("Artist", "Track", 180_000L);
        when(request.track().getPosition()).thenReturn(12_000L);

        service.recordStop(42L, request);

        assertEquals(RecommendationOutcome.QUICK_STOPPED, repository.lastOutcome);
        assertEquals(12_000d / 180_000d, repository.lastRatio, 0.001d);
    }

    @Test
    void feedbackStorageFailureCannotBlockPrimaryAction() {
        RecommendationFeedbackRepository failing = new CapturingRepository() {
            @Override
            public Optional<RecommendationFeedbackEntry> recordLatestOutcome(
                    long guildId, String trackIdentity, RecommendationOutcome outcome, double completionRatio) {
                throw new IllegalStateException("disk unavailable");
            }
        };
        RecommendationFeedbackService service = new RecommendationFeedbackService(failing);
        TrackRequest request = radioRequest("Artist", "Track", 180_000L);

        assertDoesNotThrow(() -> service.recordPlayback(42L, new PlaybackFeedbackEvent(
                PlaybackFeedbackEvent.Type.SKIPPED, request, 5_000L, 180_000L)));
    }

    private static TrackRequest radioRequest(String artist, String title, long duration) {
        return TrackRequest.create(track(artist, title, duration), new TrackRequester(0L, "📻 Radio"));
    }

    private static AudioTrack track(String artist, String title, long duration) {
        AudioTrack track = mock(AudioTrack.class);
        AudioTrackInfo info = mock(AudioTrackInfo.class);
        when(track.getInfo()).thenReturn(info);
        when(track.getDuration()).thenReturn(duration);
        return track;
    }

    private static class CapturingRepository implements RecommendationFeedbackRepository {
        private RecommendationOutcome lastOutcome;
        private double lastRatio;

        @Override
        public RecommendationFeedbackEntry recordRecommendation(RecommendationFeedbackEntry entry) {
            return entry;
        }

        @Override
        public Optional<RecommendationFeedbackEntry> recordLatestOutcome(
                long guildId,
                String trackIdentity,
                RecommendationOutcome outcome,
                double completionRatio) {
            this.lastOutcome = outcome;
            this.lastRatio = completionRatio;
            return Optional.empty();
        }

        @Override
        public Optional<RecommendationFeedbackEntry> recordUserOutcome(
                long guildId,
                long userId,
                String trackIdentity,
                RecommendationOutcome outcome,
                double completionRatio) {
            this.lastOutcome = outcome;
            this.lastRatio = completionRatio;
            return Optional.empty();
        }

        @Override
        public List<RecommendationFeedbackEntry> history(long guildId, long userId, int limit) {
            return new ArrayList<>();
        }
    }
}
