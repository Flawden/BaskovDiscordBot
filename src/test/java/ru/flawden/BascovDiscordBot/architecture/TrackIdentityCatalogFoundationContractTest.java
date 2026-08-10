package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackIdentityCatalogFoundationContractTest {

    @Test
    void trackIdentityIsProviderAndClientNeutral() throws Exception {
        String identity = read("catalog/TrackIdentity.java");

        assertTrue(identity.contains("record TrackIdentity"));
        assertTrue(identity.contains("String normalizedArtist"));
        assertTrue(identity.contains("String normalizedTitle"));
        assertTrue(identity.contains("stableKey()"));
        assertFalse(identity.contains("MediaProvider"));
        assertFalse(identity.contains("AudioTrack"));
        assertFalse(identity.contains("net.dv8tion"));
        assertFalse(identity.contains("youtube"));
        assertFalse(identity.contains("soundcloud"));
    }

    @Test
    void catalogIdsRepresentRecordingIdentityNotPlaybackProviders() throws Exception {
        String externalId = read("catalog/TrackExternalId.java");

        assertTrue(externalId.contains("MUSICBRAINZ_RECORDING"));
        assertTrue(externalId.contains("ISRC"));
        assertFalse(externalId.contains("YOUTUBE"));
        assertFalse(externalId.contains("SOUNDCLOUD"));
        assertFalse(externalId.contains("playbackIdentifier"));
    }

    @Test
    void storedAndRecommendedTracksExposeCatalogIdentityWithoutStorageMigration() throws Exception {
        String stored = read("library/StoredTrack.java");
        String candidate = read("recommendation/RecommendationCandidate.java");
        String libraryRepo = read("library/FileMusicLibraryRepository.java");

        assertTrue(stored.contains("TrackIdentity trackIdentity()"));
        assertTrue(stored.contains("TrackCatalogEntry catalogEntry()"));
        assertTrue(candidate.contains("TrackIdentity trackIdentity()"));
        assertTrue(candidate.contains("TrackCatalogEntry catalogEntry()"));
        assertTrue(libraryRepo.contains("track.playbackIdentifier()"));
        assertFalse(libraryRepo.contains("BASKOV_MUSIC_LIBRARY_V4"));
    }

    @Test
    void legacyRecommendationIdentityDelegatesToCatalogStableKey() throws Exception {
        String legacy = read("recommendation/RecommendationIdentity.java");

        assertTrue(legacy.contains("TrackIdentity.of(artist, title).stableKey()"));
        assertTrue(legacy.contains("track.trackIdentity().stableKey()"));
        assertTrue(legacy.contains("@Deprecated"));
    }

    @Test
    void lastFmCanCarryMusicBrainzRecordingIdWithoutOwningPlayback() throws Exception {
        String provider = read("recommendation/LastFmRecommendationProvider.java");
        String candidate = read("recommendation/RecommendationCandidate.java");

        assertTrue(provider.contains("TrackExternalId.musicBrainzRecording"));
        assertTrue(provider.contains("node.path(\"mbid\")"));
        assertTrue(candidate.contains("Set<TrackExternalId> externalIds"));
        assertFalse(candidate.contains("playbackIdentifier"));
        assertFalse(candidate.contains("ytsearch:"));
        String engine = read("recommendation/SmartDiscoveryEngine.java");
        assertTrue(engine.contains("candidate.externalIds()"));
    }

    @Test
    void v125DoesNotPrematurelyIntroducePlaybackResolverOrNewPersistence() throws Exception {
        String readme = Files.readString(Path.of("Readme.md"));
        String tree = Files.readString(Path.of("docs/TRACK-IDENTITY-CATALOG.md"));

        assertTrue(readme.contains("Track Identity & Catalog Foundation"));
        assertTrue(tree.contains("PlaybackResolver"));
        assertTrue(tree.contains("v1.26"));
        assertFalse(Files.exists(Path.of("src/main/java/ru/flawden/BascovDiscordBot/playback/PlaybackResolver.java")));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/flawden/BascovDiscordBot").resolve(relative));
    }
}
