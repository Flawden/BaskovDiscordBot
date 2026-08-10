package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.catalog.TrackExternalId;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationCandidateCatalogTest {

    @Test
    void catalogEntryCarriesIdentityTagsAndAuthoritativeIdsWithoutPlaybackLocator() {
        RecommendationCandidate candidate = new RecommendationCandidate(
                "Blink-182",
                "Dammit",
                0.82d,
                "Last.fm",
                "similar",
                Set.of("Pop Punk"))
                .withExternalId(TrackExternalId.musicBrainzRecording("550e8400-e29b-41d4-a716-446655440000"));

        assertEquals("blink 182::dammit", candidate.catalogEntry().identity().stableKey());
        assertEquals(Set.of("pop punk"), candidate.catalogEntry().tags());
        assertEquals(1, candidate.catalogEntry().externalIds().size());
    }

    @Test
    void enrichingTagsPreservesExternalCatalogIds() {
        RecommendationCandidate candidate = new RecommendationCandidate(
                "Artist", "Track", 0.5d, "Last.fm", "reason")
                .withExternalId(TrackExternalId.isrc("USAB12345678"));

        RecommendationCandidate enriched = candidate.withTags(Set.of("Rock"));

        assertEquals(candidate.externalIds(), enriched.externalIds());
        assertTrue(enriched.tags().contains("rock"));
    }

    @Test
    void candidateIdentityMatchesCatalogStableKey() {
        RecommendationCandidate candidate = new RecommendationCandidate(
                "  Linkin Park ", "Numb!!!", 0.9d, "Last.fm", "reason");

        assertEquals(candidate.trackIdentity().stableKey(), candidate.identity());
    }
}
