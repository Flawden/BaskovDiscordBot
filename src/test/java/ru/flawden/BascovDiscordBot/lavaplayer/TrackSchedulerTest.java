package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        AudioTrack first = track(Duration.ofMinutes(3));
        AudioTrack second = track(Duration.ofMinutes(4));
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
        AudioTrack track = track(Duration.ofMinutes(31));

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

    private static AudioTrack track(Duration duration) {
        AudioTrack track = mock(AudioTrack.class);
        AudioTrackInfo info = mock(AudioTrackInfo.class);
        when(track.getInfo()).thenReturn(info);
        when(track.getDuration()).thenReturn(duration.toMillis());
        return track;
    }
}
