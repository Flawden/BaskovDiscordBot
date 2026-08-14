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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductAutoplayServiceTest {

    @Test
    void returnsOneProviderNeutralCandidateUsingPersonalTasteAndKnownLibrary() {
        SmartDiscoveryEngine discovery = mock(SmartDiscoveryEngine.class);
        MusicLibraryRepository library = mock(MusicLibraryRepository.class);
        RecommendationFeedbackService feedback = mock(RecommendationFeedbackService.class);
        PersonalTasteProfile taste = new PersonalTasteProfile(
                3, 4, 1, Map.of(), Map.of("green day", 3.0d), Map.of());

        when(library.history(42L)).thenReturn(List.of(track("Basket Case", "Green Day")));
        when(library.favorites(42L, 7L)).thenReturn(List.of());
        when(library.personalHistory(42L, 7L)).thenReturn(List.of());
        when(feedback.history(42L, 7L, 20)).thenReturn(List.of());
        when(feedback.tasteProfile(42L, 7L)).thenReturn(taste);
        when(feedback.banditProfile(42L, 7L)).thenReturn(ContextualBanditProfile.empty());
        when(discovery.recommend(any(), eq(RadioStrategy.SIMILAR), any()))
                .thenReturn(CompletableFuture.completedFuture(new RecommendationPlan(
                        new RecommendationCandidate(
                                "The Offspring",
                                "The Kids Aren't Alright",
                                0.87d,
                                "Last.fm",
                                "similar + personal"),
                        true,
                        false)));

        ProductAutoplaySnapshot result = new ProductAutoplayService(discovery, library, feedback)
                .next(42L, 7L, "Green Day", "Holiday")
                .join();

        assertTrue(result.available());
        assertFalse(result.fallback());
        assertEquals("The Kids Aren't Alright", result.next().title());
        assertEquals("Last.fm", result.provider());

        ArgumentCaptor<RecommendationContext> context = ArgumentCaptor.forClass(RecommendationContext.class);
        verify(discovery).recommend(any(), eq(RadioStrategy.SIMILAR), context.capture());
        assertEquals(taste, context.getValue().personalTaste());
        assertTrue(context.getValue().knownTrackIdentities().contains(
                track("Basket Case", "Green Day").trackIdentity().stableKey()));
        assertTrue(context.getValue().recentTrackIdentities().contains(
                ru.flawden.BascovDiscordBot.catalog.TrackIdentity.of("Green Day", "Holiday").stableKey()));
    }

    @Test
    void providerFallbackNeverLoopsTheSeedBackToAndroid() {
        SmartDiscoveryEngine discovery = mock(SmartDiscoveryEngine.class);
        MusicLibraryRepository library = emptyLibrary();
        RecommendationFeedbackService feedback = emptyFeedback();

        when(discovery.recommend(any(), eq(RadioStrategy.SIMILAR), any()))
                .thenAnswer(invocation -> {
                    StoredTrack seed = invocation.getArgument(0);
                    return CompletableFuture.completedFuture(
                            RecommendationPlan.fallback(seed, "provider unavailable"));
                });

        ProductAutoplaySnapshot result = new ProductAutoplayService(discovery, library, feedback)
                .next(42L, 7L, "Green Day", "Holiday")
                .join();

        assertFalse(result.available());
        assertTrue(result.fallback());
        assertEquals(null, result.next());
    }

    @Test
    void rejectsBlankOrOverlongSeedBeforeCallingProvider() {
        ProductAutoplayService service = new ProductAutoplayService(
                mock(SmartDiscoveryEngine.class),
                emptyLibrary(),
                emptyFeedback());

        assertThrows(IllegalArgumentException.class, () -> service.next(42L, 7L, "", "Holiday"));
        assertThrows(IllegalArgumentException.class, () -> service.next(42L, 7L, "Green Day", " "));
        assertThrows(IllegalArgumentException.class, () ->
                service.next(42L, 7L, "x".repeat(181), "Holiday"));
    }

    private static MusicLibraryRepository emptyLibrary() {
        MusicLibraryRepository library = mock(MusicLibraryRepository.class);
        when(library.history(42L)).thenReturn(List.of());
        when(library.favorites(42L, 7L)).thenReturn(List.of());
        when(library.personalHistory(42L, 7L)).thenReturn(List.of());
        return library;
    }

    private static RecommendationFeedbackService emptyFeedback() {
        RecommendationFeedbackService feedback = mock(RecommendationFeedbackService.class);
        when(feedback.history(42L, 7L, 20)).thenReturn(List.of());
        when(feedback.tasteProfile(42L, 7L)).thenReturn(PersonalTasteProfile.empty());
        when(feedback.banditProfile(42L, 7L)).thenReturn(ContextualBanditProfile.empty());
        return feedback;
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
