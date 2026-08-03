package ru.flawden.BascovDiscordBot.config.eventconfig;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRegistryTest {

    @Test
    void findsCommandsWithoutCaseSensitivityAndKeepsSortedCatalog() {
        Event version = event("version");
        Event help = event("help");
        CommandRegistry registry = new CommandRegistry(List.of(version, help));

        assertTrue(registry.find("HeLp").isPresent());
        assertEquals(List.of("help", "version"),
                registry.commands().stream().map(Event::getName).toList());
    }

    @Test
    void rejectsCaseInsensitiveDuplicates() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new CommandRegistry(List.of(event("help"), event("HELP"))));

        assertTrue(exception.getMessage().contains("Duplicate command name"));
    }

    @Test
    void rejectsUnsafeCommandNames() {
        assertThrows(IllegalStateException.class,
                () -> new CommandRegistry(List.of(event("bad command"))));
    }

    private static Event event(String name) {
        return new Event() {
            @Override
            public void execute(EventArgs event) {
            }

            @Override
            public String getGroup() {
                return "Test";
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String helpMessage() {
                return "test";
            }

            @Override
            public boolean needOwner() {
                return false;
            }
        };
    }
}
