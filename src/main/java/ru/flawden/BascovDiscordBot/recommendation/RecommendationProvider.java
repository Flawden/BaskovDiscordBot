package ru.flawden.BascovDiscordBot.recommendation;

import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface RecommendationProvider {

    String name();

    boolean available();

    CompletableFuture<List<RecommendationCandidate>> similarTracks(StoredTrack seed, int limit);

    default CompletableFuture<RecommendationCandidate> enrich(RecommendationCandidate candidate) {
        return CompletableFuture.completedFuture(candidate);
    }
}
