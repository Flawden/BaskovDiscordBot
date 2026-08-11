package ru.flawden.BascovDiscordBot.product.api;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeProductGuildAccessAdapterTest {

    @Test
    void unavailableJdaFailsClosedForDiscoveryAndAuthorization() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JDA> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        var adapter = new RuntimeProductGuildAccessAdapter(provider);

        assertFalse(adapter.canAccess(10L, 42L));
        assertEquals(List.of(), adapter.accessibleGuilds(42L));
    }

    @Test
    void accessibleGuildsAreMembershipFilteredAndStableSorted() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JDA> provider = mock(ObjectProvider.class);
        JDA jda = mock(JDA.class);
        Guild zeta = guild(20L, "Zeta", true, 42L);
        Guild alpha = guild(10L, "Alpha", true, 42L);
        Guild denied = guild(30L, "Denied", false, 42L);
        when(provider.getIfAvailable()).thenReturn(jda);
        when(jda.getGuilds()).thenReturn(List.of(zeta, denied, alpha));

        var guilds = new RuntimeProductGuildAccessAdapter(provider).accessibleGuilds(42L);

        assertEquals(List.of("Alpha", "Zeta"), guilds.stream().map(ProductGuildAccessPort.GuildSummary::name).toList());
        assertEquals(List.of(10L, 20L), guilds.stream().map(ProductGuildAccessPort.GuildSummary::guildId).toList());
    }

    private static Guild guild(long id, String name, boolean member, long userId) {
        Guild guild = mock(Guild.class);
        when(guild.getIdLong()).thenReturn(id);
        when(guild.getName()).thenReturn(name);
        when(guild.getMemberById(userId)).thenReturn(member ? mock(Member.class) : null);
        return guild;
    }
}
