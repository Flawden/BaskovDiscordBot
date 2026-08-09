package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;
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
