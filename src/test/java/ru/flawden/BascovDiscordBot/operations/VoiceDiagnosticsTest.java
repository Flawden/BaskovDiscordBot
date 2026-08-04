package ru.flawden.BascovDiscordBot.operations;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.managers.AudioManager;
import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.config.MusicProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VoiceDiagnosticsTest {

    @Test
    void retainsLastFailureAfterMusicSessionHasBeenDestroyed() {
        MusicProperties properties = new MusicProperties();
        VoiceDiagnostics diagnostics = new VoiceDiagnostics(
                "bridge",
                properties,
                Clock.fixed(Instant.parse("2026-08-04T13:00:00Z"), ZoneOffset.UTC));
        Guild guild = guild(false);

        diagnostics.connectionRequested(42L, 99L);
        diagnostics.selfVoiceEvent(42L, "JOIN", 99L);
        diagnostics.sourceFailure(42L, "Brain Stew", "HTTP 404");
        diagnostics.selfVoiceEvent(42L, "LEAVE", 99L);

        VoiceDiagnosticSnapshot snapshot = diagnostics.snapshot(guild, null);

        assertEquals("bridge", snapshot.networkMode());
        assertEquals("DISCONNECTED", snapshot.controlState());
        assertEquals(1L, snapshot.connectionAttempts());
        assertEquals(1L, snapshot.selfJoinEvents());
        assertEquals(1L, snapshot.selfLeaveEvents());
        assertTrue(snapshot.lastSourceError().contains("HTTP 404"));
        assertFalse(snapshot.sessionActive());
    }

    @Test
    void reportsWatchdogAsObserveOnlyByDefault() {
        MusicProperties properties = new MusicProperties();
        VoiceDiagnostics diagnostics = new VoiceDiagnostics(
                "host",
                properties,
                Clock.systemUTC());

        VoiceDiagnosticSnapshot snapshot = diagnostics.snapshot(guild(true), null);

        assertEquals("host", snapshot.networkMode());
        assertFalse(snapshot.watchdogEnforced());
        assertTrue(snapshot.audioManagerConnected());
    }

    private static Guild guild(boolean audioConnected) {
        Guild guild = mock(Guild.class);
        SelfMember self = mock(SelfMember.class);
        GuildVoiceState voiceState = mock(GuildVoiceState.class);
        AudioManager audioManager = mock(AudioManager.class);
        when(guild.getIdLong()).thenReturn(42L);
        when(guild.getSelfMember()).thenReturn(self);
        when(self.getVoiceState()).thenReturn(voiceState);
        when(voiceState.getChannel()).thenReturn(null);
        when(guild.getAudioManager()).thenReturn(audioManager);
        when(audioManager.isConnected()).thenReturn(audioConnected);
        return guild;
    }
}
