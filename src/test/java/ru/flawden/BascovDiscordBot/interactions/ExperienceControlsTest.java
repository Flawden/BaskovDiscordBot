package ru.flawden.BascovDiscordBot.interactions;

import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceControlsTest {

    @Test
    void helpSectionButtonsRoundTripAndMarkActiveSection() {
        var rows = ExperienceControls.helpRows(ExperienceControls.HelpSection.QUEUE);
        List<Button> buttons = rows.get(0).getButtons();

        assertEquals(5, buttons.size());
        assertEquals(ExperienceControls.HelpSection.QUEUE,
                ExperienceControls.helpSection("baskov:ux:help:queue").orElseThrow());
        assertTrue(buttons.stream()
                .filter(button -> "Очередь".equals(button.getLabel()))
                .findFirst()
                .orElseThrow()
                .isDisabled());
    }

    @Test
    void confirmationButtonsRoundTrip() {
        String token = "abcdefghijklmnop";
        var rows = ExperienceControls.confirmationRows(token);
        List<Button> buttons = rows.get(0).getButtons();

        assertEquals(2, buttons.size());
        var confirm = ExperienceControls.confirmationAction(buttons.get(0).getCustomId()).orElseThrow();
        var cancel = ExperienceControls.confirmationAction(buttons.get(1).getCustomId()).orElseThrow();
        assertEquals(token, confirm.token());
        assertEquals(ExperienceControls.Decision.CONFIRM, confirm.decision());
        assertEquals(ExperienceControls.Decision.CANCEL, cancel.decision());
        assertTrue(ExperienceControls.supports(ExperienceControls.STATUS_REFRESH));
    }

    @Test
    void malformedComponentIdsAreIgnored() {
        assertFalse(ExperienceControls.helpSection("baskov:ux:help:nope").isPresent());
        assertFalse(ExperienceControls.confirmationAction("baskov:ux:confirm:short:yes").isPresent());
        assertFalse(ExperienceControls.confirmationAction("baskov:ux:confirm:abcdefghijklmnop:maybe").isPresent());
        assertThrows(IllegalArgumentException.class,
                () -> ExperienceControls.confirmationRows("bad token"));
    }
}
