package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.Objects;

/**
 * Трек вместе с данными пользователя, который его заказал.
 */
public record TrackRequest(AudioTrack track, TrackRequester requester, long requestedAtEpochMillis) {

    public TrackRequest {
        Objects.requireNonNull(track, "track");
        requester = requester == null ? TrackRequester.unknown() : requester;
    }

    public static TrackRequest create(AudioTrack track, TrackRequester requester) {
        return new TrackRequest(track, requester, System.currentTimeMillis());
    }

    public TrackRequest withTrack(AudioTrack replacement) {
        return new TrackRequest(replacement, requester, requestedAtEpochMillis);
    }
}
