package ru.flawden.BascovDiscordBot.config.eventconfig;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Неизменяемый case-insensitive реестр команд.
 */
public final class CommandRegistry {

    private static final Pattern COMMAND_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9-]{0,31}");

    private final Map<String, Event> commandsByName;
    private final List<Event> commands;

    public CommandRegistry(Collection<Event> events) {
        Objects.requireNonNull(events, "events");

        List<Event> sorted = events.stream()
                .peek(Objects::requireNonNull)
                .sorted(Comparator.comparing(Event::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Map<String, Event> indexed = new LinkedHashMap<>();
        for (Event event : sorted) {
            String originalName = Objects.requireNonNull(event.getName(), "Command name").trim();
            if (!COMMAND_NAME.matcher(originalName).matches()) {
                throw new IllegalStateException("Invalid command name: " + originalName);
            }

            String normalizedName = normalize(originalName);
            Event duplicate = indexed.putIfAbsent(normalizedName, event);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "Duplicate command name: " + duplicate.getName() + " / " + event.getName());
            }
        }

        this.commandsByName = Map.copyOf(indexed);
        this.commands = List.copyOf(sorted);
    }

    public Optional<Event> find(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(commandsByName.get(normalize(commandName)));
    }

    public List<Event> commands() {
        return commands;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
