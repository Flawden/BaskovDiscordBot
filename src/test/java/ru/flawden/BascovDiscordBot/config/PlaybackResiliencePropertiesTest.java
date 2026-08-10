package ru.flawden.BascovDiscordBot.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaybackResiliencePropertiesTest {

    @Test
    void defaultsAreBoundedAndConservative() {
        PlaybackResilienceProperties properties = new PlaybackResilienceProperties();
        assertEquals(3, properties.getFailureThreshold());
        assertEquals(Duration.ofSeconds(90), properties.getCooldown());
    }

    @Test
    void rejectsInvalidThreshold() {
        PlaybackResilienceProperties properties = new PlaybackResilienceProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.setFailureThreshold(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setFailureThreshold(11));
    }

    @Test
    void rejectsUnboundedCooldown() {
        PlaybackResilienceProperties properties = new PlaybackResilienceProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.setCooldown(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> properties.setCooldown(Duration.ofHours(1)));
    }
}
