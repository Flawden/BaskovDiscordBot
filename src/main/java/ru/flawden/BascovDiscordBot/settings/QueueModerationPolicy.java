package ru.flawden.BascovDiscordBot.settings;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Component;

/**
 * Least-privilege policy for moderation of the pending music queue.
 * A moderator may remove queued requests but cannot change guild settings.
 */
@Component
public class QueueModerationPolicy {

    private final GuildPreferencesRepository preferencesRepository;

    public QueueModerationPolicy(GuildPreferencesRepository preferencesRepository) {
        this.preferencesRepository = preferencesRepository;
    }

    public boolean canModerate(Member member) {
        if (member == null || member.getGuild() == null) {
            return false;
        }
        GuildPreferences preferences = preferencesRepository.get(member.getGuild().getIdLong());
        return canModerate(
                member.isOwner(),
                member.hasPermission(Permission.MANAGE_SERVER),
                hasRole(member, preferences.managerRoleId()),
                hasRole(member, preferences.moderatorRoleId()),
                hasRole(member, preferences.djRoleId()));
    }

    static boolean canModerate(
            boolean owner,
            boolean manageServer,
            boolean managerRole,
            boolean moderatorRole,
            boolean djRole) {
        return owner || manageServer || managerRole || moderatorRole || djRole;
    }

    private static boolean hasRole(Member member, long roleId) {
        if (roleId <= 0) {
            return false;
        }
        return member.getRoles().stream().anyMatch(role -> role.getIdLong() == roleId);
    }
}
