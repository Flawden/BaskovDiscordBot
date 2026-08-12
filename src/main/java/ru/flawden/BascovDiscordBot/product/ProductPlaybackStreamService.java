package ru.flawden.BascovDiscordBot.product;

import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;

import java.util.Objects;

/** Client-neutral use case that turns a logical track into a short-lived foreground audio stream. */
@Component
public class ProductPlaybackStreamService {

    private final ProductPlaybackStreamPort streamPort;

    public ProductPlaybackStreamService(ProductPlaybackStreamPort streamPort) {
        this.streamPort = Objects.requireNonNull(streamPort, "streamPort");
    }

    public ProductPlaybackStreamSession open(long guildId, long userId, String artist, String title) {
        return open(guildId, userId, artist, title, 0L);
    }

    public ProductPlaybackStreamSession open(
            long guildId,
            long userId,
            String artist,
            String title,
            long startPositionMillis) {
        if (guildId <= 0L) {
            throw new IllegalArgumentException("guildId must be positive");
        }
        if (userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (startPositionMillis < 0L) {
            throw new IllegalArgumentException("startPositionMillis must not be negative");
        }
        return streamPort.open(
                guildId,
                userId,
                TrackIdentity.of(artist, title),
                startPositionMillis);
    }
}
