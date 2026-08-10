package ru.flawden.BascovDiscordBot.product;

/** Runtime-only read port needed by client-neutral product use cases. */
public interface MusicProductReadPort {

    ProductPlaybackSnapshot playback(long guildId);
}
