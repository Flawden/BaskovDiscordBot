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
        assertEquals(Duration.ofSeconds(15), properties.getVoiceConnectTimeout());
        assertEquals(Duration.ofSeconds(30), properties.getVoiceFailureCooldown());
        assertEquals(Duration.ofSeconds(5), properties.getVoiceDisconnectGrace());
        assertEquals(100, properties.getDefaultVolume());
        assertEquals(150, properties.getMaxVolume());
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
        assertThrows(IllegalArgumentException.class,
                () -> properties.setVoiceConnectTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setVoiceConnectTimeout(Duration.ofMinutes(3)));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setVoiceFailureCooldown(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setVoiceFailureCooldown(Duration.ofMinutes(11)));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setVoiceDisconnectGrace(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setVoiceDisconnectGrace(Duration.ofMinutes(2)));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxVolume(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxVolume(501));
        assertThrows(IllegalArgumentException.class, () -> properties.setDefaultVolume(-1));
        assertThrows(IllegalArgumentException.class, () -> properties.setDefaultVolume(151));
    }
}
