package ru.flawden.BascovDiscordBot.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.config.DiscoveryProperties;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastFmRecommendationProviderTest {

    @Test
    void parsesSimilarTracksAndPreservesSimilarity() throws Exception {
        DiscoveryProperties properties = new DiscoveryProperties();
        LastFmRecommendationProvider provider = new LastFmRecommendationProvider(properties, new ObjectMapper());
        try {
            String json = """
                    {"similartracks":{"track":[
                      {"name":"Fresh Song","match":"0.873","artist":{"name":"Fresh Artist"}},
                      {"name":"Second Song","match":"0.600","artist":{"name":"Second Artist"}}
                    ]}}
                    """;

            List<RecommendationCandidate> candidates = provider.parseSimilarTracks(json, 10);
            assertEquals(2, candidates.size());
            assertEquals("Fresh Artist", candidates.get(0).artist());
            assertEquals("Fresh Song", candidates.get(0).title());
            assertEquals(0.873d, candidates.get(0).similarity(), 0.0001d);
        } finally {
            provider.close();
        }
    }

    @Test
    void requestUriIsHttpsAndUsesTrackGetSimilar() {
        DiscoveryProperties properties = new DiscoveryProperties();
        properties.setLastfmApiKey("test-key");
        LastFmRecommendationProvider provider = new LastFmRecommendationProvider(properties, new ObjectMapper());
        try {
            URI uri = provider.buildUri("track.getsimilar", "Linkin Park", "Numb", 25);
            assertEquals("https", uri.getScheme());
            assertTrue(uri.getQuery().contains("method=track.getsimilar"));
            assertTrue(uri.getQuery().contains("artist=Linkin+Park"));
            assertTrue(uri.getQuery().contains("track=Numb"));
        } finally {
            provider.close();
        }
    }
}
