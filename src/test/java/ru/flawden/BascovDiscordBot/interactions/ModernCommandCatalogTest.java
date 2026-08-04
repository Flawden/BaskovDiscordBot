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
                "skip", "stop", "queue", "now", "seek",
                "volume", "repeat", "shuffle", "remove", "move", "clear", "settings"), names);
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

    @Test
    void queueExperienceCommandsExposeRequiredOptions() {
        SlashCommandData repeat = command("repeat");
        SlashCommandData move = command("move");
        SlashCommandData volume = command("volume");

        assertEquals(Set.of("mode"), repeat.getOptions().stream()
                .map(option -> option.getName())
                .collect(Collectors.toSet()));
        assertEquals(Set.of("from", "to"), move.getOptions().stream()
                .map(option -> option.getName())
                .collect(Collectors.toSet()));
        assertEquals(Set.of("level"), volume.getOptions().stream()
                .map(option -> option.getName())
                .collect(Collectors.toSet()));
    }

    @Test
    void settingsCommandExposesPersistentPreferenceSubcommands() {
        SlashCommandData settings = command("settings");
        assertEquals(Set.of("show", "volume", "repeat", "reset"), settings.getSubcommands().stream()
                .map(subcommand -> subcommand.getName())
                .collect(Collectors.toSet()));
    }

    private static SlashCommandData command(String name) {
        CommandData command = ModernCommandCatalog.commands().stream()
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
        return assertInstanceOf(SlashCommandData.class, command);
    }
}
