package ru.flawden.BascovDiscordBot.library;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaylistNameTest {

    @Test
    void normalizesWhitespaceAndCaseInsensitiveKey() {
        assertEquals("Рок для работы", PlaylistName.display("  Рок   для работы  "));
        assertEquals("рок для работы", PlaylistName.key("Рок Для Работы"));
    }

    @Test
    void rejectsBlankAndOversizedNames() {
        assertThrows(IllegalArgumentException.class, () -> PlaylistName.display("   "));
        assertThrows(IllegalArgumentException.class, () -> PlaylistName.display("x".repeat(41)));
    }

    @Test
    void rejectsControlCharacters() {
        assertThrows(IllegalArgumentException.class, () -> PlaylistName.display("rock\u0000roll"));
    }
}
