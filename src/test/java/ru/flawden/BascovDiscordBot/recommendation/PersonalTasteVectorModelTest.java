package ru.flawden.BascovDiscordBot.recommendation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalTasteVectorModelTest {

    private final FeatureHashRecommendationEmbeddingProvider provider =
            new FeatureHashRecommendationEmbeddingProvider();

    @Test
    void emptyFeedbackProducesUnavailableVectorProfile() {
        PersonalTasteVector vector = PersonalTasteVectorModel.build(PersonalTasteProfile.empty(), provider);

        assertEquals("feature-hash-v1", vector.provider());
        assertEquals(64, vector.dimensions());
        assertEquals(0, vector.contributingFeatures());
        assertEquals(0.0d, vector.confidence(), 0.0d);
        assertTrue(!vector.available());
    }

    @Test
    void likedFeaturesPointTasteVectorTowardRelatedCandidate() {
        RecommendationFeedbackEntry liked = entry(
                "1", "Green Day", "Holiday", Set.of("punk rock", "alternative rock"), 8.0d, 3, 0);
        PersonalTasteProfile profile = PersonalRankingModel.build(List.of(liked));
        PersonalTasteVector taste = PersonalTasteVectorModel.build(profile, provider);

        double related = RecommendationVectorMath.cosine(
                taste.vector(),
                provider.embed("Green Day", "Basket Case", Set.of("punk rock")));
        double unrelated = RecommendationVectorMath.cosine(
                taste.vector(),
                provider.embed("Massive Attack", "Teardrop", Set.of("trip hop")));

        assertTrue(taste.available());
        assertTrue(related > unrelated);
    }

    @Test
    void dislikedTrackCanPushVectorSimilarityNegative() {
        RecommendationFeedbackEntry disliked = entry(
                "1", "Artist", "Neon Dreams", Set.of("synth rock"), -9.0d, 0, 3);
        PersonalTasteProfile profile = PersonalRankingModel.build(List.of(disliked));
        PersonalTasteVector taste = PersonalTasteVectorModel.build(profile, provider);

        double similarity = RecommendationVectorMath.cosine(
                taste.vector(),
                provider.embed("Other Artist", "Neon Nights", Set.of("synth rock")));

        assertTrue(similarity < 0.0d);
    }

    private static RecommendationFeedbackEntry entry(
            String id,
            String artist,
            String title,
            Set<String> tags,
            double score,
            int positive,
            int negative) {
        return new RecommendationFeedbackEntry(
                id,
                42L,
                7L,
                "Seed",
                "Seed Song",
                artist,
                title,
                RecommendationIdentity.of(artist, title),
                tags,
                RadioStrategy.DISCOVERY,
                "Last.fm",
                0.8d,
                1_700_000_000_000L,
                score > 0 ? RecommendationOutcome.FAVORITED : RecommendationOutcome.UNFAVORITED,
                1_700_000_001_000L,
                positive,
                negative,
                score,
                1.0d);
    }
}
