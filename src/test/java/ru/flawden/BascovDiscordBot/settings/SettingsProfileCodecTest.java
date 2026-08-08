package ru.flawden.BascovDiscordBot.settings;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsProfileCodecTest {

    @Test
    void roundTripsAllAdministrationSettings() {
        GuildPreferences source = new GuildPreferences(
                63,
                RepeatMode.QUEUE,
                PlaybackAccessMode.VOTE_SKIP,
                RequestAccessMode.DJ_ONLY,
                11L,
                22L,
                33L,
                67);

        String encoded = SettingsProfileCodec.encode(source);

        assertTrue(encoded.startsWith("BASKOV_SETTINGS_V1."));
        assertEquals(source, SettingsProfileCodec.decode(encoded));
    }

    @Test
    void rejectsUnknownOrCorruptedProfiles() {
        assertThrows(IllegalArgumentException.class, () -> SettingsProfileCodec.decode("BASKOV_SETTINGS_V0.deadbeef"));
        assertThrows(IllegalArgumentException.class, () -> SettingsProfileCodec.decode("BASKOV_SETTINGS_V1.not_base64!"));
    }
}
