package ru.flawden.BascovDiscordBot.product.api;

/** JSON request DTOs for the bounded shared-playlist mutation surface. */
public final class ProductPlaylistApiRequest {

    private ProductPlaylistApiRequest() {
    }

    public record Create(String name) {
    }

    public record AddTrack(String artist, String title) {
    }

    public record Rename(String newName) {
    }

    public record MoveTrack(int from, int to) {
    }
}
