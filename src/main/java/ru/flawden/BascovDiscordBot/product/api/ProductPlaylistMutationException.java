package ru.flawden.BascovDiscordBot.product.api;

import org.springframework.http.HttpStatus;

/** HTTP-facing mapping for an existing playlist-domain mutation status. */
public final class ProductPlaylistMutationException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ProductPlaylistMutationException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
