package ru.flawden.BascovDiscordBot.lavaplayer;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.MusicProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Выполняет ровно одну ограниченную попытку voice-подключения на гильдию.
 *
 * <p>Автоматический reconnect JDA намеренно отключён: при проблеме транспорта
 * бот должен завершить попытку, освободить сессию и дать пользователю повторить
 * команду позже, а не бесконечно входить и выходить из канала.</p>
 */
@Slf4j
@Component
public class VoiceConnectionCoordinator {

    private static final long POLL_INTERVAL_MILLIS = 250L;

    private final Map<Long, Attempt> attempts = new ConcurrentHashMap<>();
    private final Map<Long, Instant> cooldownUntil = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final MusicProperties properties;
    private final Clock clock;
    private final AtomicBoolean active = new AtomicBoolean(true);

    @Autowired
    public VoiceConnectionCoordinator(MusicProperties properties) {
        this(properties, Clock.systemUTC());
    }

    VoiceConnectionCoordinator(MusicProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "baskov-voice-connection");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<VoiceConnectionResult> ensureConnected(
            Guild guild,
            AudioChannel target,
            AudioPlayerSendHandler sendHandler) {
        Objects.requireNonNull(guild, "guild");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(sendHandler, "sendHandler");

        if (!active.get()) {
            return CompletableFuture.completedFuture(result(
                    VoiceConnectionResult.Status.SHUTTING_DOWN,
                    "Voice coordinator is shutting down"));
        }

        long guildId = guild.getIdLong();
        Instant now = clock.instant();
        Instant blockedUntil = cooldownUntil.get(guildId);
        if (blockedUntil != null && blockedUntil.isAfter(now)) {
            Duration remaining = Duration.between(now, blockedUntil);
            log.warn("Voice connection suppressed by cooldown for guild {} (remaining={})",
                    guild.getId(), remaining);
            return CompletableFuture.completedFuture(result(
                    VoiceConnectionResult.Status.COOLDOWN,
                    "Повторная попытка будет доступна через " + Math.max(1L, remaining.toSeconds()) + " с."));
        }
        if (blockedUntil != null) {
            cooldownUntil.remove(guildId, blockedUntil);
        }

        AudioManager audioManager = guild.getAudioManager();
        if (isConnectedTo(guild, audioManager, target)) {
            audioManager.setAutoReconnect(false);
            audioManager.setSendingHandler(sendHandler);
            return CompletableFuture.completedFuture(result(
                    VoiceConnectionResult.Status.CONNECTED,
                    "Voice connection is already ready"));
        }

        synchronized (this) {
            Attempt existing = attempts.get(guildId);
            if (existing != null && !existing.future().isDone()) {
                if (existing.channelId() == target.getIdLong()) {
                    log.info("Joining existing voice connection attempt for guild {} and channel {}",
                            guild.getId(), target.getId());
                    return existing.future();
                }
                return CompletableFuture.completedFuture(result(
                        VoiceConnectionResult.Status.BUSY,
                        "Уже выполняется подключение к другому голосовому каналу."));
            }

            CompletableFuture<VoiceConnectionResult> future = new CompletableFuture<>();
            Attempt attempt = new Attempt(
                    guild,
                    target,
                    audioManager,
                    future,
                    clock.instant().plus(properties.getVoiceConnectTimeout()));
            attempts.put(guildId, attempt);

            try {
                audioManager.setAutoReconnect(false);
                audioManager.setSendingHandler(sendHandler);
                audioManager.openAudioConnection(target);
                log.info("Voice connection requested: guild={}, channel={}, timeout={}, autoReconnect=false",
                        guild.getId(), target.getId(), properties.getVoiceConnectTimeout());
            } catch (RuntimeException exception) {
                log.error("Voice connection request failed immediately: guild={}, channel={}",
                        guild.getId(), target.getId(), exception);
                fail(attempt, VoiceConnectionResult.Status.FAILED,
                        "Discord отклонил попытку подключения к голосовому каналу.", true);
                return future;
            }

            try {
                ScheduledFuture<?> poller = scheduler.scheduleAtFixedRate(
                        () -> pollSafely(attempt),
                        0L,
                        POLL_INTERVAL_MILLIS,
                        TimeUnit.MILLISECONDS);
                attempt.poller(poller);
            } catch (RuntimeException exception) {
                fail(attempt, VoiceConnectionResult.Status.SHUTTING_DOWN,
                        "Voice coordinator could not schedule the connection check.", false);
            }
            return future;
        }
    }

    public void recordTransportFailure(Guild guild, String reason) {
        Objects.requireNonNull(guild, "guild");
        long guildId = guild.getIdLong();
        cooldownUntil.put(guildId, clock.instant().plus(properties.getVoiceFailureCooldown()));
        Attempt attempt = attempts.remove(guildId);
        if (attempt != null) {
            attempt.cancelPoller();
            attempt.future().complete(result(VoiceConnectionResult.Status.FAILED, reason));
        }
        safeClose(guild, guild.getAudioManager());
        log.error("Voice transport failure: guild={}, cooldown={}, reason={}",
                guild.getId(), properties.getVoiceFailureCooldown(), reason);
    }

    public synchronized void cancel(Guild guild) {
        Attempt attempt = attempts.remove(guild.getIdLong());
        if (attempt != null) {
            attempt.cancelPoller();
            attempt.future().complete(result(
                    VoiceConnectionResult.Status.FAILED,
                    "Voice connection attempt was cancelled"));
        }
    }

