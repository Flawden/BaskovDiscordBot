package ru.flawden.BascovDiscordBot.product;

/** Stable product-level failure used when no provider can prepare a foreground mobile stream. */
public class ProductPlaybackUnavailableException extends RuntimeException {
    public ProductPlaybackUnavailableException(String message) {
        super(message);
    }

    public ProductPlaybackUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
