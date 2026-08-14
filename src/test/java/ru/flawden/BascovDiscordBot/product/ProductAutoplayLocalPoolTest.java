package ru.flawden.BascovDiscordBot.product;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.library.MusicLibraryRepository;
import ru.flawden.BascovDiscordBot.library.StoredTrack;
import ru.flawden.BascovDiscordBot.recommendation.ContextualBanditProfile;
import ru.flawden.BascovDiscordBot.recommendation.PersonalTasteProfile;
import ru.flawden.BascovDiscordBot.recommendation.RadioStrategy;
import ru.flawden.BascovDiscordBot.recommendation.RecommendationCandidate;
import ru.flawden.BascovDiscordBot.recommendation.RecommendationContext;
import ru.flawden.BascovDiscordBot.recommendation.RecommendationFeedbackService;
import ru.flawden.BascovDiscordBot.recommendation.RecommendationPlan;
import ru.flawden.BascovDiscordBot.recommendation.SmartDiscoveryEngine;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductAutoplayLocalPoolTest {

    @Test
    void passesKnownLibraryTracksAsLocalCandidates() {
        SmartDiscoveryEngine discovery = mock(SmartDiscoveryEngine.class);
        MusicLibraryRepository library = mock(MusicLibraryRepository.class);
        RecommendationFeedbackService feedback = mock(RecommendationFeedbackService.class);

        when(library.history(42L)).thenReturn(List.of(track("Basket Case", "Green Day")));
        when(library.favorites(42L, 7L)).thenReturn(List.of());
        when(library.personalHistory(42L, 7L)).thenReturn(List.of());
        when(feedback.history(42L, 7L, 20)).thenReturn(List.of());
        when(feedback.tasteProfile(42L, 7L)).thenReturn(PersonalTasteProfile.empty());
        when(feedback.banditProfile(42L, 7L)).thenReturn(ContextualBanditProfile.empty());

        when(discovery.recommend(any(), eq(RadioStrategy.SIMILAR), any(), anyList()))
                .thenAnswer(invocation -> {
                    List<RecommendationCandidate> candidates = invocation.getArgument(3);
                    return CompletableFuture.completedFuture(new RecommendationPlan(
                            candidates.get(0),
                            false,
                            true));
                });

        new ProductAutoplayService(discovery, library, feedback)
                .next(42L, 7L, "Green Day", "Holiday")
                .join();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecommendationCandidate>> candidates =
                ArgumentCaptor.forClass((Class) List.class);

        verify(discovery).recommend(
                any(),
                eq(RadioStrategy.SIMILAR),
                any(RecommendationContext.class),
                candidates.capture());

        assertTrue(candidates.getValue().stream().anyMatch(candidate ->
                candidate.trackIdentity().stableKey().equals(
                        track("Basket Case", "Green Day").trackIdentity().stableKey())));
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
