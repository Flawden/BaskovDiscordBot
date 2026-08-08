package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackSchedulerTest {

    @Test
    void enforcesBoundedWaitingQueue() {
        AudioPlayer player = mock(AudioPlayer.class);
        AtomicInteger activity = new AtomicInteger();
        TrackScheduler scheduler = new TrackScheduler(
                player, 1, Duration.ofHours(4), activity::incrementAndGet, () -> { });
        AudioTrack first = track("First", Duration.ofMinutes(3));
        AudioTrack second = track("Second", Duration.ofMinutes(4));
        when(player.startTrack(first, true)).thenReturn(false);
        when(player.startTrack(second, true)).thenReturn(false);

        assertEquals(TrackScheduler.QueueStatus.QUEUED, scheduler.queue(first).status());
        assertEquals(TrackScheduler.QueueStatus.QUEUE_FULL, scheduler.queue(second).status());
        assertEquals(1, scheduler.queueSize());
        assertEquals(2, activity.get());
    }

    @Test
    void rejectsTracksLongerThanConfiguredLimitBeforeStartingPlayer() {
        AudioPlayer player = mock(AudioPlayer.class);
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofMinutes(30), () -> { }, () -> { });
        AudioTrack track = track("Too long", Duration.ofMinutes(31));

        assertEquals(TrackScheduler.QueueStatus.TRACK_TOO_LONG, scheduler.queue(track).status());
        assertEquals(0, scheduler.queueSize());
    }

    @Test
    void emptyQueueSchedulesIdleDisconnect() {
        AudioPlayer player = mock(AudioPlayer.class);
        AtomicInteger idle = new AtomicInteger();
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofHours(4), () -> { }, idle::incrementAndGet);

        assertEquals(null, scheduler.nextTrack());
        verify(player).stopTrack();
        assertEquals(1, idle.get());
    }

    @Test
    void keepsRequesterAndCalculatesEstimatedStartTime() {
        AudioPlayer player = mock(AudioPlayer.class);
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofHours(4), () -> { }, () -> { });
        AudioTrack current = track("Current", Duration.ofSeconds(60));
        AudioTrack queued = track("Queued", Duration.ofSeconds(30));
        when(current.getPosition()).thenReturn(10_000L);
        when(player.getPlayingTrack()).thenReturn(current);
        when(player.startTrack(queued, true)).thenReturn(false);
        TrackRequester requester = new TrackRequester(42L, "Даниил");

        TrackScheduler.QueueResult result = scheduler.queue(queued, requester);

        assertEquals(TrackScheduler.QueueStatus.QUEUED, result.status());
        assertEquals(50_000L, result.estimatedWaitMillis());
        assertEquals(requester, result.request().requester());
        assertEquals(requester, scheduler.queuedRequests().get(0).requester());
    }

    @Test
    void supportsMoveRemoveAndClearWithoutTouchingCurrentTrack() {
        AudioPlayer player = mock(AudioPlayer.class);
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofHours(4), () -> { }, () -> { });
        AudioTrack first = track("First", Duration.ofMinutes(1));
        AudioTrack second = track("Second", Duration.ofMinutes(2));
        AudioTrack third = track("Third", Duration.ofMinutes(3));
        when(player.startTrack(first, true)).thenReturn(false);
        when(player.startTrack(second, true)).thenReturn(false);
        when(player.startTrack(third, true)).thenReturn(false);
        scheduler.queue(first);
        scheduler.queue(second);
        scheduler.queue(third);

        assertTrue(scheduler.move(1, 3));
        assertEquals(List.of(second, third, first), scheduler.queuedTracks());
        assertSame(third, scheduler.removeAt(2).track());
        assertEquals(List.of(second, first), scheduler.queuedTracks());
        assertEquals(2, scheduler.clearQueue());
        assertEquals(0, scheduler.queueSize());
    }

    @Test
    void queueRevisionTracksWaitingQueueMutations() {
        AudioPlayer player = mock(AudioPlayer.class);
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofHours(4), () -> { }, () -> { });
        AudioTrack first = track("First", Duration.ofMinutes(1));
        AudioTrack second = track("Second", Duration.ofMinutes(2));
        when(player.startTrack(first, true)).thenReturn(true);
        when(player.startTrack(second, true)).thenReturn(false);

        scheduler.queue(first);
        assertEquals(0L, scheduler.queueRevision());

        scheduler.queue(second);
        assertEquals(1L, scheduler.queueRevision());

        scheduler.removeAt(1);
        assertEquals(2L, scheduler.queueRevision());
    }

    @Test
    void staleRevisionRejectsRangeRemovalWithoutChangingQueue() {
        AudioPlayer player = mock(AudioPlayer.class);
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofHours(4), () -> { }, () -> { });
        AudioTrack first = trackWithIdentifier("First", "one", Duration.ofMinutes(1));
        AudioTrack second = trackWithIdentifier("Second", "two", Duration.ofMinutes(2));
        when(player.startTrack(first, true)).thenReturn(false);
        when(player.startTrack(second, true)).thenReturn(false);
        scheduler.queue(first);
        scheduler.queue(second);

        TrackScheduler.QueueMutationResult result = scheduler.removeRange(
                1, 2, OptionalLong.of(1L));

        assertEquals(TrackScheduler.QueueMutationStatus.STALE_REVISION, result.status());
        assertEquals(List.of(first, second), scheduler.queuedTracks());
        assertEquals(2L, result.revision());
    }

    @Test
    void deduplicatePreservesFirstOccurrenceAndReportsRemovedDuration() {
        AudioPlayer player = mock(AudioPlayer.class);
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofHours(4), () -> { }, () -> { });
        AudioTrack first = trackWithIdentifier("First", "same-id", Duration.ofMinutes(1));
        AudioTrack duplicate = trackWithIdentifier("Duplicate", "same-id", Duration.ofMinutes(3));
        AudioTrack unique = trackWithIdentifier("Unique", "unique-id", Duration.ofMinutes(2));
        when(player.startTrack(first, true)).thenReturn(false);
        when(player.startTrack(duplicate, true)).thenReturn(false);
        when(player.startTrack(unique, true)).thenReturn(false);
        scheduler.queue(first);
        scheduler.queue(duplicate);
        scheduler.queue(unique);
        long revision = scheduler.queueRevision();

        TrackScheduler.QueueMutationResult result = scheduler.deduplicateQueue(
                OptionalLong.of(revision));

        assertEquals(TrackScheduler.QueueMutationStatus.APPLIED, result.status());
        assertEquals(1, result.removedCount());
        assertEquals(Duration.ofMinutes(3).toMillis(), result.removedDurationMillis());
        assertEquals(List.of(first, unique), scheduler.queuedTracks());
        assertEquals(revision + 1L, result.revision());
    }

    @Test
    void removeRequesterOnlyDeletesOwnedWaitingTracks() {
        AudioPlayer player = mock(AudioPlayer.class);
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofHours(4), () -> { }, () -> { });
        AudioTrack mine1 = trackWithIdentifier("Mine 1", "mine-1", Duration.ofMinutes(1));
        AudioTrack other = trackWithIdentifier("Other", "other", Duration.ofMinutes(2));
        AudioTrack mine2 = trackWithIdentifier("Mine 2", "mine-2", Duration.ofMinutes(3));
        when(player.startTrack(mine1, true)).thenReturn(false);
        when(player.startTrack(other, true)).thenReturn(false);
        when(player.startTrack(mine2, true)).thenReturn(false);
        scheduler.queue(mine1, new TrackRequester(42L, "Me"));
        scheduler.queue(other, new TrackRequester(7L, "Other"));
        scheduler.queue(mine2, new TrackRequester(42L, "Me"));

        TrackScheduler.QueueMutationResult result = scheduler.removeRequester(
                42L, OptionalLong.of(scheduler.queueRevision()));

        assertEquals(TrackScheduler.QueueMutationStatus.APPLIED, result.status());
        assertEquals(2, result.removedCount());
        assertEquals(List.of(other), scheduler.queuedTracks());
        assertEquals(1, scheduler.queueStats().uniqueRequesters());
    }

    @Test
    void repeatsFinishedTrackInTrackMode() {
        AudioPlayer player = mock(AudioPlayer.class);
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofHours(4), () -> { }, () -> { });
        AudioTrack original = track("Golden Cup", Duration.ofMinutes(3));
        AudioTrack clone = track("Golden Cup", Duration.ofMinutes(3));
        when(player.startTrack(original, true)).thenReturn(true);
        when(original.getPosition()).thenReturn(Duration.ofMinutes(3).toMillis());
        when(original.makeClone()).thenReturn(clone);
        scheduler.queue(original, new TrackRequester(7L, "Requester"));
        scheduler.setRepeatMode(RepeatMode.TRACK);

        scheduler.onTrackEnd(player, original, AudioTrackEndReason.FINISHED);

        verify(player).startTrack(clone, false);
        assertSame(clone, scheduler.getCurrentRequest().track());
        assertEquals(RepeatMode.TRACK, scheduler.getRepeatMode());
    }



    @Test
    void prematureFinishedPreviewUsesFallbackInsteadOfAdvancingVisibleQueue() {
        AudioPlayer player = mock(AudioPlayer.class);
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofHours(4), () -> { }, () -> { });
        AudioTrack preview = track("Preview", Duration.ofMinutes(3));
        AudioTrack fallback = track("Full version", Duration.ofMinutes(3));
        when(preview.getPosition()).thenReturn(30_000L);
        when(player.startTrack(preview, true)).thenReturn(true);
        scheduler.queue(preview, TrackRequester.unknown(), List.of(fallback));

        scheduler.onTrackEnd(player, preview, AudioTrackEndReason.FINISHED);

        verify(player).startTrack(fallback, false);
        assertSame(fallback, scheduler.getCurrentRequest().track());
        assertEquals(0, scheduler.historySize());
    }

    @Test
    void prematureFinishedPreviewWithoutFallbackDoesNotEnterHistory() {
        AudioPlayer player = mock(AudioPlayer.class);
        AtomicInteger idle = new AtomicInteger();
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofHours(4), () -> { }, idle::incrementAndGet);
        AudioTrack preview = track("Preview", Duration.ofMinutes(3));
        when(preview.getPosition()).thenReturn(30_000L);
        when(player.startTrack(preview, true)).thenReturn(true);
        scheduler.queue(preview);

        scheduler.onTrackEnd(player, preview, AudioTrackEndReason.FINISHED);

        assertEquals(null, scheduler.getCurrentRequest());
        assertEquals(0, scheduler.historySize());
        assertEquals(1, idle.get());
    }

    @Test
    void cleanupEndReasonUsesFallbackWithoutClosingVoiceSession() {
        AudioPlayer player = mock(AudioPlayer.class);
        AtomicInteger idle = new AtomicInteger();
        TrackScheduler scheduler = new TrackScheduler(
                player,
                10,
                Duration.ofHours(4),
                RepeatMode.OFF,
                () -> { },
                idle::incrementAndGet);
        AudioTrack current = track("Unstable", Duration.ofMinutes(3));
        AudioTrack fallback = track("Fallback", Duration.ofMinutes(3));
        when(player.startTrack(current, true)).thenReturn(true);
        scheduler.queue(current, TrackRequester.unknown(), List.of(fallback));

        scheduler.onTrackEnd(player, current, AudioTrackEndReason.CLEANUP);

        verify(player).startTrack(fallback, false);
        assertSame(fallback, scheduler.getCurrentRequest().track());
        assertEquals(0, idle.get());
    }

    @Test
    void cleanupWithoutFallbackMovesToQueueOrSchedulesNormalIdleDisconnect() {
        AudioPlayer player = mock(AudioPlayer.class);
        AtomicInteger idle = new AtomicInteger();
        TrackScheduler scheduler = new TrackScheduler(
                player,
                10,
                Duration.ofHours(4),
                RepeatMode.OFF,
                () -> { },
                idle::incrementAndGet);
        AudioTrack current = track("Unstable", Duration.ofMinutes(3));
        when(player.startTrack(current, true)).thenReturn(true);
        scheduler.queue(current);

        scheduler.onTrackEnd(player, current, AudioTrackEndReason.CLEANUP);

        verify(player).stopTrack();
        assertEquals(null, scheduler.getCurrentRequest());
        assertEquals(1, idle.get());
    }

    @Test
    void startsWithPersistedRepeatModeWithoutTriggeringActivity() {
        AudioPlayer player = mock(AudioPlayer.class);
        AtomicInteger activity = new AtomicInteger();
        TrackScheduler scheduler = new TrackScheduler(
                player,
                10,
                Duration.ofHours(4),
                RepeatMode.QUEUE,
                activity::incrementAndGet,
                () -> { });

        assertEquals(RepeatMode.QUEUE, scheduler.getRepeatMode());
        assertEquals(0, activity.get());
    }


    @Test
    void playbackFailureTriesNextSearchResultBeforeLeavingQueue() {
        AudioPlayer player = mock(AudioPlayer.class);
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofHours(4), () -> { }, () -> { });
        AudioTrack primary = track("Broken result", Duration.ofMinutes(3));
        AudioTrack fallback = track("Working result", Duration.ofMinutes(3));
        when(player.startTrack(primary, true)).thenReturn(true);
        scheduler.queue(primary, TrackRequester.unknown(), List.of(fallback));

        com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception =
                mock(com.sedmelluq.discord.lavaplayer.tools.FriendlyException.class);
        when(exception.getMessage()).thenReturn("404");

        scheduler.onTrackException(player, primary, exception);

        verify(player).startTrack(fallback, false);
        assertSame(fallback, scheduler.getCurrentRequest().track());
        assertEquals(0, scheduler.getCurrentRequest().fallbackTracks().size());
    }

    @Test
    void ignoresStaleEndCallbackAfterFallbackHasStarted() {
        AudioPlayer player = mock(AudioPlayer.class);
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofHours(4), () -> { }, () -> { });
        AudioTrack primary = track("Broken", Duration.ofMinutes(3));
        AudioTrack fallback = track("Fallback", Duration.ofMinutes(3));
        when(player.startTrack(primary, true)).thenReturn(true);
        scheduler.queue(primary, TrackRequester.unknown(), List.of(fallback));

        com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception =
                mock(com.sedmelluq.discord.lavaplayer.tools.FriendlyException.class);
        when(exception.getMessage()).thenReturn("404");
        scheduler.onTrackException(player, primary, exception);
        scheduler.onTrackEnd(player, primary, AudioTrackEndReason.CLEANUP);

        assertSame(fallback, scheduler.getCurrentRequest().track());
        verify(player).startTrack(fallback, false);
    }

    @Test
    void publishesRememberedTrackToPersistentHistoryListener() {
        AudioPlayer player = mock(AudioPlayer.class);
        java.util.concurrent.atomic.AtomicReference<TrackRequest> persisted =
                new java.util.concurrent.atomic.AtomicReference<>();
        TrackScheduler scheduler = new TrackScheduler(
                player,
                10,
                Duration.ofHours(4),
                RepeatMode.OFF,
                () -> { },
                () -> { },
                TrackScheduler.Diagnostics.noop(),
                persisted::set);
        AudioTrack first = track("First", Duration.ofMinutes(3));
        AudioTrack remembered = track("First", Duration.ofMinutes(3));
        AudioTrack second = track("Second", Duration.ofMinutes(4));
        when(player.startTrack(first, true)).thenReturn(true);
        when(player.startTrack(second, true)).thenReturn(false);
        when(first.makeClone()).thenReturn(remembered);

        scheduler.queue(first, new TrackRequester(42L, "Requester"));
        scheduler.queue(second);
        scheduler.nextTrack();

        assertSame(remembered, persisted.get().track());
        assertEquals(42L, persisted.get().requester().userId());
    }

    @Test
    void persistentHistoryFailureCannotBlockNextTrack() {
        AudioPlayer player = mock(AudioPlayer.class);
        TrackScheduler scheduler = new TrackScheduler(
                player,
                10,
                Duration.ofHours(4),
                RepeatMode.OFF,
                () -> { },
                () -> { },
                TrackScheduler.Diagnostics.noop(),
                ignored -> {
                    throw new IllegalStateException("storage unavailable");
                });
        AudioTrack first = track("First", Duration.ofMinutes(3));
        AudioTrack remembered = track("First", Duration.ofMinutes(3));
        AudioTrack second = track("Second", Duration.ofMinutes(4));
        when(player.startTrack(first, true)).thenReturn(true);
        when(player.startTrack(second, true)).thenReturn(false);
        when(first.makeClone()).thenReturn(remembered);

        scheduler.queue(first);
        scheduler.queue(second);
        TrackRequest next = scheduler.nextTrack();

        assertSame(second, next.track());
        assertSame(second, scheduler.getCurrentRequest().track());
        verify(player).startTrack(second, false);
    }

    @Test
    void previousRestoresHistoryAndReturnsInterruptedTrackToQueueFront() {
        AudioPlayer player = mock(AudioPlayer.class);
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofHours(4), () -> { }, () -> { });
        AudioTrack first = track("First", Duration.ofMinutes(3));
        AudioTrack firstHistory = track("First", Duration.ofMinutes(3));
        AudioTrack firstReplay = track("First", Duration.ofMinutes(3));
        AudioTrack second = track("Second", Duration.ofMinutes(4));
        AudioTrack secondReturned = track("Second", Duration.ofMinutes(4));
        when(player.startTrack(first, true)).thenReturn(true);
        when(player.startTrack(second, true)).thenReturn(false);
        when(first.makeClone()).thenReturn(firstHistory);
        when(firstHistory.makeClone()).thenReturn(firstReplay);
        when(second.makeClone()).thenReturn(secondReturned);

        scheduler.queue(first, new TrackRequester(1L, "First requester"));
        scheduler.queue(second, new TrackRequester(2L, "Second requester"));
        assertSame(second, scheduler.nextTrack().track());
        assertEquals(1, scheduler.historySize());

        TrackScheduler.PreviousResult result = scheduler.previousTrack();

        assertEquals(TrackScheduler.PreviousStatus.STARTED, result.status());
        assertTrue(result.returnedCurrentToQueue());
        assertSame(firstReplay, result.request().track());
        assertSame(secondReturned, scheduler.queuedTracks().get(0));
        assertEquals(0, scheduler.historySize());
        verify(player).startTrack(firstReplay, false);
    }

    @Test
    void previousReportsEmptyHistoryWithoutReplacingCurrentTrack() {
        AudioPlayer player = mock(AudioPlayer.class);
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofHours(4), () -> { }, () -> { });
        AudioTrack current = track("Only", Duration.ofMinutes(3));
        when(player.startTrack(current, true)).thenReturn(true);
        scheduler.queue(current);

        TrackScheduler.PreviousResult result = scheduler.previousTrack();

        assertEquals(TrackScheduler.PreviousStatus.NO_HISTORY, result.status());
        assertSame(current, scheduler.getCurrentRequest().track());
    }

    private static AudioTrack track(String title, Duration duration) {
        return trackWithIdentifier(title, title.toLowerCase(), duration);
    }

    private static AudioTrack trackWithIdentifier(String title, String identifier, Duration duration) {
        AudioTrack track = mock(AudioTrack.class);
        AudioTrackInfo info = mock(AudioTrackInfo.class);
        when(track.getInfo()).thenReturn(info);
        when(track.getIdentifier()).thenReturn(identifier);
        when(track.getDuration()).thenReturn(duration.toMillis());
        return track;
    }
}
