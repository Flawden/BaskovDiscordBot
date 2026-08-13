package ru.flawden.BascovDiscordBot.library;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import ru.flawden.BascovDiscordBot.catalog.TrackCatalogEntry;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequest;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackRequester;

import java.util.Objects;
import java.util.Optional;

/**
 * Небольшое устойчивое к перезапускам описание трека.
 *
 * <p>В файл не сериализуется сам {@link AudioTrack}: для повторной загрузки
 * сохраняется только публичный URL/identifier и безопасные метаданные.</p>
 */
public record StoredTrack(
        String title,
        String author,
        String playbackIdentifier,
        String sourceIdentifier,
        MediaProvider provider,
        long durationMillis,
        long requesterUserId,
        String requesterDisplayName,
        long capturedAtEpochMillis) {

    private static final int MAX_TITLE_LENGTH = 180;
    private static final int MAX_AUTHOR_LENGTH = 120;
    private static final int MAX_IDENTIFIER_LENGTH = 2_048;
    private static final int MAX_REQUESTER_LENGTH = 100;

    public StoredTrack {
        title = requiredText(title, "title", MAX_TITLE_LENGTH);
        author = optionalText(author, "Неизвестно", MAX_AUTHOR_LENGTH);
        playbackIdentifier = requiredText(
                playbackIdentifier,
                "playbackIdentifier",
                MAX_IDENTIFIER_LENGTH);
        sourceIdentifier = optionalText(sourceIdentifier, "", MAX_IDENTIFIER_LENGTH);
        provider = Objects.requireNonNullElse(provider, MediaProvider.UNKNOWN);
        if (durationMillis <= 0L) {
            throw new IllegalArgumentException("durationMillis must be positive");
        }
        if (requesterUserId < 0L) {
            throw new IllegalArgumentException("requesterUserId cannot be negative");
        }
        requesterDisplayName = optionalText(
                requesterDisplayName,
                "Неизвестно",
                MAX_REQUESTER_LENGTH);
        if (capturedAtEpochMillis <= 0L) {
            throw new IllegalArgumentException("capturedAtEpochMillis must be positive");
        }
    }

    public TrackIdentity trackIdentity() {
        return TrackIdentity.of(author, title);
    }

    public TrackCatalogEntry catalogEntry() {
        return TrackCatalogEntry.of(trackIdentity());
    }

    public static Optional<StoredTrack> from(TrackRequest request) {
        if (request == null || request.track() == null) {
            return Optional.empty();
        }
        TrackRequester requester = request.requester() == null
                ? TrackRequester.unknown()
                : request.requester();
        return from(
                request.track(),
                Math.max(0L, requester.userId()),
                requester.displayName());
    }

    /** Builds the same durable replay descriptor for non-Discord clients such as BaskovAndroid. */
    public static Optional<StoredTrack> from(
            AudioTrack track,
            long requesterUserId,
            String requesterDisplayName) {
        if (track == null || track.getInfo() == null) {
            return Optional.empty();
        }
        AudioTrackInfo info = track.getInfo();
        String playbackIdentifier = firstReplayableIdentifier(info.uri, info.identifier);
        if (playbackIdentifier == null || track.getDuration() <= 0L) {
            return Optional.empty();
        }

        MediaProvider provider = MediaProvider.fromUri(playbackIdentifier);
        if (provider == MediaProvider.UNKNOWN) {
            provider = MediaProvider.fromIdentifier(info.identifier);
        }
        if (provider != MediaProvider.YOUTUBE && provider != MediaProvider.SOUNDCLOUD) {
            return Optional.empty();
        }

        return Optional.of(new StoredTrack(
                info.title,
                info.author,
                playbackIdentifier,
                info.identifier,
                provider,
                track.getDuration(),
                Math.max(0L, requesterUserId),
                requesterDisplayName,
                System.currentTimeMillis()));
    }

    private static String firstReplayableIdentifier(String uri, String identifier) {
        MediaProvider uriProvider = MediaProvider.fromUri(uri);
        if (uriProvider == MediaProvider.YOUTUBE || uriProvider == MediaProvider.SOUNDCLOUD) {
            return uri == null ? null : uri.trim();
        }
        MediaProvider identifierProvider = MediaProvider.fromUri(identifier);
        if (identifierProvider == MediaProvider.YOUTUBE
                || identifierProvider == MediaProvider.SOUNDCLOUD) {
            return identifier == null ? null : identifier.trim();
        }
        return null;
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength);
        }
        return normalized;
    }

    private static String optionalText(String value, String fallback, int maxLength) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        if (normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength);
        }
        return normalized;
    }
}
