package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.MusicProperties;
import ru.flawden.BascovDiscordBot.operations.MusicRuntimeSnapshot;
import ru.flawden.BascovDiscordBot.settings.GuildPreferences;
import ru.flawden.BascovDiscordBot.settings.GuildPreferencesRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Spring-managed lifecycle всех музыкальных сессий.
 */
@Slf4j
@Component
public class PlayerManager {

    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> idleDisconnects = new ConcurrentHashMap<>();
    private final Map<Long, Instant> voiceDisconnectedSince = new ConcurrentHashMap<>();
    private final DefaultAudioPlayerManager audioPlayerManager;
    private final ScheduledExecutorService idleScheduler;
    private final MusicProperties properties;
    private final GuildPreferencesRepository preferencesRepository;
    private final VoiceConnectionCoordinator voiceConnections;

    public PlayerManager(
            MusicProperties properties,
            GuildPreferencesRepository preferencesRepository,
            VoiceConnectionCoordinator voiceConnections) {
        this.properties = properties;
        this.preferencesRepository = preferencesRepository;
        this.voiceConnections = voiceConnections;
        this.audioPlayerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(this.audioPlayerManager);
        this.idleScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "baskov-music-lifecycle");
            thread.setDaemon(true);
            return thread;
        });
        this.idleScheduler.scheduleWithFixedDelay(
                this::monitorVoiceConnections,
                1L,
                1L,
                TimeUnit.SECONDS);
        log.info("PlayerManager initialized: maxQueue={}, maxTrack={}, idleTimeout={}, volume={}/{}",
                properties.getMaxQueueSize(),
                properties.getMaxTrackDuration(),
                properties.getIdleDisconnectTimeout(),
                properties.getDefaultVolume(),
                properties.getMaxVolume());
    }

    public GuildMusicManager getMusicManager(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(), guildId -> {
            GuildPreferences preferences = preferencesRepository.get(guildId);
            GuildMusicManager manager = new GuildMusicManager(
                    audioPlayerManager,
                    guild,
                    properties,
                    preferences,
                    () -> cancelIdleDisconnect(guildId),
                    () -> scheduleIdleDisconnect(guild),
                    () -> submitPlaybackCleanup(guild));
            guild.getAudioManager().setSendingHandler(manager.getSendHandler());
            return manager;
        });
    }

    public Optional<GuildMusicManager> findMusicManager(Guild guild) {
        return Optional.ofNullable(musicManagers.get(guild.getIdLong()));
    }

    /**
     * Устанавливает ограниченное voice-подключение без бесконечного reconnect.
     */
    public CompletableFuture<VoiceConnectionResult> ensureVoiceConnection(
            Guild guild,
            AudioChannel target) {
        GuildMusicManager manager = getMusicManager(guild);
        return voiceConnections.ensureConnected(guild, target, manager.getSendHandler())
                .thenApply(result -> {
                    if (!result.connected()) {
                        stopAndRelease(guild);
                    }
                    return result;
                });
    }

    /**
     * Загружает трек и возвращает транспорт-независимый результат через callback.
     */
    public void loadAndPlay(Guild guild, String identifier, Consumer<MusicLoadResult> resultConsumer) {
        loadAndPlay(guild, identifier, TrackRequester.unknown(), resultConsumer);
    }

    public void loadAndPlay(
            Guild guild,
            String identifier,
            TrackRequester requester,
            Consumer<MusicLoadResult> resultConsumer) {
        GuildMusicManager musicManager = getMusicManager(guild);
        musicManager.markActivity();
        log.info("Loading media for guild {}: {}", guild.getId(), identifier);

        audioPlayerManager.loadItemOrdered(musicManager, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                deliverQueueResult(guild, musicManager, track, requester, resultConsumer);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack selected = playlist.getSelectedTrack();
                if (selected == null && !playlist.getTracks().isEmpty()) {
                    selected = playlist.getTracks().get(0);
                }

                if (selected == null) {
                    musicManager.getScheduler().scheduleDisconnectIfIdle();
                    resultConsumer.accept(MusicLoadResult.withoutTrack(MusicLoadResult.Status.NO_MATCHES));
                    return;
                }

                deliverQueueResult(guild, musicManager, selected, requester, resultConsumer);
            }

            @Override
            public void noMatches() {
                musicManager.getScheduler().scheduleDisconnectIfIdle();
                resultConsumer.accept(MusicLoadResult.withoutTrack(MusicLoadResult.Status.NO_MATCHES));
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                log.warn("Media load failed in guild {}: {}", guild.getId(), exception.getMessage());
                musicManager.getScheduler().scheduleDisconnectIfIdle();
                resultConsumer.accept(MusicLoadResult.withoutTrack(MusicLoadResult.Status.LOAD_FAILED));
            }
        });
    }

    public void stopAndRelease(Guild guild) {
        GuildMusicManager manager = musicManagers.remove(guild.getIdLong());
        cancelIdleDisconnect(guild.getIdLong());
        voiceDisconnectedSince.remove(guild.getIdLong());
        voiceConnections.cancel(guild);
        safeCloseAudio(guild);
        if (manager != null) {
            manager.destroy();
        }
    }

    private void deliverQueueResult(
            Guild guild,
            GuildMusicManager musicManager,
            AudioTrack track,
            TrackRequester requester,
            Consumer<MusicLoadResult> resultConsumer) {
        if (!musicManager.isActive()) {
            log.info("Ignoring completed media load for closed guild session {}", guild.getId());
            resultConsumer.accept(MusicLoadResult.withoutTrack(MusicLoadResult.Status.SESSION_CLOSED));
            return;
        }

        TrackScheduler.QueueResult queueResult = musicManager.getScheduler().queue(track, requester);
        MusicLoadResult.Status status = switch (queueResult.status()) {
            case STARTED -> MusicLoadResult.Status.STARTED;
            case QUEUED -> MusicLoadResult.Status.QUEUED;
            case QUEUE_FULL -> MusicLoadResult.Status.QUEUE_FULL;
            case TRACK_TOO_LONG -> MusicLoadResult.Status.TRACK_TOO_LONG;
            case STREAM_NOT_ALLOWED -> MusicLoadResult.Status.STREAM_NOT_ALLOWED;
        };

        if (status == MusicLoadResult.Status.QUEUE_FULL
                || status == MusicLoadResult.Status.TRACK_TOO_LONG
                || status == MusicLoadResult.Status.STREAM_NOT_ALLOWED) {
            musicManager.getScheduler().scheduleDisconnectIfIdle();
        }

        resultConsumer.accept(MusicLoadResult.of(
                status,
                track,
                queueResult.queuePosition(),
                queueResult.estimatedWaitMillis(),
                requester));
    }

    private void monitorVoiceConnections() {
        Instant now = Instant.now();
        for (GuildMusicManager manager : musicManagers.values()) {
            if (!manager.isActive()) {
                continue;
            }

            Guild guild = manager.getGuild();
            long guildId = guild.getIdLong();
            boolean playbackExpected = manager.getAudioPlayer().getPlayingTrack() != null
                    || manager.getScheduler().getCurrentRequest() != null;
            try {
                if (!playbackExpected || guild.getAudioManager().isConnected()) {
                    voiceDisconnectedSince.remove(guildId);
                    continue;
                }
            } catch (RuntimeException exception) {
                log.debug("Voice watchdog skipped unavailable guild {}: {}",
                        guild.getId(), exception.getMessage());
                continue;
            }

            Instant disconnectedAt = voiceDisconnectedSince.putIfAbsent(guildId, now);
            if (disconnectedAt == null) {
                log.warn("Voice transport became disconnected while playback was expected: guild={}",
                        guild.getId());
                continue;
            }

            Duration disconnectedFor = Duration.between(disconnectedAt, now);
            if (disconnectedFor.compareTo(properties.getVoiceDisconnectGrace()) < 0) {
                continue;
            }

            log.error("Voice transport did not recover within {}: guild={}",
                    properties.getVoiceDisconnectGrace(), guild.getId());
            voiceConnections.recordTransportFailure(
                    guild,
                    "Discord voice transport was disconnected for " + disconnectedFor.toSeconds() + " s");
            stopAndRelease(guild);
        }
    }

    private void submitPlaybackCleanup(Guild guild) {
        try {
            idleScheduler.execute(() -> {
                GuildMusicManager manager = musicManagers.get(guild.getIdLong());
                if (manager == null || !manager.isActive()) {
                    return;
                }
                voiceConnections.recordTransportFailure(
                        guild,
                        "LavaPlayer stopped because Discord no longer requested audio frames");
                stopAndRelease(guild);
            });
        } catch (RejectedExecutionException exception) {
            log.debug("Playback cleanup ignored during shutdown for guild {}", guild.getId());
        }
    }

    private void safeCloseAudio(Guild guild) {
        AudioManager audioManager = guild.getAudioManager();
        try {
            audioManager.setAutoReconnect(false);
            audioManager.closeAudioConnection();
        } catch (RuntimeException exception) {
            log.debug("Voice connection already unavailable in guild {}: {}",
                    guild.getId(), exception.getMessage());
        }
        try {
            audioManager.setSendingHandler(null);
        } catch (RuntimeException exception) {
            log.debug("Audio sending handler already unavailable in guild {}: {}",
                    guild.getId(), exception.getMessage());
        }
    }

    private void scheduleIdleDisconnect(Guild guild) {
        long guildId = guild.getIdLong();
        cancelIdleDisconnect(guildId);

        GuildMusicManager manager = musicManagers.get(guildId);
        if (manager == null || !manager.isActive()) {
            return;
        }
        long expectedActivityVersion = manager.getActivityVersion();

        Duration timeout = properties.getIdleDisconnectTimeout();
        if (timeout.isZero()) {
            disconnectIfStillIdle(guild, manager, expectedActivityVersion);
            return;
        }

        ScheduledFuture<?> future = idleScheduler.schedule(
                () -> disconnectIfStillIdle(guild, manager, expectedActivityVersion),
                timeout.toMillis(),
                TimeUnit.MILLISECONDS);
        idleDisconnects.put(guildId, future);
        log.info("Idle disconnect scheduled for guild {} in {}", guild.getId(), timeout);
    }

    private void disconnectIfStillIdle(
            Guild guild,
            GuildMusicManager expectedManager,
            long expectedActivityVersion) {
        GuildMusicManager currentManager = musicManagers.get(guild.getIdLong());
        if (currentManager != expectedManager
                || !currentManager.isActive()
                || currentManager.getActivityVersion() != expectedActivityVersion
                || !currentManager.isIdle()) {
            return;
        }

        log.info("Disconnecting idle music session in guild {}", guild.getId());
        stopAndRelease(guild);
    }

    private void cancelIdleDisconnect(long guildId) {
        ScheduledFuture<?> future = idleDisconnects.remove(guildId);
        if (future != null) {
            future.cancel(false);
        }
    }

    public MusicRuntimeSnapshot runtimeSnapshot() {
        int activeSessions = 0;
        int playingSessions = 0;
        int queuedTracks = 0;
        for (GuildMusicManager manager : musicManagers.values()) {
            if (!manager.isActive()) {
                continue;
            }
            activeSessions++;
            if (manager.getAudioPlayer().getPlayingTrack() != null) {
                playingSessions++;
            }
            queuedTracks += manager.getScheduler().queueSize();
        }
        return new MusicRuntimeSnapshot(activeSessions, playingSessions, queuedTracks);
    }

    int activeSessionCount() {
        return musicManagers.size();
    }

    @PreDestroy
    public void close() {
        log.info("Shutting down {} music sessions", musicManagers.size());
        for (GuildMusicManager manager : musicManagers.values()) {
            Guild guild = manager.getGuild();
            voiceConnections.cancel(guild);
            safeCloseAudio(guild);
            manager.destroy();
        }
        musicManagers.clear();
        idleDisconnects.values().forEach(future -> future.cancel(false));
        idleDisconnects.clear();
        voiceDisconnectedSince.clear();
        idleScheduler.shutdownNow();
        audioPlayerManager.shutdown();
    }
}
