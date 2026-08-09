package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

/**
 * Потокобезопасная очередь одной Discord-гильдии.
 */
@Slf4j
public class TrackScheduler extends AudioEventAdapter {

    private final AudioPlayer audioPlayer;
    private final LinkedBlockingDeque<TrackRequest> queue;
    private final Deque<TrackRequest> history = new ArrayDeque<>();
    private final int maxQueueSize;
    private final int maxHistorySize;
    private final long maxTrackDurationMillis;
    private final Runnable onActivity;
    private final Runnable onIdle;
    private final Diagnostics diagnostics;
    private final Consumer<TrackRequest> historyListener;
    private final Object mutationLock = new Object();
    private long queueRevision;

    private volatile TrackRequest currentRequest;
    private volatile long currentTrackStartedAtNanos;
    private volatile RepeatMode repeatMode = RepeatMode.OFF;

    public TrackScheduler(
            AudioPlayer audioPlayer,
            int maxQueueSize,
            Duration maxTrackDuration,
            Runnable onActivity,
            Runnable onIdle) {
        this(
                audioPlayer,
                maxQueueSize,
                maxTrackDuration,
                RepeatMode.OFF,
                onActivity,
                onIdle,
                Diagnostics.noop());
    }

    public TrackScheduler(
            AudioPlayer audioPlayer,
            int maxQueueSize,
            Duration maxTrackDuration,
            RepeatMode initialRepeatMode,
            Runnable onActivity,
            Runnable onIdle) {
        this(
                audioPlayer,
                maxQueueSize,
                maxTrackDuration,
                initialRepeatMode,
                onActivity,
                onIdle,
                Diagnostics.noop());
    }

    public TrackScheduler(
            AudioPlayer audioPlayer,
            int maxQueueSize,
            Duration maxTrackDuration,
            RepeatMode initialRepeatMode,
            Runnable onActivity,
            Runnable onIdle,
            Diagnostics diagnostics) {
        this(
                audioPlayer,
                maxQueueSize,
                maxTrackDuration,
                initialRepeatMode,
                onActivity,
                onIdle,
                diagnostics,
                ignored -> { });
    }

    public TrackScheduler(
            AudioPlayer audioPlayer,
            int maxQueueSize,
            Duration maxTrackDuration,
            RepeatMode initialRepeatMode,
            Runnable onActivity,
            Runnable onIdle,
            Diagnostics diagnostics,
            Consumer<TrackRequest> historyListener) {
        this.audioPlayer = Objects.requireNonNull(audioPlayer, "audioPlayer");
        if (maxQueueSize < 1) {
            throw new IllegalArgumentException("maxQueueSize must be positive");
        }
        this.maxQueueSize = maxQueueSize;
        this.maxHistorySize = Math.max(1, Math.min(maxQueueSize, 25));
        this.queue = new LinkedBlockingDeque<>(maxQueueSize + maxHistorySize);
        this.maxTrackDurationMillis = Objects.requireNonNull(maxTrackDuration, "maxTrackDuration").toMillis();
        this.repeatMode = Objects.requireNonNull(initialRepeatMode, "initialRepeatMode");
        this.onActivity = Objects.requireNonNull(onActivity, "onActivity");
        this.onIdle = Objects.requireNonNull(onIdle, "onIdle");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.historyListener = Objects.requireNonNull(historyListener, "historyListener");
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
                markCurrentTrackStarted();
                diagnostics.trackStarted(title(track));
                log.info("Track started: {} (requested by {})",
                        track.getInfo().title, request.requester().displayName());
                return new QueueResult(QueueStatus.STARTED, 0, 0L, request);
            }

            long estimatedWaitMillis = estimatedWaitMillisLocked();
            if (queue.size() >= maxQueueSize || !queue.offerLast(request)) {
                log.warn("Queue limit reached; rejected track: {}", track.getInfo().title);
                return new QueueResult(QueueStatus.QUEUE_FULL, queue.size(), estimatedWaitMillis, request);
            }

