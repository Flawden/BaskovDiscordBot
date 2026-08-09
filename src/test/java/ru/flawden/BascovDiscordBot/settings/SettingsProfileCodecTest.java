package ru.flawden.BascovDiscordBot.settings;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsProfileCodecTest {

    @Test
    void roundTripsAllAdministrationAndModerationSettingsAsV2() {
        GuildPreferences source = new GuildPreferences(
                63,
                RepeatMode.QUEUE,
                PlaybackAccessMode.VOTE_SKIP,
                RequestAccessMode.DJ_ONLY,
                11L,
                22L,
                33L,
                44L,
                67,
                7);

        String encoded = SettingsProfileCodec.encode(source);

        assertTrue(encoded.startsWith("BASKOV_SETTINGS_V2."));
        assertEquals(source, SettingsProfileCodec.decode(encoded));
    }

    @Test
    void importsLegacyV1WithSafeModerationDefaults() {
        String legacyBody = String.join("\n",
                "volume=63",
                "repeat=QUEUE",
                "playbackAccess=VOTE_SKIP",
                "requestAccess=DJ_ONLY",
                "djRole=11",
                "managerRole=22",
                "musicChannel=44",
                "voteSkipPercent=67");
        String encoded = "BASKOV_SETTINGS_V1." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(legacyBody.getBytes(StandardCharsets.UTF_8));

        assertEquals(new GuildPreferences(
                63,
                RepeatMode.QUEUE,
                PlaybackAccessMode.VOTE_SKIP,
                RequestAccessMode.DJ_ONLY,
                11L,
                22L,
                0L,
                44L,
                67,
                0), SettingsProfileCodec.decode(encoded));
    }

    @Test
    void rejectsUnknownOrCorruptedProfiles() {
        assertThrows(IllegalArgumentException.class, () -> SettingsProfileCodec.decode("BASKOV_SETTINGS_V0.deadbeef"));
        assertThrows(IllegalArgumentException.class, () -> SettingsProfileCodec.decode("BASKOV_SETTINGS_V2.not_base64!"));
    }
}
