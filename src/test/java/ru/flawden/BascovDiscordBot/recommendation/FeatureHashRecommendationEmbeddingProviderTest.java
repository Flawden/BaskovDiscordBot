package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureHashRecommendationEmbeddingProviderTest {

    private final FeatureHashRecommendationEmbeddingProvider provider =
            new FeatureHashRecommendationEmbeddingProvider();

    @Test
    void embeddingIsDeterministicBoundedAndNormalized() {
        RecommendationEmbedding first = provider.embed("Green Day", "Holiday", Set.of("punk rock", "alternative"));
        RecommendationEmbedding second = provider.embed("Green Day", "Holiday", Set.of("punk rock", "alternative"));

        assertEquals(64, first.dimensions());
        assertEquals(1.0d, first.norm(), 1.0e-9d);
        for (int index = 0; index < first.dimensions(); index++) {
            assertEquals(first.value(index), second.value(index), 0.0d);
        }
    }

    @Test
    void sharedArtistAndTagsAreCloserThanUnrelatedTrack() {
        RecommendationEmbedding seed = provider.embed("Green Day", "Holiday", Set.of("punk rock", "alternative rock"));
        RecommendationEmbedding related = provider.embed("Green Day", "Basket Case", Set.of("punk rock"));
        RecommendationEmbedding unrelated = provider.embed("Massive Attack", "Teardrop", Set.of("trip hop"));

        assertTrue(RecommendationVectorMath.cosine(seed, related)
                > RecommendationVectorMath.cosine(seed, unrelated));
    }

    @Test
    void cosineIsAlwaysBoundedAndZeroForEmptyVector() {
        RecommendationEmbedding vector = provider.embed("Artist", "Track", Set.of("rock"));
        RecommendationEmbedding empty = provider.embed("", "", Set.of());

        assertTrue(RecommendationVectorMath.cosine(vector, vector) <= 1.0d);
        assertTrue(RecommendationVectorMath.cosine(vector, vector) >= -1.0d);
        assertEquals(0.0d, RecommendationVectorMath.cosine(vector, empty), 0.0d);
    }
}
