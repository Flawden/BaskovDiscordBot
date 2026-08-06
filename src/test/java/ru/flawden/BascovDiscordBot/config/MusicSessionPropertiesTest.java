package ru.flawden.BascovDiscordBot.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicSessionPropertiesTest {

    @Test
    void usesSafeRecoveryDefaults() {
        MusicSessionProperties properties = new MusicSessionProperties();

        assertEquals(Path.of("data", "music-sessions.tsv"), properties.getFile());
        assertEquals(Duration.ofSeconds(5), properties.getCheckpointInterval());
        assertEquals(Duration.ofHours(6), properties.getMaxAge());
        assertEquals(3, properties.getMaxRecoveryAttempts());
        assertEquals(Duration.ofSeconds(2), properties.getRecoveryBackoff());
        assertTrue(properties.isRestoreOnStartup());
        assertTrue(properties.isRequireHumanListener());
        assertTrue(properties.isVoiceRecoveryEnabled());
    }

    @Test
    void rejectsUnboundedRecoveryConfiguration() {
        MusicSessionProperties properties = new MusicSessionProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setMaxRecoveryAttempts(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxRecoveryAttempts(11));
        assertThrows(IllegalArgumentException.class, () -> properties.setRecoveryBackoff(Duration.ofMinutes(2)));
        assertThrows(IllegalArgumentException.class, () -> properties.setCheckpointInterval(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setCheckpointInterval(Duration.ofNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxAge(Duration.ofDays(8)));
    }
}
