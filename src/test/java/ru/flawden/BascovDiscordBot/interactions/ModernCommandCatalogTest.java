package ru.flawden.BascovDiscordBot.interactions;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModernCommandCatalogTest {

    @Test
    void publishesExpectedSlashCommandsWithoutDuplicates() {
        List<CommandData> commands = ModernCommandCatalog.commands();
        Set<String> names = commands.stream().map(CommandData::getName).collect(Collectors.toSet());

        assertEquals(commands.size(), names.size());
        assertEquals(Set.of(
                "help", "version", "play", "pause", "resume",
                "skip", "stop", "queue", "now", "seek"), names);
    }

    @Test
    void playCommandEnablesAutocomplete() {
        CommandData playCommand = ModernCommandCatalog.commands().stream()
                .filter(command -> command.getName().equals("play"))
                .findFirst()
                .orElseThrow();
        SlashCommandData play = assertInstanceOf(SlashCommandData.class, playCommand);

        assertTrue(play.getOptions().stream()
                .anyMatch(option -> option.getName().equals("query") && option.isAutoComplete()));
    }
}