            markQueueMutationLocked();
            int position = queue.size();
            log.info("Track queued at position {}: {} (requested by {})",
                    position, track.getInfo().title, request.requester().displayName());
            return new QueueResult(QueueStatus.QUEUED, position, estimatedWaitMillis, request);
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (!isCurrentTrack(track)) {
            diagnostics.staleCallback("end:" + endReason, title(track));
            log.warn("Ignoring stale track-end callback: track={}, reason={}", title(track), endReason);
            return;
        }
        log.info("Track ended: {} (reason: {})", track.getInfo().title, endReason);
        long elapsedMillis = currentTrackElapsedMillis();
        if (endReason == AudioTrackEndReason.FINISHED
                && PrematureTrackEndPolicy.isPremature(track, elapsedMillis)) {
            String reason = PrematureTrackEndPolicy.diagnostic(track, elapsedMillis);
            diagnostics.sourceFailure(title(track), reason);
            log.warn("Track ended far before its advertised duration: track={}, {}",
                    title(track), reason);
            if (!startFallback(track, "premature finish")) {
                advanceToNext(false);
            }
            return;
        }
        if (endReason == AudioTrackEndReason.CLEANUP) {
            diagnostics.cleanup(title(track));
            log.warn("Track cleanup received for {}; keeping voice session alive and trying recovery",
                    track.getInfo().title);
            if (!startFallback(track, "cleanup")) {
                advanceToNext(false);
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
                markCurrentTrackStarted();
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
                } else {
                    markQueueMutationLocked();
                }
            }
        }
        advanceToNext(true);
    }

    public TrackRequest nextTrack() {
        return advanceToNext(true);
    }

    private TrackRequest advanceToNext(boolean rememberCurrent) {
        synchronized (mutationLock) {
            if (rememberCurrent) {
                rememberCurrentLocked();
            }
            TrackRequest next = queue.pollFirst();
            if (next == null) {
                currentRequest = null;
                audioPlayer.stopTrack();
                onIdle.run();
                return null;
            }

            markQueueMutationLocked();
            currentRequest = next;
            onActivity.run();
            audioPlayer.startTrack(next.track(), false);
            markCurrentTrackStarted();
            diagnostics.trackStarted(title(next.track()));
            log.info("Playing next track: {}", next.track().getInfo().title);
            return next;
        }
    }

    public PreviousResult previousTrack() {
        synchronized (mutationLock) {
            TrackRequest previous = history.pollFirst();
            if (previous == null) {
                return new PreviousResult(PreviousStatus.NO_HISTORY, null, false);
            }

            TrackRequest displaced = currentRequest;
            boolean returnedCurrentToQueue = false;
            if (displaced != null) {
                TrackRequest clone = cloneRequest(displaced);
                returnedCurrentToQueue = queue.offerFirst(clone);
                if (!returnedCurrentToQueue) {
                    history.addFirst(previous);
                    return new PreviousResult(PreviousStatus.QUEUE_CAPACITY_EXCEEDED, null, false);
                }
                markQueueMutationLocked();
            }

            TrackRequest restarted = cloneRequest(previous);
            currentRequest = restarted;
            onActivity.run();
            audioPlayer.startTrack(restarted.track(), false);
            markCurrentTrackStarted();
            diagnostics.trackStarted(title(restarted.track()));
            log.info("Playing previous track: {}", restarted.track().getInfo().title);
            return new PreviousResult(PreviousStatus.STARTED, restarted, returnedCurrentToQueue);
        }
    }

    public int historySize() {
        synchronized (mutationLock) {
            return history.size();
        }
    }

    public int clearQueue() {
        synchronized (mutationLock) {
            int removed = queue.size();
            queue.clear();
            if (removed > 0) {
                markQueueMutationLocked();
                onActivity.run();
            }
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
            markQueueMutationLocked();
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
            markQueueMutationLocked();
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
            markQueueMutationLocked();
            onActivity.run();
            return tracks.size();
        }
    }

    public QueueSnapshot queueSnapshot() {
        synchronized (mutationLock) {
            List<TrackRequest> tracks = List.copyOf(queue);
            Set<String> requesters = new HashSet<>();
            Set<String> uniqueTracks = new HashSet<>();
            int duplicates = 0;
            long totalDurationMillis = 0L;
            for (TrackRequest request : tracks) {
                TrackRequester requester = request.requester();
                requesters.add(requester.userId() > 0
                        ? "user:" + requester.userId()
                        : "name:" + requester.displayName());
                if (!uniqueTracks.add(queueIdentity(request))) {
                    duplicates++;
                }
                totalDurationMillis += safeDuration(request.track());
            }
            return new QueueSnapshot(
                    queueRevision,
                    tracks,
                    totalDurationMillis,
                    requesters.size(),
                    duplicates);
        }
    }

    public QueueStats queueStats() {
        QueueSnapshot snapshot = queueSnapshot();
        return new QueueStats(
                snapshot.revision(),
                snapshot.requests().size(),
                snapshot.totalDurationMillis(),
                snapshot.uniqueRequesters(),
                snapshot.duplicateCount());
    }

    public QueueMutationResult removeRange(
            int startOneBased,
            int endOneBased,
            OptionalLong expectedRevision) {
        synchronized (mutationLock) {
            QueueMutationResult stale = staleRevisionResult(expectedRevision);
            if (stale != null) {
                return stale;
            }
            List<TrackRequest> tracks = new ArrayList<>(queue);
            if (startOneBased < 1
                    || endOneBased < startOneBased
                    || endOneBased > tracks.size()) {
                return mutationResult(
                        QueueMutationStatus.INVALID_ARGUMENT,
                        List.of());
            }

            List<TrackRequest> removed = new ArrayList<>(
                    tracks.subList(startOneBased - 1, endOneBased));
            tracks.subList(startOneBased - 1, endOneBased).clear();
            replaceQueueLocked(tracks);
            markQueueMutationLocked();
            onActivity.run();
            return mutationResult(QueueMutationStatus.APPLIED, removed);
        }
    }

    public QueueMutationResult deduplicateQueue(OptionalLong expectedRevision) {
        synchronized (mutationLock) {
            QueueMutationResult stale = staleRevisionResult(expectedRevision);
            if (stale != null) {
                return stale;
            }
            List<TrackRequest> kept = new ArrayList<>();
            List<TrackRequest> removed = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (TrackRequest request : queue) {
                if (seen.add(queueIdentity(request))) {
                    kept.add(request);
                } else {
                    removed.add(request);
                }
            }
            if (removed.isEmpty()) {
                return mutationResult(QueueMutationStatus.NO_CHANGES, List.of());
            }
            replaceQueueLocked(kept);
            markQueueMutationLocked();
            onActivity.run();
            return mutationResult(QueueMutationStatus.APPLIED, removed);
        }
    }

    public QueueMutationResult removeRequesterAt(
            long requesterUserId,
            int oneBasedPosition,
            OptionalLong expectedRevision) {
        synchronized (mutationLock) {
            QueueMutationResult stale = staleRevisionResult(expectedRevision);
            if (stale != null) {
                return stale;
            }
            if (requesterUserId <= 0L || oneBasedPosition < 1 || oneBasedPosition > queue.size()) {
                return mutationResult(QueueMutationStatus.INVALID_ARGUMENT, List.of());
            }
            List<TrackRequest> tracks = new ArrayList<>(queue);
            TrackRequest candidate = tracks.get(oneBasedPosition - 1);
            if (candidate.requester().userId() != requesterUserId) {
                return mutationResult(QueueMutationStatus.NOT_OWNER, List.of());
            }
            TrackRequest removed = tracks.remove(oneBasedPosition - 1);
            replaceQueueLocked(tracks);
            markQueueMutationLocked();
            onActivity.run();
            return mutationResult(QueueMutationStatus.APPLIED, List.of(removed));
        }
    }

    public QueueMutationResult removeRequester(
            long requesterUserId,
            OptionalLong expectedRevision) {
        synchronized (mutationLock) {
            QueueMutationResult stale = staleRevisionResult(expectedRevision);
            if (stale != null) {
                return stale;
            }
            if (requesterUserId <= 0L) {
                return mutationResult(QueueMutationStatus.INVALID_ARGUMENT, List.of());
            }
            List<TrackRequest> kept = new ArrayList<>();
            List<TrackRequest> removed = new ArrayList<>();
            for (TrackRequest request : queue) {
                if (request.requester().userId() == requesterUserId) {
                    removed.add(request);
                } else {
                    kept.add(request);
                }
            }
            if (removed.isEmpty()) {
                return mutationResult(QueueMutationStatus.NO_CHANGES, List.of());
            }
            replaceQueueLocked(kept);
            markQueueMutationLocked();
            onActivity.run();
            return mutationResult(QueueMutationStatus.APPLIED, removed);
        }
    }

    private QueueMutationResult staleRevisionResult(OptionalLong expectedRevision) {
        OptionalLong safeExpected = expectedRevision == null ? OptionalLong.empty() : expectedRevision;
        if (safeExpected.isPresent() && safeExpected.getAsLong() != queueRevision) {
            return mutationResult(QueueMutationStatus.STALE_REVISION, List.of());
        }
        return null;
    }

    private QueueMutationResult mutationResult(
            QueueMutationStatus status,
            List<TrackRequest> removed) {
        List<TrackRequest> safeRemoved = List.copyOf(removed);
        long removedDurationMillis = safeRemoved.stream()
                .mapToLong(request -> safeDuration(request.track()))
                .sum();
        return new QueueMutationResult(
                status,
                safeRemoved.size(),
                removedDurationMillis,
                queue.size(),
                queueRevision,
                safeRemoved);
    }

    private static String queueIdentity(TrackRequest request) {
        AudioTrack track = request.track();
        String identifier = track.getIdentifier();
        if (identifier != null && !identifier.isBlank()) {
            return "id:" + identifier.trim();
        }
        if (track.getInfo() != null) {
            String uri = track.getInfo().uri;
            if (uri != null && !uri.isBlank()) {
                return "uri:" + uri.trim();
            }
            return "meta:"
                    + Objects.toString(track.getInfo().title, "") + '\u0000'
                    + Objects.toString(track.getInfo().author, "") + '\u0000'
                    + safeDuration(track);
        }
        return "track:" + System.identityHashCode(track);
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

    public long queueRevision() {
        synchronized (mutationLock) {
            return queueRevision;
        }
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
        if (!isCurrentTrack(track)) {
            diagnostics.staleCallback("exception", title(track));
            log.warn("Ignoring stale track-exception callback: track={}", title(track));
            return;
        }
        String reason = SourceFailureFormatter.describe(track, exception);
        diagnostics.sourceFailure(title(track), reason);
        log.error("Track exception for {}: {}", track.getInfo().title, reason, exception);
        if (!startFallback(track, "playback exception")) {
            advanceToNext(false);
        }
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        if (!isCurrentTrack(track)) {
            diagnostics.staleCallback("stuck", title(track));
            log.warn("Ignoring stale track-stuck callback: track={}", title(track));
            return;
        }
        diagnostics.sourceFailure(title(track), "stuck for " + thresholdMs + "ms");
        log.error("Track stuck: {} (threshold: {}ms)", track.getInfo().title, thresholdMs);
        if (!startFallback(track, "stuck track")) {
            advanceToNext(false);
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
            markCurrentTrackStarted();
            diagnostics.fallback(title(failedTrack), title(fallback.track()));
            diagnostics.trackStarted(title(fallback.track()));
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

    private void rememberCurrentLocked() {
        TrackRequest current = currentRequest;
        if (current == null) {
            return;
        }
        TrackRequest remembered = cloneRequest(current);
        history.addFirst(remembered);
        while (history.size() > maxHistorySize) {
            history.removeLast();
        }
        try {
            historyListener.accept(remembered);
        } catch (RuntimeException exception) {
            log.error("Persistent history listener failed for track {}; playback will continue",
                    title(remembered.track()),
                    exception);
        }
    }

    private static TrackRequest cloneRequest(TrackRequest request) {
        return request.withTrack(request.track().makeClone());
    }

    private void markQueueMutationLocked() {
        queueRevision++;
    }

    private void replaceQueueLocked(List<TrackRequest> tracks) {
        queue.clear();
        for (TrackRequest track : tracks) {
            if (!queue.offerLast(track)) {
                throw new IllegalStateException("Queue capacity changed during mutation");
            }
        }
    }

    private void markCurrentTrackStarted() {
        currentTrackStartedAtNanos = System.nanoTime();
    }

    private long currentTrackElapsedMillis() {
        long startedAt = currentTrackStartedAtNanos;
        if (startedAt <= 0L) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static long safeDuration(AudioTrack track) {
        return Math.max(0L, track.getDuration());
    }

    private boolean isCurrentTrack(AudioTrack track) {
        TrackRequest current = currentRequest;
        return current != null && current.track() == track;
    }

    private static String title(AudioTrack track) {
        if (track == null || track.getInfo() == null || track.getInfo().title == null) {
            return "unknown track";
        }
        return track.getInfo().title;
    }

    public interface Diagnostics {
        void trackStarted(String title);

        void sourceFailure(String title, String reason);

        void cleanup(String title);

        void fallback(String fromTitle, String toTitle);

        void staleCallback(String callback, String title);

        static Diagnostics noop() {
            return new Diagnostics() {
                @Override
                public void trackStarted(String title) {
                }

                @Override
                public void sourceFailure(String title, String reason) {
                }

                @Override
                public void cleanup(String title) {
                }

                @Override
                public void fallback(String fromTitle, String toTitle) {
                }

                @Override
                public void staleCallback(String callback, String title) {
                }
            };
        }
    }

    public enum PreviousStatus {
        STARTED,
        NO_HISTORY,
        QUEUE_CAPACITY_EXCEEDED
    }

    public record PreviousResult(
            PreviousStatus status,
            TrackRequest request,
            boolean returnedCurrentToQueue) {
    }

    public enum QueueMutationStatus {
        APPLIED,
        NO_CHANGES,
        STALE_REVISION,
        INVALID_ARGUMENT,
        NOT_OWNER
    }

    public record QueueSnapshot(
            long revision,
            List<TrackRequest> requests,
            long totalDurationMillis,
            int uniqueRequesters,
            int duplicateCount) {

        public QueueSnapshot {
            requests = List.copyOf(requests);
        }
    }

    public record QueueStats(
            long revision,
            int size,
            long totalDurationMillis,
            int uniqueRequesters,
            int duplicateCount) {
    }

    public record QueueMutationResult(
            QueueMutationStatus status,
            int removedCount,
            long removedDurationMillis,
            int queueSize,
            long revision,
            List<TrackRequest> removed) {

        public QueueMutationResult {
            removed = List.copyOf(removed);
        }
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
