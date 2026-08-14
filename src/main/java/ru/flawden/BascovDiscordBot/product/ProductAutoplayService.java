package ru.flawden.BascovDiscordBot.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;
import ru.flawden.BascovDiscordBot.commands.music.MediaProvider;
import ru.flawden.BascovDiscordBot.library.MusicLibraryRepository;
import ru.flawden.BascovDiscordBot.library.StoredTrack;
import ru.flawden.BascovDiscordBot.recommendation.CollaborativeArtistSignals;
import ru.flawden.BascovDiscordBot.recommendation.ContextualBanditProfile;
import ru.flawden.BascovDiscordBot.recommendation.MixDiversityProfile;
import ru.flawden.BascovDiscordBot.recommendation.RecommendationContext;
import ru.flawden.BascovDiscordBot.recommendation.RecommendationFeedbackRepository;
import ru.flawden.BascovDiscordBot.recommendation.RecommendationFeedbackService;
import ru.flawden.BascovDiscordBot.recommendation.RecommendationIdentity;
import ru.flawden.BascovDiscordBot.recommendation.RecommendationPlan;
import ru.flawden.BascovDiscordBot.recommendation.RadioStrategy;
import ru.flawden.BascovDiscordBot.recommendation.SessionTasteProfile;
import ru.flawden.BascovDiscordBot.recommendation.SmartDiscoveryEngine;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Stateless continuation boundary for Android/Web.
 *
 * It chooses exactly one logical next track. It never touches Discord voice/player state and
 * never resolves a playback provider; clients hand the returned artist/title to playback/stream.
 */
@Component
public class ProductAutoplayService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductAutoplayService.class);
    private static final int RECENT_FEEDBACK_LIMIT = 20;

    private final SmartDiscoveryEngine discovery;
    private final MusicLibraryRepository library;
    private final RecommendationFeedbackService feedback;

    public ProductAutoplayService(
            SmartDiscoveryEngine discovery,
            MusicLibraryRepository library,
            RecommendationFeedbackService feedback) {
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.library = Objects.requireNonNull(library, "library");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
    }

    public CompletableFuture<ProductAutoplaySnapshot> next(
            long guildId,
            long userId,
            String artist,
            String title) {
        validate(guildId, userId, artist, title);
        TrackIdentity seedIdentity = TrackIdentity.of(artist, title);
        StoredTrack seed = logicalSeed(seedIdentity, userId);
        RecommendationContext context = context(guildId, userId, seedIdentity);

        return discovery.recommend(seed, RadioStrategy.SIMILAR, context)
                .thenApply(plan -> {
                    ProductAutoplaySnapshot result = snapshot(guildId, userId, seedIdentity, plan);
                    LOGGER.info(
                            "Product autoplay decision guild={} user={} seed={} available={} fallback={} provider={} next={} reason={}",
                            guildId,
                            userId,
                            seedIdentity.stableKey(),
                            result.available(),
                            result.fallback(),
                            result.provider(),
                            result.next() == null ? "none" : result.next().trackIdentity().stableKey(),
                            result.reason());
                    return result;
                });
    }

    private RecommendationContext context(long guildId, long userId, TrackIdentity seed) {
        Set<String> known = new LinkedHashSet<>();
        library.history(guildId).stream()
                .map(RecommendationIdentity::of)
                .forEach(known::add);
        library.favorites(guildId, userId).stream()
                .map(RecommendationIdentity::of)
                .forEach(known::add);
        library.personalHistory(guildId, userId).stream()
                .map(RecommendationIdentity::of)
                .forEach(known::add);

        Set<String> recentTracks = new LinkedHashSet<>();
        Set<String> recentArtists = new LinkedHashSet<>();
        recentTracks.add(seed.stableKey());
        recentArtists.add(RecommendationIdentity.normalizeArtist(seed.artist()));

        feedback.history(guildId, userId, RECENT_FEEDBACK_LIMIT).forEach(entry -> {
            if (entry.trackIdentity() != null && !entry.trackIdentity().isBlank()) {
                recentTracks.add(entry.trackIdentity());
            }
            if (entry.trackArtist() != null && !entry.trackArtist().isBlank()) {
                recentArtists.add(RecommendationIdentity.normalizeArtist(entry.trackArtist()));
            }
        });

        return new RecommendationContext(
                known,
                recentTracks,
                recentArtists,
                feedback.tasteProfile(guildId, userId),
                CollaborativeArtistSignals.empty(),
                SessionTasteProfile.empty(0L),
                feedback.banditProfile(guildId, userId),
                MixDiversityProfile.disabled());
    }

    private static ProductAutoplaySnapshot snapshot(
            long guildId,
            long userId,
            TrackIdentity seed,
            RecommendationPlan plan) {
        if (plan == null || plan.candidate() == null) {
            return unavailable(guildId, userId, seed, false, "none", "No recommendation plan");
        }
        var candidate = plan.candidate();
        boolean sameAsSeed = candidate.trackIdentity().stableKey().equals(seed.stableKey());
        if (sameAsSeed) {
            return unavailable(
                    guildId,
                    userId,
                    seed,
                    plan.fallback(),
                    candidate.source(),
                    candidate.reason());
        }
        return new ProductAutoplaySnapshot(
                guildId,
                userId,
                seed,
                candidate,
                true,
                plan.fallback(),
                candidate.source(),
                candidate.reason());
    }

    private static ProductAutoplaySnapshot unavailable(
            long guildId,
            long userId,
            TrackIdentity seed,
            boolean fallback,
            String provider,
            String reason) {
        return new ProductAutoplaySnapshot(
                guildId,
                userId,
                seed,
                null,
                false,
                fallback,
                provider,
                reason);
    }

    /**
     * SmartDiscoveryEngine currently accepts StoredTrack as seed although providers/ranking use
     * only logical artist/title identity. This synthetic descriptor is process-local and is never
     * persisted or handed to playback.
     */
    private static StoredTrack logicalSeed(TrackIdentity identity, long userId) {
        return new StoredTrack(
                identity.title(),
                identity.artist(),
                "baskov:logical-autoplay-seed:" + identity.stableKey(),
                "",
                MediaProvider.UNKNOWN,
                1L,
                userId,
                "Baskov Product Autoplay",
                System.currentTimeMillis());
    }

    private static void validate(
            long guildId,
            long userId,
            String artist,
            String title) {
        if (guildId <= 0L || userId <= 0L) {
            throw new IllegalArgumentException("guildId and userId must be positive");
        }
        if (artist == null || artist.isBlank()) {
            throw new IllegalArgumentException("artist cannot be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title cannot be blank");
        }
        if (artist.trim().length() > 180 || title.trim().length() > 180) {
            throw new IllegalArgumentException("artist/title is too long");
        }
    }
}
