package ru.flawden.BascovDiscordBot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModernInteractionsContractTest {

    private static final Path MAIN = Path.of("src/main/java/ru/flawden/BascovDiscordBot");

    @Test
    void botRegistersSlashCommandsAndInteractionListener() throws IOException {
        String config = Files.readString(MAIN.resolve("config/BotConfig.java"));

        assertTrue(config.contains("ModernCommandCatalog.commands()"));
        assertTrue(config.contains("modernInteractions"));
        assertTrue(config.contains("updateCommands()"));
    }

    @Test
    void modernLayerContainsButtonsAutocompleteAndLegacyCompatibility() throws IOException {
        String interactions = Files.readString(MAIN.resolve("interactions/ModernInteractions.java"));
        String botEvents = Files.readString(MAIN.resolve("config/eventconfig/BotEvents.java"));

        assertTrue(interactions.contains("onSlashCommandInteraction"));
        assertTrue(interactions.contains("onCommandAutoCompleteInteraction"));
        assertTrue(interactions.contains("onButtonInteraction"));
        assertTrue(interactions.contains("MusicControls"));
        assertTrue(botEvents.contains("CommandInvocation.parse"), "Prefix commands must remain available");
    }

    @Test
    void playerManagerDoesNotDependOnTextChannelTransport() throws IOException {
        String source = Files.readString(MAIN.resolve("lavaplayer/PlayerManager.java"));

        assertFalse(source.contains("TextChannel"));
        assertTrue(source.contains("Consumer<MusicLoadResult>"));
    }
}
