package ru.flawden.BascovDiscordBot.catalog;

import java.util.Locale;
import java.util.Objects;

/**
 * Catalog identifier that helps disambiguate a recording without coupling it to playback transport.
 */
public record TrackExternalId(Namespace namespace, String value) {

    public enum Namespace {
        MUSICBRAINZ_RECORDING,
        ISRC
    }

    public TrackExternalId {
        namespace = Objects.requireNonNull(namespace, "namespace");
        value = normalize(namespace, value);
    }

    public static TrackExternalId musicBrainzRecording(String mbid) {
        return new TrackExternalId(Namespace.MUSICBRAINZ_RECORDING, mbid);
    }

    public static TrackExternalId isrc(String isrc) {
        return new TrackExternalId(Namespace.ISRC, isrc);
    }

    private static String normalize(Namespace namespace, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("external id cannot be blank");
        }
        String safe = value.trim();
        if (safe.length() > 128) {
            throw new IllegalArgumentException("external id is too long");
        }
        return switch (namespace) {
            case MUSICBRAINZ_RECORDING -> safe.toLowerCase(Locale.ROOT);
            case ISRC -> safe.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        };
    }
}
