package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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
    private final BlockingQueue<TrackRequest> queue;
    private final long maxTrackDurationMillis;
    private final Runnable onActivity;
    private final Runnable onIdle;
    private final Object mutationLock = new Object();

    private volatile TrackRequest currentRequest;
    private volatile RepeatMode repeatMode = RepeatMode.OFF;

    public TrackScheduler(
            AudioPlayer audioPlayer,
            int maxQueueSize,
            Duration maxTrackDuration,
            Runnable onActivity,
            Runnable onIdle) {
        this(audioPlayer, maxQueueSize, maxTrackDuration, RepeatMode.OFF, onActivity, onIdle);
    }

    public TrackScheduler(
            AudioPlayer audioPlayer,
            int maxQueueSize,
            Duration maxTrackDuration,
            RepeatMode initialRepeatMode,
            Runnable onActivity,
            Runnable onIdle) {
        this.audioPlayer = Objects.requireNonNull(audioPlayer, "audioPlayer");
        this.queue = new LinkedBlockingQueue<>(maxQueueSize);
        this.maxTrackDurationMillis = Objects.requireNonNull(maxTrackDuration, "maxTrackDuration").toMillis();
        this.repeatMode = Objects.requireNonNull(initialRepeatMode, "initialRepeatMode");
        this.onActivity = Objects.requireNonNull(onActivity, "onActivity");
        this.onIdle = Objects.requireNonNull(onIdle, "onIdle");
    }

    public QueueResult queue(AudioTrack track) {
        return queue(track, TrackRequester.unknown());
    }

    public QueueResult queue(AudioTrack track, TrackRequester requester) {
        return queue(track, requester, List.of());
    }

    public QueueResult queue(
            AudioTrack track,
            TrackRequester requester,
            List<AudioTrack> fallbackTracks) {
        Objects.requireNonNull(track, "track");

        if (track.getInfo().isStream) {
            return new QueueResult(QueueStatus.STREAM_NOT_ALLOWED, queue.size(), 0L, null);
        }
        if (track.getDuration() <= 0 || track.getDuration() > maxTrackDurationMillis) {
            return new QueueResult(QueueStatus.TRACK_TOO_LONG, queue.size(), 0L, null);
        }

        TrackRequest request = TrackRequest.create(track, requester, fallbackTracks);
        onActivity.run();
        synchronized (mutationLock) {
            if (audioPlayer.startTrack(track, true)) {
                currentRequest = request;
                log.info("Track started: {} (requested by {})",
                        track.getInfo().title, request.requester().displayName());
                return new QueueResult(QueueStatus.STARTED, 0, 0L, request);
            }

            long estimatedWaitMillis = estimatedWaitMillisLocked();
            if (!queue.offer(request)) {
                log.warn("Queue limit reached; rejected track: {}", track.getInfo().title);
                return new QueueResult(QueueStatus.QUEUE_FULL, queue.size(), estimatedWaitMillis, request);
            }

            int position = queue.size();
            log.info("Track queued at position {}: {} (requested by {})",
                    position, track.getInfo().title, request.requester().displayName());
            return new QueueResult(QueueStatus.QUEUED, position, estimatedWaitMillis, request);
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        log.info("Track ended: {} (reason: {})", track.getInfo().title, endReason);
        if (endReason == AudioTrackEndReason.CLEANUP) {
            log.warn("Track cleanup received for {}; keeping voice session alive and trying recovery",
                    track.getInfo().title);
            if (!startFallback(track, "cleanup")) {
                nextTrack();
            }
            return;
        }
        if (!endReason.mayStartNext) {
            return;
        }

        synchronized (mutationLock) {
            if (endReason == AudioTrackEndReason.FINISHED && repeatMode == RepeatMode.TRACK
                    && currentRequest != null) {
                AudioTrack clone = track.makeClone();
                currentRequest = currentRequest.withTrack(clone);
                onActivity.run();
                audioPlayer.startTrack(clone, false);
                log.info("Repeating current track: {}", track.getInfo().title);
                return;
            }

            if (endReason == AudioTrackEndReason.FINISHED && repeatMode == RepeatMode.QUEUE
                    && currentRequest != null) {
                AudioTrack clone = track.makeClone();
                TrackRequest repeated = currentRequest.withTrack(clone);
                if (!queue.offer(repeated)) {
                    log.warn("Could not append repeated track because queue is full: {}",
                            track.getInfo().title);
                }
            }
        }
        nextTrack();
    }

    public TrackRequest nextTrack() {
        synchronized (mutationLock) {
            TrackRequest next = queue.poll();
            if (next == null) {
                currentRequest = null;
                audioPlayer.stopTrack();
                onIdle.run();
                return null;
            }

            currentRequest = next;
            onActivity.run();
            audioPlayer.startTrack(next.track(), false);
            log.info("Playing next track: {}", next.track().getInfo().title);
            return next;
        }
    }

    public int clearQueue() {
        synchronized (mutationLock) {
            int removed = queue.size();
            queue.clear();
            return removed;
        }
    }

    public TrackRequest removeAt(int oneBasedPosition) {
        synchronized (mutationLock) {
            List<TrackRequest> tracks = new ArrayList<>(queue);
            int index = oneBasedPosition - 1;
            if (index < 0 || index >= tracks.size()) {
                return null;
            }
            TrackRequest removed = tracks.remove(index);
            replaceQueueLocked(tracks);
            onActivity.run();
            return removed;
        }
    }

    public boolean move(int fromOneBased, int toOneBased) {
        synchronized (mutationLock) {
            List<TrackRequest> tracks = new ArrayList<>(queue);
            int from = fromOneBased - 1;
            int to = toOneBased - 1;
            if (from < 0 || from >= tracks.size() || to < 0 || to >= tracks.size()) {
                return false;
            }
            if (from == to) {
                return true;
            }
            TrackRequest moved = tracks.remove(from);
            tracks.add(to, moved);
            replaceQueueLocked(tracks);
            onActivity.run();
            return true;
        }
    }

    public int shuffleQueue() {
        synchronized (mutationLock) {
            List<TrackRequest> tracks = new ArrayList<>(queue);
            if (tracks.size() < 2) {
                return tracks.size();
            }
            Collections.shuffle(tracks);
            replaceQueueLocked(tracks);
            onActivity.run();
            return tracks.size();
        }
    }

    public RepeatMode setRepeatMode(RepeatMode mode) {
        repeatMode = Objects.requireNonNull(mode, "mode");
        onActivity.run();
        return repeatMode;
    }

    public RepeatMode cycleRepeatMode() {
        return setRepeatMode(repeatMode.next());
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public TrackRequest getCurrentRequest() {
        return currentRequest;
    }

    public List<TrackRequest> queuedRequests() {
        return List.copyOf(queue);
    }

    public List<AudioTrack> queuedTracks() {
        return queue.stream().map(TrackRequest::track).toList();
    }

    public int queueSize() {
        return queue.size();
    }

    public long estimatedWaitMillis() {
        synchronized (mutationLock) {
            return estimatedWaitMillisLocked();
        }
    }

    public long totalQueuedDurationMillis() {
        return queue.stream().mapToLong(request -> safeDuration(request.track())).sum();
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
        if (!startFallback(track, "playback exception")) {
            nextTrack();
        }
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        log.error("Track stuck: {} (threshold: {}ms)", track.getInfo().title, thresholdMs);
        if (!startFallback(track, "stuck track")) {
            nextTrack();
        }
    }

    private boolean startFallback(AudioTrack failedTrack, String reason) {
        synchronized (mutationLock) {
            TrackRequest failed = currentRequest;
            if (failed == null || failed.track() != failedTrack) {
                return false;
            }
            TrackRequest fallback = failed.advanceToFallback();
            if (fallback == null) {
                return false;
            }
            currentRequest = fallback;
            onActivity.run();
            audioPlayer.startTrack(fallback.track(), false);
            log.warn("Primary search result failed ({}); trying fallback: {} -> {}",
                    reason,
                    failedTrack.getInfo().title,
                    fallback.track().getInfo().title);
            return true;
        }
    }

    private long estimatedWaitMillisLocked() {
        long wait = 0L;
        AudioTrack playing = audioPlayer.getPlayingTrack();
        if (playing != null) {
            wait += Math.max(0L, safeDuration(playing) - Math.max(0L, playing.getPosition()));
        }
        wait += queue.stream().mapToLong(request -> safeDuration(request.track())).sum();
        return wait;
    }

    private void replaceQueueLocked(List<TrackRequest> tracks) {
        queue.clear();
        for (TrackRequest track : tracks) {
            if (!queue.offer(track)) {
                throw new IllegalStateException("Queue capacity changed during mutation");
            }
        }
    }

    private static long safeDuration(AudioTrack track) {
        return Math.max(0L, track.getDuration());
    }

    public enum QueueStatus {
        STARTED,
        QUEUED,
        QUEUE_FULL,
        TRACK_TOO_LONG,
        STREAM_NOT_ALLOWED
    }

    public record QueueResult(
            QueueStatus status,
            int queuePosition,
            long estimatedWaitMillis,
            TrackRequest request) {
    }
}
