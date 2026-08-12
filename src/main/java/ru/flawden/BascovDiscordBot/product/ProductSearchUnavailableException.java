package ru.flawden.BascovDiscordBot.product;

/** Stable product-layer failure when the external search source cannot answer in time. */
public class ProductSearchUnavailableException extends RuntimeException {
    public ProductSearchUnavailableException(String message) {
        super(message);
    }

    public ProductSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
