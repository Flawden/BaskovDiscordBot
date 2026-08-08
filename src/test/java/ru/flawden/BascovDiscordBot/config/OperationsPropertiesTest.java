package ru.flawden.BascovDiscordBot.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationsPropertiesTest {

    @Test
    void exposesSafeBackupDefaults() {
        OperationsProperties properties = new OperationsProperties();

        assertTrue(properties.isPersistenceBackupEnabled());
        assertEquals(Path.of("data", "backups"), properties.getPersistenceBackupDirectory());
        assertEquals(Duration.ofHours(6), properties.getPersistenceBackupInterval());
        assertEquals(14, properties.getPersistenceBackupRetention());
    }

    @Test
    void rejectsInvalidBackupIntervalsAndRetention() {
        OperationsProperties properties = new OperationsProperties();

        assertThrows(IllegalArgumentException.class,
                () -> properties.setPersistenceBackupInterval(Duration.ofSeconds(59)));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setPersistenceBackupInterval(Duration.ofDays(8)));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setPersistenceBackupRetention(0));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setPersistenceBackupRetention(101));
    }
}
