package ru.flawden.BascovDiscordBot.product;

import ru.flawden.BascovDiscordBot.catalog.TrackIdentity;

/** Runtime port for provider-neutral mobile playback streaming. */
@FunctionalInterface
public interface ProductPlaybackStreamPort {

    ProductPlaybackStreamSession open(
            long guildId,
            long userId,
            TrackIdentity track,
            long startPositionMillis);
}
