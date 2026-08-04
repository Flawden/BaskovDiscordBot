package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;
import java.util.Objects;

/**
 * Трек вместе с данными пользователя и ограниченным списком резервных
 * результатов того же поискового запроса.
 */
public record TrackRequest(
        AudioTrack track,
        TrackRequester requester,
        long requestedAtEpochMillis,
        List<AudioTrack> fallbackTracks) {

    public TrackRequest {
        Objects.requireNonNull(track, "track");
        requester = requester == null ? TrackRequester.unknown() : requester;
        fallbackTracks = fallbackTracks == null ? List.of() : List.copyOf(fallbackTracks);
    }

    public static TrackRequest create(AudioTrack track, TrackRequester requester) {
        return create(track, requester, List.of());
    }

    public static TrackRequest create(
            AudioTrack track,
            TrackRequester requester,
            List<AudioTrack> fallbackTracks) {
        return new TrackRequest(track, requester, System.currentTimeMillis(), fallbackTracks);
    }

    public TrackRequest withTrack(AudioTrack replacement) {
        return new TrackRequest(replacement, requester, requestedAtEpochMillis, List.of());
    }

    public TrackRequest advanceToFallback() {
        if (fallbackTracks.isEmpty()) {
            return null;
        }
        return new TrackRequest(
                fallbackTracks.get(0),
                requester,
                requestedAtEpochMillis,
                fallbackTracks.subList(1, fallbackTracks.size()));
    }
}
