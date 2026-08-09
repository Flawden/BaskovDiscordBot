package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordExperienceContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void destructiveActionsUseOneTimeInteractiveConfirmation() throws IOException {
        String interactions = Files.readString(MAIN.resolve("interactions/ModernInteractions.java"));
        String store = Files.readString(MAIN.resolve("interactions/ConfirmationStore.java"));

        assertTrue(interactions.contains("ConfirmationStore.Action.STOP"));
        assertTrue(interactions.contains("ConfirmationStore.Action.CLEAR_QUEUE"));
        assertTrue(interactions.contains("ConfirmationStore.Action.DELETE_PLAYLIST"));
        assertTrue(interactions.contains("ConfirmationStore.Action.RESET_SETTINGS"));
        assertTrue(interactions.contains("confirmationStore.claim("));
        assertTrue(store.contains("pending.remove(token, confirmation)"));
    }

    @Test
    void helpAndStatusAreInteractiveWithoutChangingMusicState() throws IOException {
        String interactions = Files.readString(MAIN.resolve("interactions/ModernInteractions.java"));
        String controls = Files.readString(MAIN.resolve("interactions/ExperienceControls.java"));

        assertTrue(interactions.contains("ExperienceControls.helpRows"));
        assertTrue(interactions.contains("ExperienceControls.statusRows"));
        assertTrue(interactions.contains("statusEmbed(guild)"));
        assertTrue(controls.contains("STATUS_REFRESH"));
        assertFalse(controls.contains("PlayerManager"));
    }
}
