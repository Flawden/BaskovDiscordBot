package ru.flawden.BascovDiscordBot.operations;

import net.dv8tion.jda.api.entities.Guild;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.dave.DaveRuntimeInfo;
import ru.flawden.BascovDiscordBot.lavaplayer.PlayerManager;
import ru.flawden.BascovDiscordBot.lavaplayer.YoutubeSourceRuntimeInfo;
import ru.flawden.BascovDiscordBot.playback.PlaybackProviderHealthSnapshot;
import ru.flawden.BascovDiscordBot.playback.PlaybackProviderStatus;
import ru.flawden.BascovDiscordBot.session.SessionRecoverySnapshot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Aggregates existing safe runtime signals into an actionable self-diagnostic report.
 * It deliberately performs no external network requests: source health is inferred
 * from the configured engine identity plus recent runtime failures/fallbacks.
 */
@Component
public class SystemDoctor {

    private static final Duration GATEWAY_HEARTBEAT_STALE_AFTER = Duration.ofSeconds(30);
    private static final Duration FRAME_DEMAND_STALE_AFTER = Duration.ofSeconds(15);
    private static final Duration RECENT_FAILURE_WINDOW = Duration.ofMinutes(10);

    private final RuntimeHealthMonitor healthMonitor;
    private final PersistenceReadiness persistenceReadiness;
    private final PersistenceBackupService persistenceBackupService;
    private final OperationalMetrics operationalMetrics;
    private final PlayerManager playerManager;
    private final DaveRuntimeInfo daveRuntimeInfo;
    private final Clock clock;

    @Autowired
    public SystemDoctor(
            RuntimeHealthMonitor healthMonitor,
            PersistenceReadiness persistenceReadiness,
            PersistenceBackupService persistenceBackupService,
            OperationalMetrics operationalMetrics,
            PlayerManager playerManager,
            DaveRuntimeInfo daveRuntimeInfo) {
        this(
                healthMonitor,
                persistenceReadiness,
                persistenceBackupService,
                operationalMetrics,
                playerManager,
                daveRuntimeInfo,
                Clock.systemUTC());
    }

