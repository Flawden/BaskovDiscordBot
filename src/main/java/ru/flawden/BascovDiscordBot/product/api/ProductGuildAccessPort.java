package ru.flawden.BascovDiscordBot.product.api;

/** Client-neutral authorization boundary for checking linked Discord membership. */
public interface ProductGuildAccessPort {
    boolean canAccess(long guildId, long discordUserId);
}