    private void pollSafely(Attempt attempt) {
        try {
            poll(attempt);
        } catch (RuntimeException exception) {
            log.error("Voice connection polling failed: guild={}, channel={}",
                    attempt.guild().getId(), attempt.target().getId(), exception);
            fail(attempt, VoiceConnectionResult.Status.FAILED,
                    "Внутренняя ошибка проверки голосового соединения.", true);
        }
    }

    private void poll(Attempt attempt) {
        if (attempt.future().isDone() || !active.get()) {
            return;
        }

        AudioManager audioManager = attempt.audioManager();
        String state = state(audioManager);
        if (!state.equals(attempt.lastState())) {
            attempt.lastState(state);
            log.info("Voice connection state: guild={}, channel={}, state={}",
                    attempt.guild().getId(), attempt.target().getId(), state);
        }

        if (isConnectedTo(attempt.guild(), audioManager, attempt.target())) {
            complete(attempt, result(
                    VoiceConnectionResult.Status.CONNECTED,
                    "Voice connection is ready"));
            return;
        }

        if (!clock.instant().isBefore(attempt.deadline())) {
            fail(attempt, VoiceConnectionResult.Status.TIMEOUT,
                    "Не удалось установить стабильное голосовое соединение за "
                            + properties.getVoiceConnectTimeout().toSeconds() + " с.", true);
        }
    }

    private synchronized void complete(Attempt attempt, VoiceConnectionResult result) {
        if (!attempts.remove(attempt.guild().getIdLong(), attempt)) {
            return;
        }
        attempt.cancelPoller();
        attempt.future().complete(result);
        log.info("Voice connection ready: guild={}, channel={}",
                attempt.guild().getId(), attempt.target().getId());
    }

    private synchronized void fail(
            Attempt attempt,
            VoiceConnectionResult.Status status,
            String details,
            boolean applyCooldown) {
        attempts.remove(attempt.guild().getIdLong(), attempt);
        attempt.cancelPoller();
        if (applyCooldown) {
            cooldownUntil.put(
                    attempt.guild().getIdLong(),
                    clock.instant().plus(properties.getVoiceFailureCooldown()));
        }
        safeClose(attempt.guild(), attempt.audioManager());
        attempt.future().complete(result(status, details));
        log.warn("Voice connection failed: guild={}, channel={}, status={}, details={}",
                attempt.guild().getId(), attempt.target().getId(), status, details);
    }

    private static boolean isConnectedTo(Guild guild, AudioManager audioManager, AudioChannel target) {
        if (!audioManager.isConnected()) {
            return false;
        }
        var voiceState = guild.getSelfMember().getVoiceState();
        return voiceState != null
                && voiceState.inAudioChannel()
                && voiceState.getChannel() != null
                && voiceState.getChannel().getIdLong() == target.getIdLong();
    }

    private static String state(AudioManager audioManager) {
        return audioManager.isConnected() ? "CONNECTED" : "CONNECTING";
    }

    private static VoiceConnectionResult result(VoiceConnectionResult.Status status, String details) {
        return new VoiceConnectionResult(status, details);
    }

    private static void safeClose(Guild guild, AudioManager audioManager) {
        try {
            audioManager.closeAudioConnection();
        } catch (RuntimeException exception) {
            log.debug("Voice connection was already unavailable while closing guild {}: {}",
                    guild.getId(), exception.getMessage());
        }
        try {
            audioManager.setSendingHandler(null);
        } catch (RuntimeException exception) {
            log.debug("Sending handler was already unavailable while closing guild {}: {}",
                    guild.getId(), exception.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        if (!active.compareAndSet(true, false)) {
            return;
        }
        attempts.values().forEach(attempt -> {
            attempt.cancelPoller();
            safeClose(attempt.guild(), attempt.audioManager());
            attempt.future().complete(result(
                    VoiceConnectionResult.Status.SHUTTING_DOWN,
                    "Voice coordinator is shutting down"));
        });
        attempts.clear();
        cooldownUntil.clear();
        scheduler.shutdownNow();
    }

    private static final class Attempt {
        private final Guild guild;
        private final AudioChannel target;
        private final AudioManager audioManager;
        private final CompletableFuture<VoiceConnectionResult> future;
        private final Instant deadline;
        private volatile ScheduledFuture<?> poller;
        private volatile String lastState = "";

        private Attempt(
                Guild guild,
                AudioChannel target,
                AudioManager audioManager,
                CompletableFuture<VoiceConnectionResult> future,
                Instant deadline) {
            this.guild = guild;
            this.target = target;
            this.audioManager = audioManager;
            this.future = future;
            this.deadline = deadline;
        }

        Guild guild() {
            return guild;
        }

        AudioChannel target() {
            return target;
        }

        long channelId() {
            return target.getIdLong();
        }

        AudioManager audioManager() {
            return audioManager;
        }

        CompletableFuture<VoiceConnectionResult> future() {
            return future;
        }

        Instant deadline() {
            return deadline;
        }

        String lastState() {
            return lastState;
        }

        void lastState(String state) {
            this.lastState = state;
        }

        void poller(ScheduledFuture<?> value) {
            this.poller = value;
            if (future.isDone()) {
                value.cancel(false);
            }
        }

        void cancelPoller() {
            ScheduledFuture<?> value = poller;
            if (value != null) {
                value.cancel(false);
            }
        }
    }
}
