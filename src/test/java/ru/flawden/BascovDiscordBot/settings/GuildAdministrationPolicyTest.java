package ru.flawden.BascovDiscordBot.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildAdministrationPolicyTest {

    @Test
    void ownerManageServerAndConfiguredManagerRoleCanAdminister() {
        assertTrue(GuildAdministrationPolicy.canManage(true, false, false));
        assertTrue(GuildAdministrationPolicy.canManage(false, true, false));
        assertTrue(GuildAdministrationPolicy.canManage(false, false, true));
        assertFalse(GuildAdministrationPolicy.canManage(false, false, false));
    }
}
