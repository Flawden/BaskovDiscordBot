package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.commands.music.MediaQueryResolver;
import ru.flawden.BascovDiscordBot.config.MusicProperties;
import ru.flawden.BascovDiscordBot.operations.MusicRuntimeSnapshot;
import ru.flawden.BascovDiscordBot.operations.VoiceDiagnosticSnapshot;
import ru.flawden.BascovDiscordBot.operations.VoiceDiagnostics;
import ru.flawden.BascovDiscordBot.settings.GuildPreferences;
import ru.flawden.BascovDiscordBot.settings.GuildPreferencesRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Spring-managed lifecycle всех музыкальных сессий.
 */
@Slf4j
@Component
public class PlayerManager {

    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> idleDisconnects = new ConcurrentHashMap<>();
    private final Map<Long, Instant> voiceFrameDemandMissingSince = new ConcurrentHashMap<>();
    private final Map<Long, Instant> voiceWatchdogNotBefore = new ConcurrentHashMap<>();
    private final Set<Long> voiceWatchdogReported = ConcurrentHashMap.newKeySet();
    private final DefaultAudioPlayerManager audioPlayerManager;
    private final ScheduledExecutorService idleScheduler;
    private final ScheduledExecutorService playbackReadinessScheduler;
    private final MusicProperties properties;
    private final GuildPreferencesRepository preferencesRepository;
    private final VoiceConnectionCoordinator voiceConnections;
    private final VoiceDiagnostics voiceDiagnostics;

