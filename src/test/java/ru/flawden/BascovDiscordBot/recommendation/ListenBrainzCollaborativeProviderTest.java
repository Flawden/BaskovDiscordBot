package ru.flawden.BascovDiscordBot.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.config.DiscoveryProperties;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListenBrainzCollaborativeProviderTest {

    @Test
    void providerIsOptionalAndUsesBoundedHttpsEndpoints() {
        DiscoveryProperties properties = new DiscoveryProperties();
        ListenBrainzCollaborativeProvider provider = new ListenBrainzCollaborativeProvider(properties, new ObjectMapper());
        try {
            assertFalse(provider.available());
            properties.setListenbrainzToken("token");
            assertTrue(provider.available());
            URI lookup = provider.buildLookupUri("Green Day", "Holiday");
            URI radio = provider.buildArtistRadioUri("1234-5678");
            assertTrue(lookup.toString().startsWith("https://api.listenbrainz.org/1/metadata/lookup/"));
            assertTrue(lookup.toString().contains("Green%20Day"));
            assertTrue(radio.toString().contains("/1/lb-radio/artist/1234-5678"));
            assertTrue(radio.toString().contains("mode=medium"));
            assertTrue(radio.toString().contains("max_similar_artists=12"));
        } finally {
            provider.close();
        }
    }

    @Test
    void parsesNestedArtistRadioPayloadIntoNormalizedAffinity() throws Exception {
        DiscoveryProperties properties = new DiscoveryProperties();
        properties.setListenbrainzToken("token");
        ListenBrainzCollaborativeProvider provider = new ListenBrainzCollaborativeProvider(properties, new ObjectMapper());
        try {
            CollaborativeArtistSignals signals = provider.parseArtistSignals("""
                    {"payload":{"artists":[
                      {"similar_artist_name":"Jimmy Eat World","total_listen_count":10000},
                      {"similar_artist_name":"Sum 41","total_listen_count":1000},
                      {"similar_artist_name":"Jimmy Eat World","total_listen_count":9000}
                    ]}}
                    """);
            assertEquals("ListenBrainz", signals.source());
            assertEquals(1.0d, signals.affinity("jimmy eat world"), 0.0001d);
            assertTrue(signals.affinity("sum 41") > 0.0d);
            assertTrue(signals.affinity("sum 41") < 1.0d);
        } finally {
            provider.close();
        }
    }
}
