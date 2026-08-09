package ru.flawden.BascovDiscordBot.interactions;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import ru.flawden.BascovDiscordBot.dave.DaveRuntimeInfo;
import ru.flawden.BascovDiscordBot.lavaplayer.GuildMusicManager;
import ru.flawden.BascovDiscordBot.lavaplayer.YoutubeSourceRuntimeInfo;
import ru.flawden.BascovDiscordBot.operations.MusicRuntimeSnapshot;
import ru.flawden.BascovDiscordBot.operations.OperationalMetrics;
import ru.flawden.BascovDiscordBot.operations.PersistenceBackupService;
import ru.flawden.BascovDiscordBot.operations.PersistenceReadiness;
import ru.flawden.BascovDiscordBot.operations.RuntimeHealthMonitor;
import ru.flawden.BascovDiscordBot.operations.VoiceDiagnosticSnapshot;
import ru.flawden.BascovDiscordBot.session.SessionRecoverySnapshot;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Formats compact multi-line /status sections without exposing secrets or
 * absolute persistence paths.
 */
final class StatusMessageFormatter {

    private StatusMessageFormatter() {
    }

    static String discord(RuntimeHealthMonitor.Snapshot runtime, String jdaVersion) {
        Objects.requireNonNull(runtime, "runtime");
        return String.join("\n",
                "Статус: `" + runtime.jdaStatus() + "`",
                "JDA: `" + (jdaVersion == null ? "unknown" : jdaVersion) + "`",
                "Серверов: `" + runtime.guildCount() + "`",
                "Slash-команд: `" + runtime.registeredSlashCommands() + "`",
                "Gateway transitions: `" + runtime.gatewayStatusTransitions() + "`",
                "Disconnected samples: `" + runtime.disconnectedHeartbeatSamples() + "`",
                "Последний CONNECTED: " + discordTime(runtime.lastConnectedAt()));
    }

    static String dave(DaveRuntimeInfo.Snapshot dave) {
        Objects.requireNonNull(dave, "dave");
        return String.join("\n",
                "Статус: `" + dave.status() + "`",
                "Реализация: `" + dave.implementation() + " "
                        + dave.implementationVersion() + "`",
                "Max protocol: `" + dave.maxProtocolVersion() + "`",
                "Native: `" + dave.platform() + "`",
                "Ошибка: " + inline(dave.error()));
    }

    static String music(MusicRuntimeSnapshot music) {
        Objects.requireNonNull(music, "music");
        return String.join("\n",
                "Основной поиск: `YouTube`",
                "YouTube engine: `" + YoutubeSourceRuntimeInfo.statusLabel() + "`",
                "Активных сессий: `" + music.activeSessions() + "`",
                "Сейчас играет: `" + music.playingSessions() + "`",
                "Треков в очередях: `" + music.queuedTracks() + "`");
    }

    static String recovery(SessionRecoverySnapshot recovery) {
        Objects.requireNonNull(recovery, "recovery");
        return String.join("\n",
                "Checkpoint-сессий: `" + recovery.persistedSessions() + "`",
                "Восстановлений сейчас: `" + recovery.recoveriesInProgress() + "`",
                "Transport A/S/F: `" + recovery.transportAttempts() + "/"
                        + recovery.transportSuccesses() + "/" + recovery.transportFailures() + "`",
                "Startup restored/failed: `" + recovery.startupRestoreSuccesses() + "/"
                        + recovery.startupRestoreFailures() + "`",
                "Previous restored/failed: `" + recovery.startupHistoryTracksRestored() + "/"
                        + recovery.startupHistoryTrackFailures() + "`",
                "Последнее событие: " + inline(recovery.lastEvent()));
    }

    static String playback(GuildMusicManager manager) {
        if (manager == null || !manager.isActive()) {
            return String.join("\n",
                    "Сессия: `INACTIVE`",
                    "Повтор: `—`",
                    "Громкость: `—`",
                    "Предыдущих: `0`",
                    "Seek: `—`");
        }

        AudioPlayer player = manager.getAudioPlayer();
        AudioTrack track = player.getPlayingTrack();
        String state = track == null ? "IDLE" : player.isPaused() ? "PAUSED" : "PLAYING";
        String seek = track == null ? "—" : track.isSeekable() ? "READY" : "UNAVAILABLE";
        return String.join("\n",
                "Сессия: `" + state + "`",
                "Повтор: `" + manager.getScheduler().getRepeatMode().label() + "`",
                "Громкость: `" + player.getVolume() + "%`",
                "Предыдущих: `" + manager.getScheduler().historySize() + "`",
                "Seek: `" + seek + "`");
    }

