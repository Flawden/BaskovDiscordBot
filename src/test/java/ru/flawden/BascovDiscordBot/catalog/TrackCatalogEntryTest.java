package ru.flawden.BascovDiscordBot.catalog;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackCatalogEntryTest {

    @Test
    void mergeCombinesCatalogIdsAndTagsForSameLogicalTrack() {
        TrackIdentity identity = TrackIdentity.of("Sum 41", "Fat Lip");
        TrackCatalogEntry left = new TrackCatalogEntry(
                identity,
                Set.of(TrackExternalId.musicBrainzRecording("550e8400-e29b-41d4-a716-446655440000")),
                Set.of("Pop Punk"));
        TrackCatalogEntry right = new TrackCatalogEntry(
                TrackIdentity.of("SUM 41", "Fat Lip!!!"),
                Set.of(TrackExternalId.isrc("USAB12345678")),
                Set.of("Punk Rock"));

        TrackCatalogEntry merged = left.merge(right);

        assertEquals(2, merged.externalIds().size());
        assertTrue(merged.tags().contains("pop punk"));
        assertTrue(merged.tags().contains("punk rock"));
    }

    @Test
    void mergeRejectsDifferentLogicalTracks() {
        TrackCatalogEntry first = TrackCatalogEntry.of(TrackIdentity.of("Artist", "One"));
        TrackCatalogEntry second = TrackCatalogEntry.of(TrackIdentity.of("Artist", "Two"));

        assertThrows(IllegalArgumentException.class, () -> first.merge(second));
    }

    @Test
    void tagMetadataIsBoundedAndNormalized() {
        TrackCatalogEntry entry = new TrackCatalogEntry(
                TrackIdentity.of("Artist", "Track"),
                Set.of(),
                Set.of("  Alternative   Rock ", "POP PUNK"));

        assertEquals(Set.of("alternative rock", "pop punk"), entry.tags());
    }
}
