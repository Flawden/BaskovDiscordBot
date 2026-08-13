package ru.flawden.BascovDiscordBot.product.api;

/** JSON request DTOs for personal favorite mutations. */
public final class ProductFavoriteApiRequest {

    private ProductFavoriteApiRequest() {
    }

    public record AddTrack(String artist, String title) {
    }
}