    static String voice(VoiceDiagnosticSnapshot voice) {
        Objects.requireNonNull(voice, "voice");
        return String.join("\n",
                "Сеть: `" + voice.networkMode() + "`",
                "Control: `" + voice.controlState() + "`",
                "Self channel: `" + voice.voiceChannelId() + "`",
                "AudioManager: `" + (voice.audioManagerConnected() ? "CONNECTED" : "DISCONNECTED") + "`",
                "Frame polling: `" + frameState(voice) + "`",
                "Watchdog: `" + (voice.watchdogEnforced() ? "ENFORCE" : "OBSERVE") + "`");
    }

    static String voiceHistory(VoiceDiagnosticSnapshot voice) {
        Objects.requireNonNull(voice, "voice");
        return String.join("\n",
                "Трек: `" + voice.currentTrack() + "`",
                "Attempts/Join/Leave: `" + voice.connectionAttempts() + "/"
                        + voice.selfJoinEvents() + "/" + voice.selfLeaveEvents() + "`",
                "Source/Cleanup/Fallback/Stale: `" + voice.trackExceptions() + "/"
                        + voice.cleanupEvents() + "/" + voice.fallbackAttempts() + "/"
                        + voice.staleCallbacks() + "`",
                "Watchdog warnings: `" + voice.watchdogWarnings() + "`",
                "Last voice: " + inline(voice.lastVoiceEvent()),
                "Last voice error: " + inline(voice.lastVoiceError()),
                "Last source error: " + inline(voice.lastSourceError()),
                "Last recovery: " + inline(voice.lastRecoveryEvent()),
                "Last stale callback: " + inline(voice.lastStaleCallback()));
    }

    static String storage(PersistenceReadiness.Snapshot storage) {
        Objects.requireNonNull(storage, "storage");
        return String.join("\n",
                "Статус: `" + storage.status() + "`",
                "Хранилищ: `" + storage.stores() + "`",
                "Файлов при старте: `" + storage.existingFiles() + "`",
                "Проверено: " + discordTime(storage.checkedAt()));
    }

    static String backups(PersistenceBackupService.Snapshot backup) {
        Objects.requireNonNull(backup, "backup");
        return String.join("\n",
                "Статус: `" + backup.status() + "`",
                "Success/Fail: `" + backup.successfulBackups() + "/" + backup.failedBackups() + "`",
                "Retention: `" + backup.retention() + "`",
                "Последний backup: " + discordTime(backup.lastSuccessAt()),
                "Файлов в snapshot: `" + backup.lastIncludedStores() + "/4`");
    }

    static String commands(OperationalMetrics.Snapshot commands) {
        Objects.requireNonNull(commands, "commands");
        return String.join("\n",
                "Успешно: `" + commands.totalSuccesses() + "`",
                "Ошибок: `" + commands.totalFailures() + "`",
                "Всего: `" + commands.totalInvocations() + "`",
                "Failure rate: `" + String.format(Locale.ROOT, "%.2f%%", commands.failureRatePercent()) + "`",
                "Prefix/Slash/Buttons: `"
                        + commands.prefixSuccesses() + "/"
                        + commands.slashSuccesses() + "/"
                        + commands.buttonSuccesses() + "`",
                "Последняя ошибка: " + discordTime(commands.lastFailureAt()));
    }

    static String reliability(
            RuntimeHealthMonitor.Snapshot runtime,
            PersistenceReadiness.Snapshot storage,
            PersistenceBackupService.Snapshot backup,
            SessionRecoverySnapshot recovery) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(backup, "backup");
        Objects.requireNonNull(recovery, "recovery");

        boolean connected = "CONNECTED".equals(runtime.jdaStatus());
        boolean ready = connected && storage.ready() && backup.healthy();
        return String.join("\n",
                "Итог: `" + (ready ? "READY" : "DEGRADED") + "`",
                "Gateway: `" + (connected ? "READY" : "DEGRADED") + "`",
                "Storage: `" + storage.status() + "`",
                "Backups: `" + backup.status() + "`",
                "Recovery failures: `" + recovery.transportFailures() + "`");
    }

    private static String frameState(VoiceDiagnosticSnapshot voice) {
        if (voice.frameRequestCount() == 0L) {
            return "never";
        }
        long ageMillis = voice.lastFrameRequestAge() == null
                ? -1L
                : voice.lastFrameRequestAge().toMillis();
        return voice.frameRequestCount() + " calls, age=" + ageMillis + "ms";
    }

    private static String inline(String value) {
        String safe = value == null ? "none" : value.replace("`", "'");
        return "`" + safe + "`";
    }

    private static String discordTime(Instant instant) {
        return instant == null ? "`never`" : "<t:" + instant.getEpochSecond() + ":R>";
    }
}
