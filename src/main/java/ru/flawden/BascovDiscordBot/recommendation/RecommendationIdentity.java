package ru.flawden.BascovDiscordBot.recommendation;

import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

/**
 * Backward-compatible facade for the pre-catalog recommendation identity API.
 *
 * <p>New code should use {@link TrackIdentity}. Keeping this facade preserves
 * persisted feedback/novelty keys and avoids a format migration in v1.25.</p>
 */
@Deprecated(forRemoval = false)
public final class RecommendationIdentity {

    private RecommendationIdentity() {
    }

    public static String of(StoredTrack track) {
        return track == null ? "unknown" : track.trackIdentity().stableKey();
    }

    public static String of(String artist, String title) {
        return TrackIdentity.of(artist, title).stableKey();
    }

    public static String normalizeArtist(String artist) {
        return TrackIdentity.normalizeArtist(artist);
    }
}
