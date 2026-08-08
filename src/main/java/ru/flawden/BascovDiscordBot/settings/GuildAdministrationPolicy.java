package ru.flawden.BascovDiscordBot.settings;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Component;

/**
 * Единая политика административных операций Баскова внутри одной guild.
 */
@Component
public class GuildAdministrationPolicy {

    private final GuildPreferencesRepository preferencesRepository;

    public GuildAdministrationPolicy(GuildPreferencesRepository preferencesRepository) {
        this.preferencesRepository = preferencesRepository;
    }

    public boolean canManage(Member member) {
        if (member == null || member.getGuild() == null) {
            return false;
        }
        GuildPreferences preferences = preferencesRepository.get(member.getGuild().getIdLong());
        return canManage(
                member.isOwner(),
                member.hasPermission(Permission.MANAGE_SERVER),
                hasRole(member, preferences.managerRoleId()));
    }

    static boolean canManage(boolean owner, boolean manageServer, boolean managerRole) {
        return owner || manageServer || managerRole;
    }

    private static boolean hasRole(Member member, long roleId) {
        if (roleId <= 0) {
            return false;
        }
        return member.getRoles().stream().anyMatch(role -> role.getIdLong() == roleId);
    }
}
