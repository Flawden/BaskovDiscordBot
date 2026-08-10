package ru.flawden.BascovDiscordBot.catalog;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackIdentityTest {

    @Test
    void stableKeyIgnoresCasePunctuationSpacingAndDiacritics() {
        TrackIdentity left = TrackIdentity.of("  Beyoncé ", "Halo!!!");
        TrackIdentity right = TrackIdentity.of("BEYONCE", "  Halo ");

        assertEquals(left.stableKey(), right.stableKey());
        assertEquals("beyonce::halo", left.stableKey());
    }

    @Test
    void nullMetadataKeepsLegacyUnknownIdentity() {
        assertEquals("unknown::unknown", TrackIdentity.of(null, null).stableKey());
    }

    @Test
    void meaningfulTitleVersionRemainsDistinct() {
        TrackIdentity studio = TrackIdentity.of("Linkin Park", "Numb");
        TrackIdentity live = TrackIdentity.of("Linkin Park", "Numb Live");

        assertFalse(studio.sameLogicalTrack(live));
    }

    @Test
    void sameTrackIsIndependentFromPlaybackProvider() {
        StoredTrack youtube = stored("https://youtube.com/watch?v=abc", MediaProvider.YOUTUBE);
        StoredTrack soundcloud = stored("https://soundcloud.com/example/numb", MediaProvider.SOUNDCLOUD);

        assertTrue(youtube.trackIdentity().sameLogicalTrack(soundcloud.trackIdentity()));
        assertEquals(youtube.trackIdentity().stableKey(), soundcloud.trackIdentity().stableKey());
    }

    @Test
    void displayMetadataIsKeptSeparateFromNormalizedKey() {
        TrackIdentity identity = TrackIdentity.of(" Linkin   Park ", " Numb ");

        assertEquals("Linkin Park", identity.artist());
        assertEquals("Numb", identity.title());
        assertEquals("linkin park", identity.normalizedArtist());
        assertEquals("numb", identity.normalizedTitle());
    }

    private static StoredTrack stored(String playbackIdentifier, MediaProvider provider) {
        return new StoredTrack(
                "Numb",
                "Linkin Park",
                playbackIdentifier,
                "provider-id",
                provider,
                180_000L,
                42L,
                "Tester",
                1_000L);
    }
}
