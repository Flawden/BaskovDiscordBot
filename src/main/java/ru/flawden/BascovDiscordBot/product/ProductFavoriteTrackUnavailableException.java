package ru.flawden.BascovDiscordBot.product;

/** Stable product-layer failure when a selected remote track cannot be persisted as a favorite. */
public final class ProductFavoriteTrackUnavailableException extends RuntimeException {

    public ProductFavoriteTrackUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
