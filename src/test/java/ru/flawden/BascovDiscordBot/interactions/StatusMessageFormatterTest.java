package ru.flawden.BascovDiscordBot.interactions;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.junit.jupiter.api.Test;
import ru.flawden.BascovDiscordBot.dave.DaveRuntimeInfo;
import ru.flawden.BascovDiscordBot.lavaplayer.GuildMusicManager;
import ru.flawden.BascovDiscordBot.lavaplayer.RepeatMode;
import ru.flawden.BascovDiscordBot.lavaplayer.TrackScheduler;
import ru.flawden.BascovDiscordBot.operations.MusicRuntimeSnapshot;
import ru.flawden.BascovDiscordBot.operations.OperationalMetrics;
import ru.flawden.BascovDiscordBot.operations.PersistenceBackupService;
import ru.flawden.BascovDiscordBot.operations.PersistenceReadiness;
import ru.flawden.BascovDiscordBot.operations.RuntimeHealthMonitor;
import ru.flawden.BascovDiscordBot.operations.VoiceDiagnosticSnapshot;
import ru.flawden.BascovDiscordBot.session.SessionRecoverySnapshot;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatusMessageFormatterTest {

    @Test
    void formatsDiscordSectionWithExplicitLineBreaks() {
        RuntimeHealthMonitor.Snapshot snapshot = new RuntimeHealthMonitor.Snapshot(
                "CONNECTED",
                3,
                18,
                Instant.parse("2026-08-04T05:30:00Z"),
                Instant.parse("2026-08-04T05:30:00Z"),
                Instant.parse("2026-08-04T05:20:00Z"),
                2L,
                1L);

        String rendered = StatusMessageFormatter.discord(snapshot, "6.5.0");
        assertTrue(rendered.contains("Статус: `CONNECTED`"));
        assertTrue(rendered.contains("JDA: `6.5.0`"));
        assertTrue(rendered.contains("Серверов: `3`"));
        assertTrue(rendered.contains("Slash-команд: `18`"));
        assertTrue(rendered.contains("Gateway transitions: `2`"));
        assertTrue(rendered.contains("Disconnected samples: `1`"));
    }

    @Test
    void formatsNativeDaveSection() {
        DaveRuntimeInfo info = new DaveRuntimeInfo();
        info.ready(1);

        String rendered = StatusMessageFormatter.dave(info.snapshot());

        assertTrue(rendered.contains("Статус: `READY`"));
        assertTrue(rendered.contains("Реализация: `libdave-jvm ce725965e`"));
        assertTrue(rendered.contains("Max protocol: `1`"));
        assertTrue(rendered.contains("Ошибка: `none`"));
    }

    @Test
    void formatsMusicSectionWithExplicitLineBreaks() {
        MusicRuntimeSnapshot snapshot = new MusicRuntimeSnapshot(2, 1, 7);

        assertEquals("""
                Основной поиск: `YouTube`
                YouTube engine: `youtube-source 1.18.2`
                Активных сессий: `2`
                Сейчас играет: `1`
                Треков в очередях: `7`""", StatusMessageFormatter.music(snapshot));
    }

    @Test
    void formatsVoiceRecoveryCheckpointSection() {
        SessionRecoverySnapshot snapshot = new SessionRecoverySnapshot(
                2,
                1,
                4L,
                3L,
                1L,
                2L,
                1L,
                5L,
                1L,
                "2026-08-06T02:00:00Z voice recovery complete");

        String rendered = StatusMessageFormatter.recovery(snapshot);

        assertTrue(rendered.contains("Checkpoint-сессий: `2`"));
        assertTrue(rendered.contains("Transport A/S/F: `4/3/1`"));
        assertTrue(rendered.contains("Startup restored/failed: `2/1`"));
        assertTrue(rendered.contains("Previous restored/failed: `5/1`"));
        assertTrue(rendered.contains("voice recovery complete"));
    }

    @Test
    void formatsGuildPlaybackModesAndHistory() {
        GuildMusicManager manager = mock(GuildMusicManager.class);
        AudioPlayer player = mock(AudioPlayer.class);
        AudioTrack track = mock(AudioTrack.class);
        TrackScheduler scheduler = mock(TrackScheduler.class);
        when(manager.isActive()).thenReturn(true);
        when(manager.getAudioPlayer()).thenReturn(player);
        when(manager.getScheduler()).thenReturn(scheduler);
        when(player.getPlayingTrack()).thenReturn(track);
        when(player.getVolume()).thenReturn(85);
        when(player.isPaused()).thenReturn(true);
        when(track.isSeekable()).thenReturn(true);
        when(scheduler.getRepeatMode()).thenReturn(RepeatMode.QUEUE);
        when(scheduler.historySize()).thenReturn(3);

        assertEquals("""
                Сессия: `PAUSED`
                Повтор: `Вся очередь`
                Громкость: `85%`
                Предыдущих: `3`
                Seek: `READY`""", StatusMessageFormatter.playback(manager));
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
                "fallback: primary -> backup",
                "stale end:REPLACED",
                false);

        assertEquals("""
                Сеть: `bridge`
                Control: `CONNECTED`
                Self channel: `99`
                AudioManager: `CONNECTED`
                Frame polling: `25 calls, age=40ms`
                Watchdog: `OBSERVE`""", StatusMessageFormatter.voice(snapshot));
        assertTrue(StatusMessageFormatter.voiceHistory(snapshot).contains("HTTP 404"));
        assertTrue(StatusMessageFormatter.voiceHistory(snapshot).contains("fallback: primary -> backup"));
        assertTrue(StatusMessageFormatter.voiceHistory(snapshot).contains("stale end:REPLACED"));
        assertTrue(StatusMessageFormatter.voiceHistory(snapshot).contains("1/1/0"));
    }

    @Test
    void formatsCommandCountersWithoutPersonalData() {
        OperationalMetrics.Snapshot snapshot = new OperationalMetrics.Snapshot(
                Instant.parse("2026-08-04T05:00:00Z"),
                Duration.ofMinutes(30),
                4, 1,
                6, 2,
                8, 3,
                Instant.parse("2026-08-04T05:29:00Z"),
                Instant.parse("2026-08-04T05:25:00Z"));

        String rendered = StatusMessageFormatter.commands(snapshot);
        assertTrue(rendered.contains("Успешно: `18`"));
        assertTrue(rendered.contains("Ошибок: `6`"));
        assertTrue(rendered.contains("Всего: `24`"));
        assertTrue(rendered.contains("Failure rate: `25.00%`"));
        assertTrue(rendered.contains("Prefix/Slash/Buttons: `4/6/8`"));
    }

    @Test
    void storageSectionDoesNotExposePaths() {
        String section = StatusMessageFormatter.storage(new PersistenceReadiness.Snapshot(
                "READY",
                3,
                2,
                java.time.Instant.parse("2026-08-08T00:00:00Z"),
                "ready"));

        assertTrue(section.contains("Статус: `READY`"));
        assertTrue(section.contains("Хранилищ: `3`"));
        assertFalse(section.contains("/app/data"));
    }
    @Test
    void formatsBackupAndReliabilitySectionsWithoutAbsolutePaths() {
        PersistenceBackupService.Snapshot backup = new PersistenceBackupService.Snapshot(
                "READY",
                true,
                Duration.ofHours(6),
                14,
                3L,
                1L,
                Instant.parse("2026-08-08T12:00:00Z"),
                Instant.parse("2026-08-08T06:00:00Z"),
                "baskov-persistence-20260808-120000-000.zip",
                3,
                "ready");
        PersistenceReadiness.Snapshot storage = new PersistenceReadiness.Snapshot(
                "READY",
                3,
                3,
                Instant.parse("2026-08-08T00:00:00Z"),
                "ready");
        RuntimeHealthMonitor.Snapshot runtime = new RuntimeHealthMonitor.Snapshot(
                "CONNECTED", 2, 20,
                Instant.parse("2026-08-08T12:00:00Z"),
                Instant.parse("2026-08-08T12:00:00Z"),
                Instant.parse("2026-08-08T11:50:00Z"),
                1L,
                0L);
        SessionRecoverySnapshot recovery = new SessionRecoverySnapshot(
                1, 0, 4L, 4L, 0L, 1L, 0L, 3L, 0L, "ready");

        String backupSection = StatusMessageFormatter.backups(backup);
        String reliabilitySection = StatusMessageFormatter.reliability(runtime, storage, backup, recovery);

        assertTrue(backupSection.contains("Статус: `READY`"));
        assertTrue(backupSection.contains("Success/Fail: `3/1`"));
        assertTrue(backupSection.contains("Retention: `14`"));
        assertFalse(backupSection.contains("/app/data"));
        assertTrue(reliabilitySection.contains("Итог: `READY`"));
        assertTrue(reliabilitySection.contains("Recovery failures: `0`"));
    }

}
