package ru.flawden.BascovDiscordBot.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueModerationPolicyTest {

    @Test
    void grantsLeastPrivilegeModerationToConfiguredOperationalRoles() {
        assertTrue(QueueModerationPolicy.canModerate(true, false, false, false, false));
        assertTrue(QueueModerationPolicy.canModerate(false, true, false, false, false));
        assertTrue(QueueModerationPolicy.canModerate(false, false, true, false, false));
        assertTrue(QueueModerationPolicy.canModerate(false, false, false, true, false));
        assertTrue(QueueModerationPolicy.canModerate(false, false, false, false, true));
        assertFalse(QueueModerationPolicy.canModerate(false, false, false, false, false));
    }
}
