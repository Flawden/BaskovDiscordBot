package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
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
    void repeatsFinishedTrackInTrackMode() {
        AudioPlayer player = mock(AudioPlayer.class);
        TrackScheduler scheduler = new TrackScheduler(
                player, 10, Duration.ofHours(4), () -> { }, () -> { });
        AudioTrack original = track("Golden Cup", Duration.ofMinutes(3));
        AudioTrack clone = track("Golden Cup", Duration.ofMinutes(3));
        when(player.startTrack(original, true)).thenReturn(true);
        when(original.makeClone()).thenReturn(clone);
        scheduler.queue(original, new TrackRequester(7L, "Requester"));
        scheduler.setRepeatMode(RepeatMode.TRACK);

        scheduler.onTrackEnd(player, original, AudioTrackEndReason.FINISHED);

        verify(player).startTrack(clone, false);
        assertSame(clone, scheduler.getCurrentRequest().track());
        assertEquals(RepeatMode.TRACK, scheduler.getRepeatMode());
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

    private static AudioTrack track(String title, Duration duration) {
        AudioTrack track = mock(AudioTrack.class);
        AudioTrackInfo info = mock(AudioTrackInfo.class);
        when(track.getInfo()).thenReturn(info);
        when(track.getDuration()).thenReturn(duration.toMillis());
        return track;
    }
}
