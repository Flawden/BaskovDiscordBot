package ru.flawden.BascovDiscordBot.session;

import ru.flawden.BascovDiscordBot.library.StoredTrack;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequest;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequester;

import java.util.Objects;
import java.util.Optional;

/**
 * Воспроизводимый трек checkpoint с позицией внутри текущей композиции.
 */
public record StoredSessionTrack(
        StoredTrack track,
        long positionMillis) {

    public StoredSessionTrack {
        track = Objects.requireNonNull(track, "track");
        if (positionMillis < 0L) {
            throw new IllegalArgumentException("positionMillis cannot be negative");
        }
    }

    public static Optional<StoredSessionTrack> from(TrackRequest request, long positionMillis) {
        return StoredTrack.from(request)
                .map(track -> new StoredSessionTrack(track, Math.max(0L, positionMillis)));
    }

    public TrackRequester requester() {
        return new TrackRequester(track.requesterUserId(), track.requesterDisplayName());
    }

    public long safeResumePositionMillis() {
        long duration = track.durationMillis();
        if (duration <= 1_000L) {
            return 0L;
        }
        return Math.min(positionMillis, duration - 1_000L);
    }
}
