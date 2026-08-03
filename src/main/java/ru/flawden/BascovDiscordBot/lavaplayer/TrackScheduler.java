package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Потокобезопасная очередь одной Discord-гильдии.
 */
@Slf4j
public class TrackScheduler extends AudioEventAdapter {

    private final AudioPlayer audioPlayer;
    private final BlockingQueue<AudioTrack> queue;
    private final long maxTrackDurationMillis;
    private final Runnable onActivity;
    private final Runnable onIdle;

    public TrackScheduler(
            AudioPlayer audioPlayer,
            int maxQueueSize,
            Duration maxTrackDuration,
            Runnable onActivity,
            Runnable onIdle) {
        this.audioPlayer = Objects.requireNonNull(audioPlayer, "audioPlayer");
        this.queue = new LinkedBlockingQueue<>(maxQueueSize);
        this.maxTrackDurationMillis = Objects.requireNonNull(maxTrackDuration, "maxTrackDuration").toMillis();
        this.onActivity = Objects.requireNonNull(onActivity, "onActivity");
        this.onIdle = Objects.requireNonNull(onIdle, "onIdle");
    }

    public QueueResult queue(AudioTrack track) {
        Objects.requireNonNull(track, "track");

        if (track.getInfo().isStream) {
            return new QueueResult(QueueStatus.STREAM_NOT_ALLOWED, queue.size());
        }
        if (track.getDuration() <= 0 || track.getDuration() > maxTrackDurationMillis) {
            return new QueueResult(QueueStatus.TRACK_TOO_LONG, queue.size());
        }

        onActivity.run();
        if (audioPlayer.startTrack(track, true)) {
            log.info("Track started: {}", track.getInfo().title);
            return new QueueResult(QueueStatus.STARTED, 0);
        }

        if (!queue.offer(track)) {
            log.warn("Queue limit reached; rejected track: {}", track.getInfo().title);
            return new QueueResult(QueueStatus.QUEUE_FULL, queue.size());
        }

        int position = queue.size();
        log.info("Track queued at position {}: {}", position, track.getInfo().title);
        return new QueueResult(QueueStatus.QUEUED, position);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        log.info("Track ended: {} (reason: {})", track.getInfo().title, endReason);
        if (endReason.mayStartNext) {
            nextTrack();
        }
    }

    public AudioTrack nextTrack() {
        AudioTrack next = queue.poll();
        if (next == null) {
            audioPlayer.stopTrack();
            onIdle.run();
            return null;
        }

        onActivity.run();
        audioPlayer.startTrack(next, false);
        log.info("Playing next track: {}", next.getInfo().title);
        return next;
    }

    public void clearQueue() {
        queue.clear();
    }

    public List<AudioTrack> queuedTracks() {
        return List.copyOf(queue);
    }

    public int queueSize() {
        return queue.size();
    }

    public boolean isIdle() {
        return audioPlayer.getPlayingTrack() == null && queue.isEmpty();
    }

    public void scheduleDisconnectIfIdle() {
        if (isIdle()) {
            onIdle.run();
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        log.error("Track exception for {}: {}", track.getInfo().title, exception.getMessage(), exception);
        nextTrack();
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        log.error("Track stuck: {} (threshold: {}ms)", track.getInfo().title, thresholdMs);
        nextTrack();
    }

    public enum QueueStatus {
        STARTED,
        QUEUED,
        QUEUE_FULL,
        TRACK_TOO_LONG,
        STREAM_NOT_ALLOWED
    }

    public record QueueResult(QueueStatus status, int queuePosition) {
    }
}
