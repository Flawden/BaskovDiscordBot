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
                "help", "version", "status", "play", "search", "discover", "history", "replay", "playlist",
                "pause", "resume", "previous", "skip", "voteskip", "stop", "queue", "now", "seek",
                "volume", "repeat", "shuffle", "remove", "move", "clear", "queue-manage", "settings"), names);
    }

    @Test
    void playAndSearchCommandsEnableAutocomplete() {
        for (String name : List.of("play", "search")) {
            SlashCommandData command = command(name);
            assertTrue(command.getOptions().stream()
                    .anyMatch(option -> option.getName().equals("query") && option.isAutoComplete()));
        }
    }

    @Test
    void discoverCommandExposesSearchContinuationModes() {
        SlashCommandData discover = command("discover");
        assertEquals(Set.of("recent", "again", "related", "history"),
                discover.getSubcommands().stream()
                        .map(subcommand -> subcommand.getName())
                        .collect(Collectors.toSet()));
        assertTrue(discover.getSubcommands().stream()
                .filter(subcommand -> "history".equals(subcommand.getName()))
                .flatMap(subcommand -> subcommand.getOptions().stream())
                .anyMatch(option -> "position".equals(option.getName())));
    }

    @Test
    void queueExperienceCommandsExposeRequiredOptions() {
        SlashCommandData repeat = command("repeat");
        SlashCommandData move = command("move");
        SlashCommandData volume = command("volume");
        SlashCommandData queue = command("queue");

        assertEquals(Set.of("mode"), repeat.getOptions().stream()
                .map(option -> option.getName())
                .collect(Collectors.toSet()));
        assertEquals(Set.of("from", "to"), move.getOptions().stream()
                .map(option -> option.getName())
                .collect(Collectors.toSet()));
        assertEquals(Set.of("level"), volume.getOptions().stream()
                .map(option -> option.getName())
                .collect(Collectors.toSet()));
        assertEquals(Set.of("page"), queue.getOptions().stream()
                .map(option -> option.getName())
                .collect(Collectors.toSet()));
    }

    @Test
    void queueManagerExposesStatsAndSafeBatchMutations() {
        SlashCommandData queueManager = command("queue-manage");
        assertEquals(Set.of("stats", "remove-range", "dedupe", "remove-mine"),
                queueManager.getSubcommands().stream()
                        .map(subcommand -> subcommand.getName())
                        .collect(Collectors.toSet()));

        assertTrue(queueManager.getSubcommands().stream()
                .filter(subcommand -> !"stats".equals(subcommand.getName()))
                .flatMap(subcommand -> subcommand.getOptions().stream())
                .anyMatch(option -> option.getName().equals("revision")));
    }

    @Test
    void playlistCommandExposesPersistentLibrarySubcommands() {
        SlashCommandData playlist = command("playlist");
        assertEquals(Set.of(
                        "list", "create", "show", "add", "play", "remove", "move",
                        "rename", "copy", "dedupe", "capture-queue", "add-history", "search", "delete"),
                playlist.getSubcommands().stream()
                        .map(subcommand -> subcommand.getName())
                        .collect(Collectors.toSet()));
        assertTrue(playlist.getSubcommands().stream()
                .filter(subcommand -> Set.of(
                                "show", "add", "play", "remove", "move", "rename",
                                "copy", "dedupe", "capture-queue", "add-history", "delete")
                        .contains(subcommand.getName()))
                .flatMap(subcommand -> subcommand.getOptions().stream())
                .filter(option -> option.getName().equals("name"))
                .allMatch(option -> option.isAutoComplete()));
    }

    @Test
    void settingsCommandExposesPersistentPreferenceSubcommands() {
        SlashCommandData settings = command("settings");
        assertEquals(Set.of("show", "volume", "repeat", "access", "dj-role", "vote-threshold", "reset"),
                settings.getSubcommands().stream()
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