    SystemDoctor(
            RuntimeHealthMonitor healthMonitor,
            PersistenceReadiness persistenceReadiness,
            PersistenceBackupService persistenceBackupService,
            OperationalMetrics operationalMetrics,
            PlayerManager playerManager,
            DaveRuntimeInfo daveRuntimeInfo,
            Clock clock) {
        this.healthMonitor = Objects.requireNonNull(healthMonitor, "healthMonitor");
        this.persistenceReadiness = Objects.requireNonNull(persistenceReadiness, "persistenceReadiness");
        this.persistenceBackupService = Objects.requireNonNull(persistenceBackupService, "persistenceBackupService");
        this.operationalMetrics = Objects.requireNonNull(operationalMetrics, "operationalMetrics");
        this.playerManager = Objects.requireNonNull(playerManager, "playerManager");
        this.daveRuntimeInfo = Objects.requireNonNull(daveRuntimeInfo, "daveRuntimeInfo");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Report diagnose(Guild guild) {
        Objects.requireNonNull(guild, "guild");
        return evaluate(
                clock.instant(),
                healthMonitor.snapshot(),
                daveRuntimeInfo.snapshot(),
                playerManager.voiceDiagnosticsSnapshot(guild),
                playerManager.sessionRecoverySnapshot(),
                persistenceReadiness.probe(),
                persistenceBackupService.snapshot(),
                operationalMetrics.snapshot(),
                playerManager.playbackProviderHealthSnapshots());
    }

    static Report evaluate(
            Instant now,
            RuntimeHealthMonitor.Snapshot runtime,
            DaveRuntimeInfo.Snapshot dave,
            VoiceDiagnosticSnapshot voice,
            SessionRecoverySnapshot recovery,
            PersistenceReadiness.Snapshot storage,
            PersistenceBackupService.Snapshot backups,
            OperationalMetrics.Snapshot commands) {
        return evaluate(
                now,
                runtime,
                dave,
                voice,
                recovery,
                storage,
                backups,
                commands,
                List.of());
    }

    static Report evaluate(
            Instant now,
            RuntimeHealthMonitor.Snapshot runtime,
            DaveRuntimeInfo.Snapshot dave,
            VoiceDiagnosticSnapshot voice,
            SessionRecoverySnapshot recovery,
            PersistenceReadiness.Snapshot storage,
            PersistenceBackupService.Snapshot backups,
            OperationalMetrics.Snapshot commands,
            List<PlaybackProviderHealthSnapshot> providerHealth) {
        Objects.requireNonNull(now, "now");
        List<Check> checks = List.of(
                gatewayCheck(now, runtime),
                daveCheck(dave),
                voiceCheck(voice),
                sourceCheck(now, voice, providerHealth),
                storageCheck(storage),
                backupCheck(backups),
                recoveryCheck(now, recovery),
                commandCheck(now, commands));
        Severity overall = checks.stream()
                .map(Check::severity)
                .max(Severity::compareTo)
                .orElse(Severity.OK);
        return new Report(now, overall, checks);
    }

    private static Check gatewayCheck(Instant now, RuntimeHealthMonitor.Snapshot runtime) {
        if (!"CONNECTED".equals(runtime.jdaStatus())) {
            return check("gateway", Severity.FAIL,
                    "Discord gateway не подключён",
                    "JDA status=" + runtime.jdaStatus(),
                    "Проверь Discord connectivity, gateway events и последние status transitions.");
        }
        if (runtime.lastHealthyAt() == null
                || Duration.between(runtime.lastHealthyAt(), now).abs().compareTo(GATEWAY_HEARTBEAT_STALE_AFTER) > 0) {
            return check("gateway", Severity.WARN,
                    "Gateway connected, но health heartbeat устарел",
                    "Последний healthy heartbeat=" + timestamp(runtime.lastHealthyAt()),
                    "Проверь поток baskov-runtime-health и нагрузку JVM.");
        }
        return check("gateway", Severity.OK,
                "Discord gateway в норме",
                "CONNECTED, guilds=" + runtime.guildCount() + ", transitions=" + runtime.gatewayStatusTransitions(),
                "Действий не требуется.");
    }

    private static Check daveCheck(DaveRuntimeInfo.Snapshot dave) {
        if (!dave.ready()) {
            return check("dave", Severity.FAIL,
                    "DAVE / E2EE не готов",
                    "status=" + dave.status() + ", error=" + safe(dave.error()),
                    "Проверь native libDAVE и совместимость платформы перед voice playback.");
        }
        return check("dave", Severity.OK,
                "DAVE / E2EE готов",
                dave.implementation() + " " + dave.implementationVersion() + ", protocol=" + dave.maxProtocolVersion(),
                "Действий не требуется.");
    }

    private static Check voiceCheck(VoiceDiagnosticSnapshot voice) {
        if (!voice.sessionActive()) {
            return check("voice", Severity.OK,
                    "Voice transport простаивает",
                    "Активной музыкальной сессии сейчас нет.",
                    "Это нормальное состояние без playback.");
        }
        if (voice.playbackExpected() && !voice.audioManagerConnected()) {
            return check("voice", Severity.FAIL,
                    "Playback ожидается, но Discord AudioManager не подключён",
                    "control=" + voice.controlState() + ", channel=" + voice.voiceChannelId(),
                    "Проверь /session status и voice recovery; при необходимости используй /session recover.");
        }
        if (voice.playbackExpected() && voice.frameRequestCount() == 0L) {
            return check("voice", Severity.WARN,
                    "Playback активен, но Discord ещё не запрашивал audio frames",
                    "frameRequests=0, control=" + voice.controlState(),
                    "Проверь voice connection и подожди несколько секунд; затем повтори /doctor voice.");
        }
        if (voice.playbackExpected()
                && voice.lastFrameRequestAge() != null
                && voice.lastFrameRequestAge().compareTo(FRAME_DEMAND_STALE_AFTER) > 0) {
            return check("voice", Severity.WARN,
                    "Audio frame demand выглядит устаревшим",
                    "Последний frame request был " + voice.lastFrameRequestAge().toSeconds() + " сек. назад.",
                    "Проверь voice transport, watchdog и /session status.");
        }
        return check("voice", Severity.OK,
                "Voice transport в норме",
                "control=" + voice.controlState() + ", frameRequests=" + voice.frameRequestCount(),
                "Действий не требуется.");
    }

    private static Check sourceCheck(
            Instant now,
            VoiceDiagnosticSnapshot voice,
            List<PlaybackProviderHealthSnapshot> providerHealth) {
        List<PlaybackProviderHealthSnapshot> safeHealth = providerHealth == null ? List.of() : providerHealth;
        PlaybackProviderHealthSnapshot coolingDown = safeHealth.stream()
                .filter(snapshot -> snapshot.status() == PlaybackProviderStatus.COOLDOWN)
                .findFirst()
                .orElse(null);
        if (coolingDown != null) {
            return check("source", Severity.WARN,
                    "Playback provider временно в cooldown",
                    providerHealthDetails(safeHealth),
                    "Resolver автоматически использует следующий доступный provider; повторный probe произойдёт после cooldown.");
        }
        PlaybackProviderHealthSnapshot degraded = safeHealth.stream()
                .filter(snapshot -> snapshot.status() == PlaybackProviderStatus.DEGRADED
                        || snapshot.status() == PlaybackProviderStatus.PROBE)
                .findFirst()
                .orElse(null);
        boolean recentSourceFailure = recentEvent(voice.lastSourceError(), now, RECENT_FAILURE_WINDOW);
        if (recentSourceFailure) {
            return check("source", Severity.WARN,
                    "Media source недавно деградировал",
                    providerHealthDetails(safeHealth) + ", lastVoiceSource=" + safe(voice.lastSourceError()),
                    "Повтори запрос; fallback остаётся автоматическим. Если WARN сохраняется, проверь /doctor source после provider probe.");
        }
        if (degraded != null) {
            return check("source", Severity.WARN,
                    "Media source недавно деградировал",
                    providerHealthDetails(safeHealth) + ", lastVoiceSource=" + safe(voice.lastSourceError()),
                    "Fallback остаётся автоматическим; проверь /doctor source повторно после успешного provider probe.");
        }
        return check("source", Severity.OK,
                "Playback providers в норме",
                YoutubeSourceRuntimeInfo.statusLabel()
                        + ", providers=" + providerHealthDetails(safeHealth)
                        + ", exceptions=" + voice.trackExceptions()
                        + ", voiceFallbacks=" + voice.fallbackAttempts(),
                "Live network probe намеренно не выполняется; health основан на runtime load/fallback событиях.");
    }

    private static String providerHealthDetails(List<PlaybackProviderHealthSnapshot> providerHealth) {
        if (providerHealth == null || providerHealth.isEmpty()) {
            return "n/a";
        }
        return providerHealth.stream()
                .map(snapshot -> snapshot.provider().label()
                        + "=" + snapshot.status()
                        + "(ok/fail/miss/fallback="
                        + snapshot.successes() + "/"
                        + snapshot.failures() + "/"
                        + snapshot.misses() + "/"
                        + snapshot.fallbacks() + ")")
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static Check storageCheck(PersistenceReadiness.Snapshot storage) {
        if (!storage.ready()) {
            return check("storage", Severity.FAIL,
                    "Persistent storage probe не прошёл",
                    safe(storage.details()),
                    "Проверь права и тип файлов guild-settings.properties, music-library.tsv, music-sessions.tsv и recommendation-feedback.tsv.");
        }
        return check("storage", Severity.OK,
                "Persistent storage готов",
                "stores=" + storage.stores() + ", existing=" + storage.existingFiles(),
                "Действий не требуется.");
    }

    private static Check backupCheck(PersistenceBackupService.Snapshot backups) {
        if (backups.enabled() && "FAILED".equals(backups.status())) {
            return check("backups", Severity.FAIL,
                    "Последний persistence backup завершился ошибкой",
                    safe(backups.details()),
                    "Проверь backup directory и права записи; storage отдельно может оставаться READY.");
        }
        if (backups.enabled() && "WAITING".equals(backups.status())) {
            return check("backups", Severity.WARN,
                    "Backup scheduler ещё не создал первый backup",
                    "status=WAITING",
                    "Повтори /doctor storage после первого scheduled backup.");
        }
        return check("backups", Severity.OK,
                backups.enabled() ? "Persistence backups в норме" : "Persistence backups отключены настройкой",
                "status=" + backups.status() + ", success/fail="
                        + backups.successfulBackups() + "/" + backups.failedBackups(),
                backups.enabled() ? "Действий не требуется." : "Это допустимо, если backups сознательно отключены.");
    }

    private static Check recoveryCheck(Instant now, SessionRecoverySnapshot recovery) {
        if (recovery.recoveriesInProgress() > 0) {
            return check("session", Severity.WARN,
                    "Playback recovery сейчас выполняется",
                    "inProgress=" + recovery.recoveriesInProgress() + ", last=" + safe(recovery.lastEvent()),
                    "Дождись завершения и повтори /doctor session.");
        }
        String last = recovery.lastEvent() == null ? "none" : recovery.lastEvent();
        String lower = last.toLowerCase(Locale.ROOT);
        if (recentEvent(last, now, RECENT_FAILURE_WINDOW)
                && (lower.contains("fail") || lower.contains("error") || lower.contains("timeout"))) {
            return check("session", Severity.WARN,
                    "Недавнее playback recovery завершилось проблемой",
                    safe(last),
                    "Проверь /session status; manager/admin может повторить /session recover.");
        }
        return check("session", Severity.OK,
                "Playback recovery без свежих проблем",
                "persisted=" + recovery.persistedSessions()
                        + ", startup restored/failed=" + recovery.startupRestoreSuccesses()
                        + "/" + recovery.startupRestoreFailures(),
                "Действий не требуется.");
    }

    private static Check commandCheck(Instant now, OperationalMetrics.Snapshot commands) {
        if (commands.totalInvocations() >= 5L && commands.failureRatePercent() >= 50.0d) {
            return check("commands", Severity.FAIL,
                    "Высокая доля ошибок команд",
                    String.format(Locale.ROOT, "failureRate=%.1f%%, failures=%d/%d",
                            commands.failureRatePercent(), commands.totalFailures(), commands.totalInvocations()),
                    "Открой /doctor failures и проверь повторяющийся тип ошибки.");
        }
        if (commands.lastFailureAt() != null
                && Duration.between(commands.lastFailureAt(), now).abs().compareTo(RECENT_FAILURE_WINDOW) <= 0) {
            return check("commands", Severity.WARN,
                    "Недавно была внутренняя ошибка команды",
                    "lastFailure=" + timestamp(commands.lastFailureAt())
                            + ", totalFailures=" + commands.totalFailures(),
                    "Открой /doctor failures для bounded журнала последних ошибок.");
        }
        return check("commands", Severity.OK,
                "Command runtime без свежих ошибок",
                String.format(Locale.ROOT, "invocations=%d, failures=%d, rate=%.1f%%",
                        commands.totalInvocations(), commands.totalFailures(), commands.failureRatePercent()),
                "Действий не требуется.");
    }

    private static Check check(String id, Severity severity, String title, String details, String action) {
        return new Check(id, severity, title, safe(details), safe(action));
    }

    private static boolean recentEvent(String value, Instant now, Duration window) {
        Instant timestamp = eventTimestamp(value);
        return timestamp != null && Duration.between(timestamp, now).abs().compareTo(window) <= 0;
    }

    private static Instant eventTimestamp(String value) {
        if (value == null || value.isBlank() || "none".equalsIgnoreCase(value.trim())) {
            return null;
        }
        int separator = value.indexOf(' ');
        String candidate = separator < 0 ? value.trim() : value.substring(0, separator).trim();
        try {
            return Instant.parse(candidate);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String timestamp(Instant instant) {
        return instant == null ? "none" : instant.toString();
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        String compact = value.replace('\n', ' ').replace('\r', ' ').replace('`', '\'').trim();
        return compact.length() <= 480 ? compact : compact.substring(0, 477) + "...";
    }

    public enum Severity {
        OK,
        WARN,
        FAIL;

        public String icon() {
            return switch (this) {
                case OK -> "✅";
                case WARN -> "⚠️";
                case FAIL -> "❌";
            };
        }
    }

    public record Check(
            String id,
            Severity severity,
            String title,
            String details,
            String action) {
    }

    public record Report(
            Instant generatedAt,
            Severity severity,
            List<Check> checks) {

        public Report {
            checks = List.copyOf(checks);
        }

        public List<Check> checks(String... ids) {
            List<String> wanted = List.of(ids);
            return checks.stream().filter(check -> wanted.contains(check.id())).toList();
        }
    }
}
