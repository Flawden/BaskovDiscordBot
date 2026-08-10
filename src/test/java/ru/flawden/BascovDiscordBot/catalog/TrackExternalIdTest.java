package ru.flawden.BascovDiscordBot.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrackExternalIdTest {

    @Test
    void musicBrainzRecordingIdIsCanonicalLowerCase() {
        TrackExternalId id = TrackExternalId.musicBrainzRecording(" 550E8400-E29B-41D4-A716-446655440000 ");

        assertEquals(TrackExternalId.Namespace.MUSICBRAINZ_RECORDING, id.namespace());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", id.value());
    }

    @Test
    void isrcFormattingIsNormalizedWithoutChangingIdentitySemantics() {
        TrackExternalId id = TrackExternalId.isrc(" us-ab1-23-45678 ");

        assertEquals(TrackExternalId.Namespace.ISRC, id.namespace());
        assertEquals("USAB12345678", id.value());
    }

    @Test
    void blankExternalIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TrackExternalId.isrc("   "));
    }
}
