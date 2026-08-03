package ru.flawden.BascovDiscordBot.config.eventconfig;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandInvocationTest {

    @Test
    void parsesCommandCaseInsensitivelyAndNormalizesWhitespace() {
        CommandInvocation invocation = CommandInvocation
                .parse("   !SeArCh   Sabaton   Heart of Iron   ", "!")
                .orElseThrow();

        assertEquals("search", invocation.commandName());
        assertEquals("Sabaton   Heart of Iron", invocation.rawArguments());
        assertEquals(4, invocation.arguments().size());
        assertArrayEquals(
                new String[]{"!SeArCh", "Sabaton", "Heart", "of", "Iron"},
                invocation.toLegacyArgs());
    }

    @Test
    void ignoresMessagesWithoutPrefixAndEmptyCommands() {
        Optional<CommandInvocation> ordinaryMessage = CommandInvocation.parse("hello", "!");
        Optional<CommandInvocation> emptyCommand = CommandInvocation.parse("!   ", "!");

        assertTrue(ordinaryMessage.isEmpty());
        assertTrue(emptyCommand.isEmpty());
    }
}
