package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.catalog.TrackExternalId;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.config.DiscoveryProperties;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartDiscoveryEngineTest {

    @Test
    void familiarNeverNeedsExternalProvider() {
        DiscoveryProperties properties = new DiscoveryProperties();
        LastFmRecommendationProvider provider = new LastFmRecommendationProvider(properties);
        try {
            SmartDiscoveryEngine engine = new SmartDiscoveryEngine(provider, properties);
            RecommendationPlan plan = engine.recommend(track("Seed", "Artist"), RadioStrategy.FAMILIAR, RecommendationContext.empty()).join();
            assertFalse(plan.external());
            assertEquals("local", plan.candidate().source());
        } finally {
            provider.close();
        }
    }

    @Test
    void discoveryFallsBackWhenApiKeyMissing() {
        DiscoveryProperties properties = new DiscoveryProperties();
        LastFmRecommendationProvider provider = new LastFmRecommendationProvider(properties);
        try {
            SmartDiscoveryEngine engine = new SmartDiscoveryEngine(provider, properties);
            RecommendationPlan plan = engine.recommend(track("Seed", "Artist"), RadioStrategy.DISCOVERY, RecommendationContext.empty()).join();
            assertTrue(plan.fallback());
            assertEquals("local-fallback", plan.candidate().source());
        } finally {
            provider.close();
        }
    }

    @Test
    void selectionExplainsNovelCandidate() {
        DiscoveryProperties properties = new DiscoveryProperties();
        LastFmRecommendationProvider provider = new LastFmRecommendationProvider(properties);
        try {
            SmartDiscoveryEngine engine = new SmartDiscoveryEngine(provider, properties);
            RecommendationCandidate candidate = new RecommendationCandidate("Fresh", "Song", 0.82, "Last.fm", "provider reason");
            RecommendationPlan plan = engine.select(
                    track("Seed", "Artist"),
                    RadioStrategy.DISCOVERY,
                    new RecommendationContext(Set.of(), Set.of(), Set.of()),
                    List.of(candidate));
            assertTrue(plan.external());
            assertTrue(plan.candidate().reason().contains("трек новый"));
        } finally {
            provider.close();
        }
    }

    @Test
    void selectionExplainsCollaborativeContribution() {
        DiscoveryProperties properties = new DiscoveryProperties();
        LastFmRecommendationProvider provider = new LastFmRecommendationProvider(properties);
        CollaborativeSignalProvider collaborative = new CollaborativeSignalProvider() {
            @Override public String name() { return "ListenBrainz"; }
            @Override public boolean available() { return true; }
            @Override public java.util.concurrent.CompletableFuture<CollaborativeArtistSignals> signalsFor(StoredTrack seed) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        new CollaborativeArtistSignals("ListenBrainz", java.util.Map.of("Fresh", 1.0d)));
            }
        };
        try {
            SmartDiscoveryEngine engine = new SmartDiscoveryEngine(
                    provider, properties, new FeatureHashRecommendationEmbeddingProvider(), collaborative);
            RecommendationCandidate candidate = new RecommendationCandidate("Fresh", "Song", 0.82, "Last.fm", "provider reason");
            RecommendationPlan plan = engine.select(
                    track("Seed", "Artist"),
                    RadioStrategy.DISCOVERY,
                    RecommendationContext.empty().withCollaborativeSignals(
                            new CollaborativeArtistSignals("ListenBrainz", java.util.Map.of("Fresh", 1.0d))),
                    List.of(candidate));
            assertTrue(plan.candidate().reason().contains("collaborative"));
            assertTrue(plan.candidate().reason().contains("ListenBrainz"));
        } finally {
            provider.close();
        }
    }

    @Test
    void selectionPreservesCatalogExternalIdsAcrossReasonRebuild() {
        DiscoveryProperties properties = new DiscoveryProperties();
        LastFmRecommendationProvider provider = new LastFmRecommendationProvider(properties);
        try {
            SmartDiscoveryEngine engine = new SmartDiscoveryEngine(provider, properties);
            RecommendationCandidate candidate = new RecommendationCandidate(
                    "Fresh", "Song", 0.82, "Last.fm", "provider reason")
                    .withExternalId(TrackExternalId.musicBrainzRecording(
                            "550e8400-e29b-41d4-a716-446655440000"));

            RecommendationPlan plan = engine.select(
                    track("Seed", "Artist"),
                    RadioStrategy.DISCOVERY,
                    RecommendationContext.empty(),
                    List.of(candidate));

            assertEquals(candidate.externalIds(), plan.candidate().externalIds());
        } finally {
            provider.close();
        }
    }

    private static StoredTrack track(String title, String artist) {
        return new StoredTrack(
                title,
                artist,
                "https://www.youtube.com/watch?v=abcdefghijk",
                "abcdefghijk",
                MediaProvider.YOUTUBE,
                180_000L,
                1L,
                "tester",
                1_700_000_000_000L);
    }
}
