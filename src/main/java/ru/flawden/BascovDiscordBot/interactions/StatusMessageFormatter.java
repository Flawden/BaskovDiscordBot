package ru.flawden.BascovDiscordBot.interactions;

import ru.flawden.BascovDiscordBot.operations.MusicRuntimeSnapshot;
import ru.flawden.BascovDiscordBot.operations.OperationalMetrics;
import ru.flawden.BascovDiscordBot.operations.RuntimeHealthMonitor;
import ru.flawden.BascovDiscordBot.operations.VoiceDiagnosticSnapshot;

import java.util.Objects;

/**
 * Формирует компактные многострочные секции команды /status.
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
                "Slash-команд: `" + runtime.registeredSlashCommands() + "`");
    }

    static String music(MusicRuntimeSnapshot music) {
        Objects.requireNonNull(music, "music");
        return String.join("\n",
                "Активных сессий: `" + music.activeSessions() + "`",
                "Сейчас играет: `" + music.playingSessions() + "`",
                "Треков в очередях: `" + music.queuedTracks() + "`");
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
                "Last source error: " + inline(voice.lastSourceError()));
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

    static String commands(OperationalMetrics.Snapshot commands) {
        Objects.requireNonNull(commands, "commands");
        return String.join("\n",
                "Успешно: `" + commands.totalSuccesses() + "`",
                "Ошибок: `" + commands.totalFailures() + "`",
                "Prefix/Slash/Buttons: `"
                        + commands.prefixSuccesses() + "/"
                        + commands.slashSuccesses() + "/"
                        + commands.buttonSuccesses() + "`");
    }
}
