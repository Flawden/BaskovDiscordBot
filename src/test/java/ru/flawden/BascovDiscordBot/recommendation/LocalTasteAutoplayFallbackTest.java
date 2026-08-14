package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.config.DiscoveryProperties;
import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalTasteAutoplayFallbackTest {

    @Test
    void missingExternalProviderRanksDistinctLocalCandidate() {
        DiscoveryProperties properties = new DiscoveryProperties();
        LastFmRecommendationProvider provider = new LastFmRecommendationProvider(properties);
        try {
            SmartDiscoveryEngine engine = new SmartDiscoveryEngine(provider, properties);

            RecommendationPlan plan = engine.recommend(
                    track("Seed", "Artist"),
                    RadioStrategy.SIMILAR,
                    RecommendationContext.empty(),
                    List.of(new RecommendationCandidate(
                            "The Offspring",
                            "The Kids Aren't Alright",
                            0.82d,
                            "local-taste",
                            "known library candidate")))
                    .join();

            assertTrue(plan.fallback());
            assertFalse(plan.external());
            assertEquals("local-taste", plan.candidate().source());
            assertEquals("The Kids Aren't Alright", plan.candidate().title());
        } finally {
            provider.close();
        }
    }

    @Test
    void emptyLocalPoolKeepsSafeSeedFallback() {
        DiscoveryProperties properties = new DiscoveryProperties();
        LastFmRecommendationProvider provider = new LastFmRecommendationProvider(properties);
        try {
            SmartDiscoveryEngine engine = new SmartDiscoveryEngine(provider, properties);

            RecommendationPlan plan = engine.recommend(
                    track("Seed", "Artist"),
                    RadioStrategy.SIMILAR,
                    RecommendationContext.empty(),
                    List.of())
                    .join();

            assertTrue(plan.fallback());
            assertEquals("local-fallback", plan.candidate().source());
            assertEquals("Seed", plan.candidate().title());
        } finally {
            provider.close();
        }
    }

    private static StoredTrack track(String title, String artist) {
        return new StoredTrack(
                title,
                artist,
                "https://www.youtube.com/watch?v=test",
                "test",
                MediaProvider.YOUTUBE,
                180_000L,
                7L,
                "Tester",
                1_700_000_000_000L);
    }
}
