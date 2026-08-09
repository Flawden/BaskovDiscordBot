package ru.flawden.BascovDiscordBot.recommendation;

import ru.flawden.BascovDiscordBot.library.StoredTrack;

import java.util.concurrent.CompletableFuture;

/**
 * Optional collaborative source. It never owns playback or queue state.
 */
public interface CollaborativeSignalProvider {

    String name();

    boolean available();

    CompletableFuture<CollaborativeArtistSignals> signalsFor(StoredTrack seed);
}
