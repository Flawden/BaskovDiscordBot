package ru.flawden.BascovDiscordBot.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersistencePropertiesTest {

    @Test
    void exposesPortableLocalDefaultAndAcceptsContainerPath() {
        PersistenceProperties properties = new PersistenceProperties();
        assertEquals(Path.of("data", "guild-settings.properties"), properties.getFile());

        Path containerPath = Path.of("/app/data/guild-settings.properties");
        properties.setFile(containerPath);
        assertEquals(containerPath, properties.getFile());
        assertThrows(NullPointerException.class, () -> properties.setFile(null));
    }
}
