package ru.flawden.BascovDiscordBot.config.eventconfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Неизменяемый результат разбора префиксной команды.
 */
public record CommandInvocation(
        String commandToken,
        String commandName,
        List<String> arguments,
        String rawArguments
) {

    public CommandInvocation {
        Objects.requireNonNull(commandToken, "commandToken");
        Objects.requireNonNull(commandName, "commandName");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(rawArguments, "rawArguments");
        arguments = List.copyOf(arguments);
    }

    /**
     * Разбирает сообщение вида {@code !command arg one}. Пустая команда не считается вызовом.
     */
    public static Optional<CommandInvocation> parse(String content, String prefix) {
        if (content == null || prefix == null || prefix.isBlank()) {
            return Optional.empty();
        }

        String trimmed = content.trim();
        if (!trimmed.startsWith(prefix)) {
            return Optional.empty();
        }

        String body = trimmed.substring(prefix.length()).trim();
        if (body.isEmpty()) {
            return Optional.empty();
        }

        int separator = firstWhitespace(body);
        String enteredName = separator < 0 ? body : body.substring(0, separator);
        String rawArguments = separator < 0 ? "" : body.substring(separator).trim();
        List<String> arguments = rawArguments.isEmpty()
                ? List.of()
                : Arrays.stream(rawArguments.split("\\s+"))
                .filter(argument -> !argument.isBlank())
                .toList();

        return Optional.of(new CommandInvocation(
                prefix + enteredName,
                enteredName.toLowerCase(Locale.ROOT),
                arguments,
                rawArguments));
    }

    /**
     * Совместимый массив: первый элемент — токен команды, далее аргументы.
     */
    public String[] toLegacyArgs() {
        List<String> legacy = new ArrayList<>(arguments.size() + 1);
        legacy.add(commandToken);
        legacy.addAll(arguments);
        return legacy.toArray(String[]::new);
    }

    private static int firstWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }
}
