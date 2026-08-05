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
import ru.flawden.BascovDiscordBot.operations.RuntimeHealthMonitor;
import ru.flawden.BascovDiscordBot.operations.VoiceDiagnosticSnapshot;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatusMessageFormatterTest {

    @Test
    void formatsDiscordSectionWithExplicitLineBreaks() {
        RuntimeHealthMonitor.Snapshot snapshot = new RuntimeHealthMonitor.Snapshot(
                "CONNECTED", 3, 18, Instant.parse("2026-08-04T05:30:00Z"));

        assertEquals("""
                Статус: `CONNECTED`
                JDA: `6.5.0`
                Серверов: `3`
                Slash-команд: `18`""", StatusMessageFormatter.discord(snapshot, "6.5.0"));
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
                Активных сессий: `2`
                Сейчас играет: `1`
                Треков в очередях: `7`""", StatusMessageFormatter.music(snapshot));
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
                8, 3);

        assertEquals("""
                Успешно: `18`
                Ошибок: `6`
                Prefix/Slash/Buttons: `4/6/8`""", StatusMessageFormatter.commands(snapshot));
    }
}
