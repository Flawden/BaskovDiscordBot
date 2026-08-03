package ru.flawden.BascovDiscordBot.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MusicPropertiesTest {

    @Test
    void exposesSafeProductionDefaults() {
        MusicProperties properties = new MusicProperties();

        assertEquals(100, properties.getMaxQueueSize());
        assertEquals(Duration.ofHours(4), properties.getMaxTrackDuration());
        assertEquals(Duration.ofMinutes(5), properties.getIdleDisconnectTimeout());
    }

    @Test
    void rejectsUnboundedOrInvalidValues() {
        MusicProperties properties = new MusicProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setMaxQueueSize(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxQueueSize(1_001));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setMaxTrackDuration(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setIdleDisconnectTimeout(Duration.ofSeconds(-1)));
    }
}
