package ru.flawden.BascovDiscordBot.product.api;

import java.util.List;

/** Client-neutral authorization boundary for linked Discord guild membership and discovery. */
public interface ProductGuildAccessPort {
    boolean canAccess(long guildId, long discordUserId);

    List<GuildSummary> accessibleGuilds(long discordUserId);

    record GuildSummary(long guildId, String name) {
        public GuildSummary {
            if (guildId <= 0L) throw new IllegalArgumentException("guildId must be positive");
            name = name == null ? "" : name.trim();
        }
    }
}
