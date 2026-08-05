package ru.flawden.BascovDiscordBot.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MusicLibraryPropertiesTest {

    @Test
    void usesDedicatedDataFileAndRejectsNull() {
        MusicLibraryProperties properties = new MusicLibraryProperties();
        assertEquals(Path.of("data", "music-library.tsv"), properties.getFile());
        assertThrows(NullPointerException.class, () -> properties.setFile(null));
    }
}
