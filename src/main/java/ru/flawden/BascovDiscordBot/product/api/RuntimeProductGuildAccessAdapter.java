package ru.flawden.BascovDiscordBot.product.api;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Runtime JDA adapter kept behind the ProductGuildAccessPort boundary. */
@Component
public class RuntimeProductGuildAccessAdapter implements ProductGuildAccessPort {
    private final ObjectProvider<JDA> jdaProvider;
    public RuntimeProductGuildAccessAdapter(ObjectProvider<JDA> jdaProvider){ this.jdaProvider=jdaProvider; }
    @Override public boolean canAccess(long guildId,long discordUserId){
        JDA jda=jdaProvider.getIfAvailable(); if(jda==null) return false;
        Guild guild=jda.getGuildById(guildId); if(guild==null) return false;
        if (guild.getMemberById(discordUserId) != null) return true;
        try {
            return guild.retrieveMemberById(discordUserId).complete() != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
