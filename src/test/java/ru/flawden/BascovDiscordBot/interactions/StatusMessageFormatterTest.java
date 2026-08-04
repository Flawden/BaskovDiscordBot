package ru.flawden.BascovDiscordBot.interactions;

import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.operations.MusicRuntimeSnapshot;
import ru.flawden.BascovDiscordBot.operations.OperationalMetrics;
import ru.flawden.BascovDiscordBot.operations.RuntimeHealthMonitor;
import ru.flawden.BascovDiscordBot.operations.VoiceDiagnosticSnapshot;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusMessageFormatterTest {

    @Test
    void formatsDiscordSectionWithExplicitLineBreaks() {
        RuntimeHealthMonitor.Snapshot snapshot = new RuntimeHealthMonitor.Snapshot(
                "CONNECTED", 3, 18, Instant.parse("2026-08-04T05:30:00Z"));

        assertEquals("""
                Статус: `CONNECTED`
                Серверов: `3`
                Slash-команд: `18`""", StatusMessageFormatter.discord(snapshot));
    }

    @Test
    void formatsMusicSectionWithExplicitLineBreaks() {
        MusicRuntimeSnapshot snapshot = new MusicRuntimeSnapshot(2, 1, 7);

        assertEquals("""
                Активных сессий: `2`
                Сейчас играет: `1`
                Треков в очередях: `7`""", StatusMessageFormatter.music(snapshot));
    }

    @Test
    void formatsVoiceTransportAndHistorySeparately() {
        VoiceDiagnosticSnapshot snapshot = new VoiceDiagnosticSnapshot(
                "bridge",
                "CONNECTED",
                "99",
                true,
                true,
                true,
                "Brain Stew",
                25L,
                Duration.ofMillis(40),
                1L,
                1L,
                0L,
                1L,
                0L,
                1L,
                2L,
                1L,
                "join",
                "none",
                "HTTP 404",
                false);

        assertEquals("""
                Сеть: `bridge`
                Control: `CONNECTED`
                Self channel: `99`
                AudioManager: `CONNECTED`
                Frame polling: `25 calls, age=40ms`
                Watchdog: `OBSERVE`""", StatusMessageFormatter.voice(snapshot));
        assertTrue(StatusMessageFormatter.voiceHistory(snapshot).contains("HTTP 404"));
        assertTrue(StatusMessageFormatter.voiceHistory(snapshot).contains("1/1/0"));
    }

    @Test
    void formatsCommandCountersWithoutPersonalData() {
        OperationalMetrics.Snapshot snapshot = new OperationalMetrics.Snapshot(
                Instant.parse("2026-08-04T05:00:00Z"),
                Duration.ofMinutes(30),
                4, 1,
                6, 2,
                8, 3);

        assertEquals("""
                Успешно: `18`
                Ошибок: `6`
                Prefix/Slash/Buttons: `4/6/8`""", StatusMessageFormatter.commands(snapshot));
    }
}
