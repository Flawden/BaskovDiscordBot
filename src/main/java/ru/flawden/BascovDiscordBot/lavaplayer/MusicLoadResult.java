package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * Результат асинхронной загрузки трека, независимый от конкретного Discord transport.
 */
public record MusicLoadResult(
        Status status,
        AudioTrack track,
        int queuePosition,
        long estimatedWaitMillis,
        TrackRequester requester) {

    public MusicLoadResult {
        requester = requester == null ? TrackRequester.unknown() : requester;
    }

    public enum Status {
        STARTED,
        QUEUED,
        QUEUE_FULL,
        TRACK_TOO_LONG,
        STREAM_NOT_ALLOWED,
        NO_MATCHES,
        LOAD_FAILED,
        SESSION_CLOSED
    }

    public static MusicLoadResult of(
            Status status,
            AudioTrack track,
            int queuePosition,
            long estimatedWaitMillis,
            TrackRequester requester) {
        return new MusicLoadResult(status, track, queuePosition, estimatedWaitMillis, requester);
    }

    public static MusicLoadResult withoutTrack(Status status) {
        return new MusicLoadResult(status, null, 0, 0L, TrackRequester.unknown());
    }
}
