package ru.flawden.BascovDiscordBot.interactions;

import ru.flawden.BascovDiscordBot.operations.MusicRuntimeSnapshot;
import ru.flawden.BascovDiscordBot.operations.OperationalMetrics;
import ru.flawden.BascovDiscordBot.operations.RuntimeHealthMonitor;

import java.util.Objects;

/**
 * Формирует компактные многострочные секции команды /status.
 */
final class StatusMessageFormatter {

    private StatusMessageFormatter() {
    }

    static String discord(RuntimeHealthMonitor.Snapshot runtime) {
        Objects.requireNonNull(runtime, "runtime");
        return String.join("\n",
                "Статус: `" + runtime.jdaStatus() + "`",
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
