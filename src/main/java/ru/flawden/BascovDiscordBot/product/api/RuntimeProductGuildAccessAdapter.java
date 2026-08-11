package ru.flawden.BascovDiscordBot.product.api;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Runtime JDA adapter kept behind the ProductGuildAccessPort boundary. */
@Component
public class RuntimeProductGuildAccessAdapter implements ProductGuildAccessPort {
    private final ObjectProvider<JDA> jdaProvider;

    public RuntimeProductGuildAccessAdapter(ObjectProvider<JDA> jdaProvider) {
        this.jdaProvider = jdaProvider;
    }

    @Override
    public boolean canAccess(long guildId, long discordUserId) {
        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) return false;
        Guild guild = jda.getGuildById(guildId);
        return guild != null && isMember(guild, discordUserId);
    }

    @Override
    public List<GuildSummary> accessibleGuilds(long discordUserId) {
        if (discordUserId <= 0L) return List.of();
        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) return List.of();
        return jda.getGuilds().stream()
                .filter(guild -> isMember(guild, discordUserId))
                .map(guild -> new GuildSummary(guild.getIdLong(), guild.getName()))
                .sorted(Comparator.comparing(GuildSummary::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(GuildSummary::guildId))
                .toList();
    }

    private static boolean isMember(Guild guild, long discordUserId) {
        if (guild.getMemberById(discordUserId) != null) return true;
        try {
            return guild.retrieveMemberById(discordUserId).complete() != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