    public PlayerManager(
            MusicProperties properties,
            GuildPreferencesRepository preferencesRepository,
            VoiceConnectionCoordinator voiceConnections,
            VoiceDiagnostics voiceDiagnostics) {
        this.properties = properties;
        this.preferencesRepository = preferencesRepository;
        this.voiceConnections = voiceConnections;
        this.voiceDiagnostics = voiceDiagnostics;
        this.audioPlayerManager = new DefaultAudioPlayerManager();

        YoutubeAudioSourceManager youtubeSourceManager = new YoutubeAudioSourceManager();
        this.audioPlayerManager.registerSourceManager(youtubeSourceManager);
        AudioSourceManagers.registerRemoteSources(
                this.audioPlayerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class);

        log.info("{} engine={} version={} clients={} legacyLavaplayerYoutube=disabled "
                        + "defaultSearchProvider=YOUTUBE directUrlProviders=YouTube|SoundCloud",
                YoutubeSourceRuntimeInfo.STARTUP_MARKER,
                YoutubeSourceRuntimeInfo.ENGINE,
                YoutubeSourceRuntimeInfo.VERSION,
                YoutubeSourceRuntimeInfo.CLIENTS);
        this.idleScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "baskov-music-lifecycle");
            thread.setDaemon(true);
            return thread;
        });
        this.playbackReadinessScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "baskov-playback-readiness");
            thread.setDaemon(true);
            return thread;
        });
        this.idleScheduler.scheduleWithFixedDelay(
                this::monitorVoiceConnections,
                1L,
                1L,
                TimeUnit.SECONDS);
        log.info("PlayerManager initialized: maxQueue={}, maxTrack={}, idleTimeout={}, "
                        + "playbackReadyTimeout={}, volume={}/{}",
                properties.getMaxQueueSize(),
                properties.getMaxTrackDuration(),
                properties.getIdleDisconnectTimeout(),
                properties.getPlaybackReadyTimeout(),
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
                    voiceDiagnostics);
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
                        return result;
                    }
                    manager.getSendHandler().resetFrameTelemetry();
                    voiceFrameDemandMissingSince.remove(guild.getIdLong());
                    voiceWatchdogReported.remove(guild.getIdLong());
                    voiceWatchdogNotBefore.put(
                            guild.getIdLong(),
                            Instant.now().plus(properties.getVoiceConnectTimeout()));
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
                deliverQueueResult(
                        guild, musicManager, track, requester, List.of(), resultConsumer);
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

                List<AudioTrack> fallbacks = searchFallbacks(identifier, playlist, selected);
                deliverQueueResult(
                        guild, musicManager, selected, requester, fallbacks, resultConsumer);
            }

            @Override
            public void noMatches() {
                voiceDiagnostics.sourceFailure(guild.getIdLong(), identifier, "no matches");
                musicManager.getScheduler().scheduleDisconnectIfIdle();
                resultConsumer.accept(MusicLoadResult.withoutTrack(MusicLoadResult.Status.NO_MATCHES));
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                String reason = SourceFailureFormatter.describe(identifier, exception);
                voiceDiagnostics.sourceFailure(
                        guild.getIdLong(),
                        identifier,
                        reason);
                log.warn("Media load failed in guild {}: {}", guild.getId(), reason);
                musicManager.getScheduler().scheduleDisconnectIfIdle();
                resultConsumer.accept(MusicLoadResult.withoutTrack(MusicLoadResult.Status.LOAD_FAILED));
            }
        });
    }

    /**
     * Выполняет поиск без автоматического запуска. Возвращает до указанного
     * количества уникальных воспроизводимых кандидатов.
     */
    public void search(
            Guild guild,
            String identifier,
            int maxResults,
            Consumer<MusicSearchResult> resultConsumer) {
        if (maxResults < 1) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        log.info("Searching media for guild {}: {}", guild.getId(), identifier);
        String orderingKey = "search:" + guild.getIdLong();
        audioPlayerManager.loadItemOrdered(orderingKey, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                List<AudioTrack> candidates = isPlayableCandidate(track)
                        ? List.of(track)
                        : List.of();
                resultConsumer.accept(candidates.isEmpty()
                        ? MusicSearchResult.empty(MusicSearchResult.Status.NO_MATCHES, identifier)
                        : MusicSearchResult.found(identifier, candidates));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                List<AudioTrack> candidates = searchCandidates(playlist, maxResults);
                resultConsumer.accept(candidates.isEmpty()
                        ? MusicSearchResult.empty(MusicSearchResult.Status.NO_MATCHES, identifier)
                        : MusicSearchResult.found(identifier, candidates));
            }

            @Override
            public void noMatches() {
                voiceDiagnostics.sourceFailure(guild.getIdLong(), identifier, "search: no matches");
                resultConsumer.accept(MusicSearchResult.empty(
                        MusicSearchResult.Status.NO_MATCHES, identifier));
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                String reason = SourceFailureFormatter.describe(identifier, exception);
                voiceDiagnostics.sourceFailure(guild.getIdLong(), identifier, "search: " + reason);
                log.warn("Media search failed in guild {}: {}", guild.getId(), reason);
                resultConsumer.accept(MusicSearchResult.empty(
                        MusicSearchResult.Status.LOAD_FAILED, identifier));
            }
        });
    }

    /**
     * Добавляет уже загруженный результат /search в текущую музыкальную сессию.
     */
    public void queueLoadedTrack(
            Guild guild,
            AudioTrack track,
            TrackRequester requester,
            Consumer<MusicLoadResult> resultConsumer) {
        GuildMusicManager musicManager = getMusicManager(guild);
        musicManager.markActivity();
        deliverQueueResult(
                guild,
                musicManager,
                track,
                requester,
                List.of(),
                resultConsumer);
    }

    public CompletableFuture<PlaybackReadinessResult> awaitPlaybackReady(
            Guild guild,
            AudioTrack expectedTrack) {
        GuildMusicManager initialManager = musicManagers.get(guild.getIdLong());
        if (initialManager == null || !initialManager.isActive()) {
            return CompletableFuture.completedFuture(PlaybackReadinessResult.failure(
                    PlaybackReadinessResult.Status.SESSION_CLOSED,
                    "Музыкальная сессия была закрыта до подтверждения воспроизведения."));
        }

        long baselineFrameRequests = initialManager.getSendHandler().frameRequestCount();
        Instant deadline = Instant.now().plus(properties.getPlaybackReadyTimeout());
        CompletableFuture<PlaybackReadinessResult> result = new CompletableFuture<>();
        AtomicReference<ScheduledFuture<?>> taskReference = new AtomicReference<>();

        Runnable probe = () -> {
            if (result.isDone()) {
                return;
            }

            GuildMusicManager currentManager = musicManagers.get(guild.getIdLong());
            boolean sessionActive = currentManager != null && currentManager.isActive();
            boolean expectedTrackIsCurrent = sessionActive
                    && currentManager.getAudioPlayer().getPlayingTrack() == expectedTrack;
            boolean selfInVoiceChannel = guild.getSelfMember().getVoiceState() != null
                    && guild.getSelfMember().getVoiceState().getChannel() != null;
            long currentFrameRequests = sessionActive
                    ? currentManager.getSendHandler().frameRequestCount()
                    : baselineFrameRequests;

            PlaybackReadinessPolicy.Decision decision = PlaybackReadinessPolicy.evaluate(
                    sessionActive,
                    expectedTrackIsCurrent,
                    selfInVoiceChannel,
                    baselineFrameRequests,
                    currentFrameRequests,
                    Instant.now(),
                    deadline);

            PlaybackReadinessResult readiness = switch (decision) {
                case WAIT -> null;
                case READY -> PlaybackReadinessResult.readyResult();
                case VOICE_LEFT -> PlaybackReadinessResult.failure(
                        PlaybackReadinessResult.Status.VOICE_LEFT,
                        "Discord завершил voice-сессию до первого аудиофрейма.");
                case FRAME_TIMEOUT -> PlaybackReadinessResult.failure(
                        PlaybackReadinessResult.Status.FRAME_TIMEOUT,
                        "Discord не запросил ни одного аудиофрейма за "
                                + properties.getPlaybackReadyTimeout().toSeconds() + " секунд.");
                case SESSION_CLOSED -> PlaybackReadinessResult.failure(
                        PlaybackReadinessResult.Status.SESSION_CLOSED,
                        "Музыкальная сессия была закрыта до подтверждения воспроизведения.");
                case TRACK_REPLACED -> PlaybackReadinessResult.failure(
                        PlaybackReadinessResult.Status.TRACK_REPLACED,
                        "Трек был заменён до подтверждения media transport.");
            };

            if (readiness == null || !result.complete(readiness)) {
                return;
            }
            if (readiness.ready()) {
                log.info("Playback confirmed by Discord frame polling: guild={}, track={}",
                        guild.getId(),
                        expectedTrack.getInfo() == null ? "unknown" : expectedTrack.getInfo().title);
            } else {
                voiceDiagnostics.voiceFailure(
                        guild.getIdLong(),
                        "PLAYBACK_CONFIRMATION_" + readiness.status() + ": " + readiness.details());
                log.error("Playback confirmation failed: guild={}, status={}, details={}",
                        guild.getId(), readiness.status(), readiness.details());
            }
        };

        ScheduledFuture<?> task = playbackReadinessScheduler.scheduleAtFixedRate(
                probe,
                0L,
                100L,
                TimeUnit.MILLISECONDS);
        taskReference.set(task);
        result.whenComplete((ignored, failure) -> {
            ScheduledFuture<?> scheduled = taskReference.get();
            if (scheduled != null) {
                scheduled.cancel(false);
            }
        });
        return result;
    }

    public void stopAndRelease(Guild guild) {
        GuildMusicManager manager = musicManagers.remove(guild.getIdLong());
        cancelIdleDisconnect(guild.getIdLong());
        voiceFrameDemandMissingSince.remove(guild.getIdLong());
        voiceWatchdogNotBefore.remove(guild.getIdLong());
        voiceWatchdogReported.remove(guild.getIdLong());
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
            List<AudioTrack> fallbackTracks,
            Consumer<MusicLoadResult> resultConsumer) {
        if (!musicManager.isActive()) {
            log.info("Ignoring completed media load for closed guild session {}", guild.getId());
            resultConsumer.accept(MusicLoadResult.withoutTrack(MusicLoadResult.Status.SESSION_CLOSED));
            return;
        }

        TrackScheduler.QueueResult queueResult = musicManager.getScheduler().queue(
                track, requester, fallbackTracks);
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
            Instant notBefore = voiceWatchdogNotBefore.getOrDefault(guildId, Instant.EPOCH);
            boolean recentFrameRequest = manager.getSendHandler()
                    .hasRecentFrameRequest(properties.getVoiceDisconnectGrace());
            Instant missingSince = voiceFrameDemandMissingSince.get(guildId);

            VoiceWatchdogPolicy.Decision decision = VoiceWatchdogPolicy.evaluate(
                    now,
                    notBefore,
                    missingSince,
                    playbackExpected,
                    recentFrameRequest,
                    properties.getVoiceDisconnectGrace());

            if (decision == VoiceWatchdogPolicy.Decision.HEALTHY) {
                voiceFrameDemandMissingSince.remove(guildId);
                voiceWatchdogReported.remove(guildId);
                continue;
            }
            if (decision == VoiceWatchdogPolicy.Decision.START_GRACE) {
                voiceFrameDemandMissingSince.putIfAbsent(guildId, now);
                log.warn("Discord stopped requesting audio frames while playback was expected: guild={}",
                        guild.getId());
                continue;
            }
            if (decision == VoiceWatchdogPolicy.Decision.WAIT) {
                continue;
            }

            Duration missingFor = Duration.between(missingSince, now);
            String reason = "Discord did not request audio frames for "
                    + missingFor.toSeconds() + " s";
            if (voiceWatchdogReported.add(guildId)) {
                voiceDiagnostics.watchdogWarning(guildId, reason);
                log.error("{}: guild={}, enforce={}",
                        reason, guild.getId(), properties.isVoiceWatchdogEnforce());
            }
            if (!properties.isVoiceWatchdogEnforce()) {
                continue;
            }
            voiceConnections.recordTransportFailure(guild, reason);
            stopAndRelease(guild);
        }
    }

    private List<AudioTrack> searchCandidates(AudioPlaylist playlist, int maxResults) {
        List<AudioTrack> ordered = new ArrayList<>();
        if (playlist.getSelectedTrack() != null) {
            ordered.add(playlist.getSelectedTrack());
        }
        ordered.addAll(playlist.getTracks());

        Set<String> seen = ConcurrentHashMap.newKeySet();
        return ordered.stream()
                .filter(this::isPlayableCandidate)
                .filter(candidate -> seen.add(trackKey(candidate)))
                .limit(maxResults)
                .toList();
    }

    private List<AudioTrack> searchFallbacks(
            String identifier,
            AudioPlaylist playlist,
            AudioTrack selected) {
        boolean supportedSearch = identifier.startsWith(MediaQueryResolver.YOUTUBE_SEARCH_PREFIX)
                || identifier.startsWith(MediaQueryResolver.SOUNDCLOUD_SEARCH_PREFIX);
        if (!supportedSearch) {
            return List.of();
        }
        Set<String> seen = ConcurrentHashMap.newKeySet();
        seen.add(trackKey(selected));
        return playlist.getTracks().stream()
                .filter(candidate -> candidate != selected)
                .filter(this::isPlayableCandidate)
                .filter(candidate -> seen.add(trackKey(candidate)))
                .limit(9)
                .toList();
    }

    private boolean isPlayableCandidate(AudioTrack track) {
        return track != null
                && !track.getInfo().isStream
                && track.getDuration() > 0L
                && track.getDuration() <= properties.getMaxTrackDuration().toMillis();
    }

    private static String trackKey(AudioTrack track) {
        if (track == null || track.getInfo() == null) {
            return "unknown";
        }
        String identifier = track.getInfo().identifier;
        if (identifier != null && !identifier.isBlank()) {
            return identifier;
        }
        return String.valueOf(track.getInfo().title) + ':' + track.getDuration();
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

    public VoiceDiagnosticSnapshot voiceDiagnosticsSnapshot(Guild guild) {
        return voiceDiagnostics.snapshot(guild, musicManagers.get(guild.getIdLong()));
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
        voiceFrameDemandMissingSince.clear();
        voiceWatchdogNotBefore.clear();
        voiceWatchdogReported.clear();
        idleScheduler.shutdownNow();
        playbackReadinessScheduler.shutdownNow();
        audioPlayerManager.shutdown();
    }
}
