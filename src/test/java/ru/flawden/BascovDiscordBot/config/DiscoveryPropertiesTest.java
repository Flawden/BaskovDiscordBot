package ru.flawden.BascovDiscordBot.config;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryPropertiesTest {

    @Test
    void lastfmIsOptionalAndBecomesEnabledWithKey() {
        DiscoveryProperties properties = new DiscoveryProperties();
        assertFalse(properties.lastfmEnabled());
        properties.setLastfmApiKey("secret-key");
        assertTrue(properties.lastfmEnabled());
    }

    @Test
    void rejectsUnsafeBaseUrlAndUnboundedValues() {
        DiscoveryProperties properties = new DiscoveryProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.setLastfmBaseUrl(URI.create("http://example.test")));
        assertThrows(IllegalArgumentException.class, () -> properties.setListenbrainzBaseUrl(URI.create("http://example.test")));
        assertThrows(IllegalArgumentException.class, () -> properties.setRequestTimeout(Duration.ofSeconds(30)));
        assertThrows(IllegalArgumentException.class, () -> properties.setCandidateLimit(101));
        assertThrows(IllegalArgumentException.class, () -> properties.setCollaborativeArtistLimit(51));
        assertThrows(IllegalArgumentException.class, () -> properties.setListenbrainzRadioMode("wild"));
    }

    @Test
    void listenbrainzIsOptionalAndBecomesEnabledWithToken() {
        DiscoveryProperties properties = new DiscoveryProperties();
        assertFalse(properties.listenbrainzEnabled());
        properties.setListenbrainzToken("token");
        assertTrue(properties.listenbrainzEnabled());
        properties.setListenbrainzRadioMode("hard");
        assertTrue("hard".equals(properties.getListenbrainzRadioMode()));
    }
}
