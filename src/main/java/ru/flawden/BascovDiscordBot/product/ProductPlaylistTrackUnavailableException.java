package ru.flawden.BascovDiscordBot.product;

/** Stable failure when a provider-neutral mobile track cannot be captured as a durable shared-playlist item. */
public class ProductPlaylistTrackUnavailableException extends RuntimeException {

    public ProductPlaylistTrackUnavailableException(String message) {
        super(message);
    }

    public ProductPlaylistTrackUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
