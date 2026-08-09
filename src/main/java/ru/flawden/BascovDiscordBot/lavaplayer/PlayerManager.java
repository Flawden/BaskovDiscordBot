package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.commands.music.MediaQueryResolver;
import ru.flawden.BascovDiscordBot.config.MusicProperties;
import ru.flawden.BascovDiscordBot.config.MusicSessionProperties;
import ru.flawden.BascovDiscordBot.operations.MusicRuntimeSnapshot;
import ru.flawden.BascovDiscordBot.operations.VoiceDiagnosticSnapshot;
import ru.flawden.BascovDiscordBot.operations.VoiceDiagnostics;
import ru.flawden.BascovDiscordBot.settings.GuildPreferences;
import ru.flawden.BascovDiscordBot.settings.GuildPreferencesRepository;
import ru.flawden.BascovDiscordBot.library.PlaybackHistoryRecorder;
import ru.flawden.BascovDiscordBot.library.MusicLibraryRepository;
import ru.flawden.BascovDiscordBot.library.PersonalListeningInsights;
import ru.flawden.BascovDiscordBot.library.StoredTrack;
import ru.flawden.BascovDiscordBot.session.MusicSessionRepository;
import ru.flawden.BascovDiscordBot.session.SessionRecoveryDetails;
import ru.flawden.BascovDiscordBot.session.SessionRecoverySnapshot;
import ru.flawden.BascovDiscordBot.session.StoredMusicSession;
import ru.flawden.BascovDiscordBot.session.StoredSessionTrack;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Spring-managed lifecycle всех музыкальных сессий.
 */
@Slf4j
@Component
public class PlayerManager {

    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> idleDisconnects = new ConcurrentHashMap<>();
    private final Map<Long, Long> voiceTargets = new ConcurrentHashMap<>();
    private final Map<Long, VoiceRecoveryRun> voiceRecoveries = new ConcurrentHashMap<>();
    private final Set<Long> startupRestoresInProgress = ConcurrentHashMap.newKeySet();
    private final Map<Long, Instant> intentionalDisconnectUntil = new ConcurrentHashMap<>();
    private final Map<Long, Instant> voiceFrameDemandMissingSince = new ConcurrentHashMap<>();
    private final Map<Long, Instant> voiceWatchdogNotBefore = new ConcurrentHashMap<>();
    private final Set<Long> voiceWatchdogReported = ConcurrentHashMap.newKeySet();
    private final DefaultAudioPlayerManager audioPlayerManager;
    private final ScheduledExecutorService idleScheduler;
    private final ScheduledExecutorService playbackReadinessScheduler;
    private final ScheduledExecutorService sessionLifecycleScheduler;
    private final MusicProperties properties;
    private final MusicSessionProperties sessionProperties;
    private final GuildPreferencesRepository preferencesRepository;
    private final VoiceConnectionCoordinator voiceConnections;
    private final VoiceDiagnostics voiceDiagnostics;
    private final PlaybackHistoryRecorder historyRecorder;
    private final MusicLibraryRepository musicLibraryRepository;
    private final MusicSessionRepository sessionRepository;
    private final Map<Long, RadioState> radioStates = new ConcurrentHashMap<>();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicLong recoveryAttempts = new AtomicLong();
    private final AtomicLong recoverySuccesses = new AtomicLong();
    private final AtomicLong recoveryFailures = new AtomicLong();
    private final AtomicLong startupRestoreSuccesses = new AtomicLong();
    private final AtomicLong startupRestoreFailures = new AtomicLong();
    private final AtomicLong startupHistoryTracksRestored = new AtomicLong();
    private final AtomicLong startupHistoryTrackFailures = new AtomicLong();
    private final AtomicReference<String> lastSessionRecoveryEvent = new AtomicReference<>("none");

    public PlayerManager(
            MusicProperties properties,
            GuildPreferencesRepository preferencesRepository,
            VoiceConnectionCoordinator voiceConnections,
            VoiceDiagnostics voiceDiagnostics,
            PlaybackHistoryRecorder historyRecorder,
            MusicLibraryRepository musicLibraryRepository,
            MusicSessionProperties sessionProperties,
            MusicSessionRepository sessionRepository) {
        this.properties = properties;
        this.sessionProperties = sessionProperties;
        this.preferencesRepository = preferencesRepository;
        this.voiceConnections = voiceConnections;
        this.voiceDiagnostics = voiceDiagnostics;
        this.historyRecorder = historyRecorder;
        this.musicLibraryRepository = Objects.requireNonNull(musicLibraryRepository, "musicLibraryRepository");
        this.sessionRepository = sessionRepository;
        this.audioPlayerManager = new DefaultAudioPlayerManager();

        YoutubeAudioSourceManager youtubeSourceManager = new YoutubeAudioSourceManager();
        this.audioPlayerManager.registerSourceManager(youtubeSourceManager);
        AudioSourceManagers.registerRemoteSources(
                this.audioPlayerManager,
                legacyYoutubeSourceClass());

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
        this.sessionLifecycleScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "baskov-session-recovery");
            thread.setDaemon(true);
            return thread;
        });
        this.idleScheduler.scheduleWithFixedDelay(
                this::monitorVoiceConnections,
                1L,
                1L,
                TimeUnit.SECONDS);
        this.sessionLifecycleScheduler.scheduleWithFixedDelay(
                this::checkpointAllSessionsSafely,
                sessionProperties.getCheckpointInterval().toMillis(),
                sessionProperties.getCheckpointInterval().toMillis(),
                TimeUnit.MILLISECONDS);
        log.info("PlayerManager initialized: maxQueue={}, maxTrack={}, idleTimeout={}, "
                        + "playbackReadyTimeout={}, volume={}/{}",
                properties.getMaxQueueSize(),
                properties.getMaxTrackDuration(),
                properties.getIdleDisconnectTimeout(),
                properties.getPlaybackReadyTimeout(),
                properties.getDefaultVolume(),
                properties.getMaxVolume());
        log.info("Voice recovery initialized: enabled={}, attempts={}, backoff={}, startupRestore={}, "
                        + "requireHumanListener={}, checkpointInterval={}, maxAge={}, persisted={}",
                sessionProperties.isVoiceRecoveryEnabled(),
                sessionProperties.getMaxRecoveryAttempts(),
                sessionProperties.getRecoveryBackoff(),
                sessionProperties.isRestoreOnStartup(),
                sessionProperties.isRequireHumanListener(),
                sessionProperties.getCheckpointInterval(),
                sessionProperties.getMaxAge(),
                sessionRepository.sessions().size());
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
                    () -> handleMusicIdle(guild),
                    voiceDiagnostics,
                    request -> historyRecorder.record(guildId, request));
            guild.getAudioManager().setSendingHandler(manager.getSendHandler());
            return manager;
        });
    }

    public boolean hasRadioSeeds(long guildId, RadioMode mode, long userId) {
        return !radioSeeds(guildId, mode, userId).isEmpty();
    }

    public RadioStartResult startRadio(Guild guild, RadioMode mode, TrackRequester owner) {
        Objects.requireNonNull(guild, "guild");
        Objects.requireNonNull(mode, "mode");
        TrackRequester requester = owner == null ? TrackRequester.unknown() : owner;
        List<StoredTrack> seeds = radioSeeds(guild.getIdLong(), mode, requester.userId());
        if (seeds.isEmpty()) {
            return new RadioStartResult(RadioStartResult.Status.NO_SEEDS, RadioSnapshot.disabled());
        }

        long guildId = guild.getIdLong();
        RadioState previous = radioStates.put(guildId, new RadioState(mode, requester));
        GuildMusicManager manager = getMusicManager(guild);
        manager.markActivity();
        RadioStartResult.Status status = previous == null
                ? RadioStartResult.Status.STARTED
                : RadioStartResult.Status.UPDATED;
        if (manager.isIdle()) {
            triggerRadioRefill(guild, manager);
        }
        return new RadioStartResult(status, radioSnapshot(guildId));
    }

    public RadioSnapshot stopRadio(long guildId) {
        RadioState removed = radioStates.remove(guildId);
        GuildMusicManager manager = musicManagers.get(guildId);
        if (manager != null && manager.isIdle()) {
            scheduleIdleDisconnect(manager.getGuild());
        }
        return removed == null ? RadioSnapshot.disabled() : removed.snapshot(false);
    }

    public RadioSnapshot radioSnapshot(long guildId) {
        RadioState state = radioStates.get(guildId);
        return state == null ? RadioSnapshot.disabled() : state.snapshot(true);
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
        return ensureVoiceConnection(guild, target, true, false);
    }

    private CompletableFuture<VoiceConnectionResult> ensureVoiceConnection(
            Guild guild,
            AudioChannel target,
            boolean releaseOnFailure,
            boolean bypassCooldown) {
        GuildMusicManager manager = getMusicManager(guild);
        voiceTargets.put(guild.getIdLong(), target.getIdLong());
        return voiceConnections.ensureConnected(
                        guild,
                        target,
                        manager.getSendHandler(),
                        bypassCooldown)
                .thenApply(result -> {
                    if (!result.connected()) {
                        if (releaseOnFailure) {
                            releaseSession(guild, true);
                        }
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


    private static Class<? extends AudioSourceManager> legacyYoutubeSourceClass() {
        try {
            return Class.forName(
                            "com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager")
                    .asSubclass(AudioSourceManager.class);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                    "Embedded LavaPlayer YouTube source is missing; cannot exclude the legacy extractor",
                    exception);
        }
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
        loadAndPlay(guild, identifier, requester, false, resultConsumer);
    }

    private void loadAndPlay(
            Guild guild,
            String identifier,
            TrackRequester requester,
            boolean recoveryRestore,
            Consumer<MusicLoadResult> resultConsumer) {
        GuildMusicManager musicManager = getMusicManager(guild);
        musicManager.markActivity();
        log.info("Loading media for guild {}: {}", guild.getId(), identifier);

        audioPlayerManager.loadItemOrdered(musicManager, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                deliverQueueResult(
                        guild, musicManager, track, requester, List.of(), recoveryRestore, resultConsumer);
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
                        guild, musicManager, selected, requester, fallbacks, recoveryRestore, resultConsumer);
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
                false,
                resultConsumer);
    }

    /**
     * Загружает несколько сохранённых URL в одном ordered channel и возвращает
     * единый итог. Порядок исходного списка сохраняется.
     */
    public void loadBatch(
            Guild guild,
            List<String> identifiers,
            TrackRequester requester,
            Consumer<BatchMusicLoadResult> resultConsumer) {
        List<String> ordered = identifiers == null
                ? List.of()
                : identifiers.stream()
                        .filter(identifier -> identifier != null && !identifier.isBlank())
                        .map(String::trim)
                        .toList();
        if (ordered.isEmpty()) {
            resultConsumer.accept(new BatchMusicLoadResult(0, 0, 0, 0, null));
            return;
        }

        AtomicInteger remaining = new AtomicInteger(ordered.size());
        AtomicInteger started = new AtomicInteger();
        AtomicInteger queued = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicReference<AudioTrack> firstStarted = new AtomicReference<>();

        for (String identifier : ordered) {
            loadAndPlay(guild, identifier, requester, result -> {
                switch (result.status()) {
                    case STARTED -> {
                        started.incrementAndGet();
                        firstStarted.compareAndSet(null, result.track());
                    }
                    case QUEUED -> queued.incrementAndGet();
                    default -> rejected.incrementAndGet();
                }
                if (remaining.decrementAndGet() == 0) {
                    resultConsumer.accept(new BatchMusicLoadResult(
                            ordered.size(),
                            started.get(),
                            queued.get(),
                            rejected.get(),
                            firstStarted.get()));
                }
            });
        }
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

    /**
     * Принимает voice-переходы самого бота. Неожиданный LEAVE при активном
     * playback запускает ограниченное восстановление текущего transport.
     */
    public void handleSelfVoiceTransition(Guild guild, String transition, Long channelId) {
        Objects.requireNonNull(guild, "guild");
        String normalized = transition == null ? "UNKNOWN" : transition.trim().toUpperCase();
        long guildId = guild.getIdLong();
        if (("JOIN".equals(normalized) || "MOVE".equals(normalized)) && channelId != null) {
            voiceTargets.put(guildId, channelId);
            intentionalDisconnectUntil.remove(guildId);
            return;
        }
        if (!"LEAVE".equals(normalized)) {
            return;
        }
        Instant intentionalUntil = intentionalDisconnectUntil.get(guildId);
        if (intentionalUntil != null && intentionalUntil.isAfter(Instant.now())) {
            log.debug("Ignoring intentional self voice leave: guild={}, channel={}", guild.getId(), channelId);
            return;
        }
        GuildMusicManager manager = musicManagers.get(guildId);
        if (manager == null || !manager.isActive() || !playbackExpected(manager)) {
            return;
        }
        recoverVoiceSession(guild, "unexpected self voice leave");
    }

    /**
     * Если startup checkpoint был оставлен pending из-за пустого канала,
     * первый вернувшийся человек запускает восстановление без новой команды.
     */
    public void handleHumanVoiceJoin(Guild guild, long channelId) {
        if (closing.get()
                || !sessionProperties.isRestoreOnStartup()
                || musicManagers.containsKey(guild.getIdLong())) {
            return;
        }
        sessionRepository.session(guild.getIdLong()).ifPresent(stored -> {
            if (stored.voiceChannelId() != channelId) {
                return;
            }
            if (stored.expired(Instant.now(), sessionProperties.getMaxAge())) {
                removeCheckpointSafely(stored.guildId(), "expired pending checkpoint");
                return;
            }
            AudioChannel target = resolveAudioChannel(guild, channelId);
            if (target != null) {
                sessionLifecycleScheduler.execute(() -> restoreStoredSession(guild, target, stored));
            }
        });
    }

    /**
     * Восстанавливает сохранённые до restart/deploy сессии после READY JDA.
     */
    public void restorePersistedSessions(JDA jda) {
        Objects.requireNonNull(jda, "jda");
        if (closing.get()) {
            return;
        }
        if (!sessionProperties.isRestoreOnStartup()) {
            log.info("Startup music session restoration is disabled; persisted={}",
                    sessionRepository.sessions().size());
            return;
        }
        sessionLifecycleScheduler.execute(() -> {
            Instant now = Instant.now();
            for (StoredMusicSession stored : sessionRepository.sessions()) {
                if (stored.expired(now, sessionProperties.getMaxAge())) {
                    removeCheckpointSafely(stored.guildId(), "expired startup checkpoint");
                    continue;
                }
                Guild guild = jda.getGuildById(stored.guildId());
                if (guild == null) {
                    removeCheckpointSafely(stored.guildId(), "bot no longer belongs to guild");
                    continue;
                }
                AudioChannel target = resolveAudioChannel(guild, stored.voiceChannelId());
                if (target == null) {
                    removeCheckpointSafely(stored.guildId(), "saved voice channel no longer exists");
                    continue;
                }
                if (sessionProperties.isRequireHumanListener()
                        && !hasHumanListener(guild, stored.voiceChannelId())) {
                    updateSessionEvent("startup restore pending: guild=" + guild.getId()
                            + " channel=" + stored.voiceChannelId() + " has no human listeners");
                    log.info("Keeping startup checkpoint pending because voice channel has no human listeners: "
                                    + "guild={}, channel={}, tracks={}",
                            guild.getId(), stored.voiceChannelId(), stored.trackCount());
                    continue;
                }
                restoreStoredSession(guild, target, stored);
            }
        });
    }

    public SessionRecoverySnapshot sessionRecoverySnapshot() {
        return new SessionRecoverySnapshot(
                sessionRepository.sessions().size(),
                voiceRecoveries.size() + startupRestoresInProgress.size(),
                recoveryAttempts.get(),
                recoverySuccesses.get(),
                recoveryFailures.get(),
                startupRestoreSuccesses.get(),
                startupRestoreFailures.get(),
                startupHistoryTracksRestored.get(),
                startupHistoryTrackFailures.get(),
                lastSessionRecoveryEvent.get());
    }

    public SessionRecoveryDetails sessionRecoveryDetails(Guild guild) {
        Objects.requireNonNull(guild, "guild");
        long guildId = guild.getIdLong();
        StoredMusicSession stored = sessionRepository.session(guildId).orElse(null);
        GuildMusicManager manager = musicManagers.get(guildId);
        boolean active = manager != null && manager.isActive() && playbackExpected(manager);
        boolean restoring = startupRestoresInProgress.contains(guildId) || voiceRecoveries.containsKey(guildId);
        if (stored == null) {
            SessionRecoveryDetails none = SessionRecoveryDetails.none(lastSessionRecoveryEvent.get());
            if (!active && !restoring) {
                return none;
            }
            return new SessionRecoveryDetails(
                    active ? SessionRecoveryDetails.State.ACTIVE : SessionRecoveryDetails.State.RESTORING,
                    voiceTargets.getOrDefault(guildId, 0L),
                    0L,
                    manager != null && manager.getAudioPlayer().isPaused(),
                    manager == null ? 0 : manager.getAudioPlayer().getVolume(),
                    manager == null ? RepeatMode.OFF : manager.getScheduler().getRepeatMode(),
                    manager == null ? 0 : (manager.getScheduler().getCurrentRequest() == null ? 0 : 1)
                            + manager.getScheduler().queueSize(),
                    manager == null ? 0 : manager.getScheduler().historySize(),
                    manager == null || manager.getAudioPlayer().getPlayingTrack() == null
                            ? 0L : Math.max(0L, manager.getAudioPlayer().getPlayingTrack().getPosition()),
                    lastSessionRecoveryEvent.get());
        }
        return new SessionRecoveryDetails(
                active ? SessionRecoveryDetails.State.ACTIVE
                        : restoring ? SessionRecoveryDetails.State.RESTORING : SessionRecoveryDetails.State.SAVED,
                stored.voiceChannelId(),
                stored.capturedAtEpochMillis(),
                stored.paused(),
                stored.volume(),
                stored.repeatMode(),
                stored.trackCount(),
                stored.history().size(),
                stored.currentTrack() == null ? 0L : stored.currentTrack().safeResumePositionMillis(),
                lastSessionRecoveryEvent.get());
    }

    public ManualSessionRecoveryResult retryPersistedSession(Guild guild) {
        Objects.requireNonNull(guild, "guild");
        if (closing.get()) {
            return new ManualSessionRecoveryResult(ManualSessionRecoveryStatus.UNAVAILABLE,
                    "Бот завершает работу; recovery недоступен.");
        }
        long guildId = guild.getIdLong();
        if (musicManagers.containsKey(guildId) || startupRestoresInProgress.contains(guildId)) {
            return new ManualSessionRecoveryResult(ManualSessionRecoveryStatus.ALREADY_ACTIVE,
                    "Музыкальная сессия уже активна или восстанавливается.");
        }
        StoredMusicSession stored = sessionRepository.session(guildId).orElse(null);
        if (stored == null) {
            return new ManualSessionRecoveryResult(ManualSessionRecoveryStatus.NO_CHECKPOINT,
                    "Сохранённого checkpoint для этого сервера нет.");
        }
        if (stored.expired(Instant.now(), sessionProperties.getMaxAge())) {
            removeCheckpointSafely(guildId, "expired manual checkpoint");
            return new ManualSessionRecoveryResult(ManualSessionRecoveryStatus.EXPIRED,
                    "Checkpoint устарел и был удалён.");
        }
        AudioChannel target = resolveAudioChannel(guild, stored.voiceChannelId());
        if (target == null) {
            removeCheckpointSafely(guildId, "manual checkpoint channel missing");
            return new ManualSessionRecoveryResult(ManualSessionRecoveryStatus.CHANNEL_MISSING,
                    "Сохранённый голосовой канал больше не существует.");
        }
        if (sessionProperties.isRequireHumanListener()
                && !hasHumanListener(guild, stored.voiceChannelId())) {
            return new ManualSessionRecoveryResult(ManualSessionRecoveryStatus.WAITING_FOR_LISTENER,
                    "Recovery ожидает человека в сохранённом голосовом канале <#"
                            + stored.voiceChannelId() + ">.");
        }
        sessionLifecycleScheduler.execute(() -> restoreStoredSession(guild, target, stored));
        updateSessionEvent("manual restore requested: guild=" + guild.getId()
                + " channel=" + target.getId() + " tracks=" + stored.recoveryTrackCount());
        return new ManualSessionRecoveryResult(ManualSessionRecoveryStatus.STARTED,
                "Recovery запущен для <#" + target.getId() + ">.");
    }

    public enum ManualSessionRecoveryStatus {
        STARTED,
        NO_CHECKPOINT,
        ALREADY_ACTIVE,
        EXPIRED,
        CHANNEL_MISSING,
        WAITING_FOR_LISTENER,
        UNAVAILABLE
    }

    public record ManualSessionRecoveryResult(
            ManualSessionRecoveryStatus status,
            String details) {
    }

    private void restoreStoredSession(
            Guild guild,
            AudioChannel target,
            StoredMusicSession stored) {
        long guildId = guild.getIdLong();
        if (musicManagers.containsKey(guildId) || !startupRestoresInProgress.add(guildId)) {
            return;
        }
        voiceTargets.put(guildId, target.getIdLong());
        updateSessionEvent("startup restore requested: guild=" + guild.getId()
                + " channel=" + target.getId() + " tracks=" + stored.trackCount());
        log.info("Restoring persisted music session: guild={}, channel={}, tracks={}, position={}ms, paused={}",
                guild.getId(),
                target.getId(),
                stored.trackCount(),
                stored.currentTrack() == null ? 0L : stored.currentTrack().positionMillis(),
                stored.paused());

        ensureVoiceConnection(guild, target, false, true)
                .whenComplete((connection, failure) -> {
                    if (closing.get()) {
                        startupRestoresInProgress.remove(guildId);
                        return;
                    }
                    if (failure != null || connection == null || !connection.connected()) {
                        startupRestoresInProgress.remove(guildId);
                        startupRestoreFailures.incrementAndGet();
                        String details = failure == null
                                ? connection == null ? "missing connection result" : connection.details()
                                : failure.getClass().getSimpleName() + ": " + failure.getMessage();
                        updateSessionEvent("startup restore failed: guild=" + guild.getId() + " " + details);
                        log.warn("Startup session restore could not connect: guild={}, details={}",
                                guild.getId(), details);
                        releaseSession(guild, false);
                        return;
                    }
                    GuildMusicManager manager = musicManagers.get(guildId);
                    if (manager == null || !manager.isActive()) {
                        startupRestoresInProgress.remove(guildId);
                        startupRestoreFailures.incrementAndGet();
                        updateSessionEvent("startup restore failed: guild=" + guild.getId()
                                + " session disappeared after voice connect");
                        return;
                    }
                    manager.getAudioPlayer().setVolume(stored.volume());
                    manager.getScheduler().setRepeatMode(stored.repeatMode());
                    List<StoredSessionTrack> ordered = new ArrayList<>();
                    if (stored.currentTrack() != null) {
                        ordered.add(stored.currentTrack());
                    }
                    ordered.addAll(stored.queue());
                    restoreTracksSequentially(guild, manager, stored, ordered, 0, new AtomicInteger());
                });
    }

    private void restoreTracksSequentially(
            Guild guild,
            GuildMusicManager manager,
            StoredMusicSession stored,
            List<StoredSessionTrack> ordered,
            int index,
            AtomicInteger accepted) {
        long guildId = guild.getIdLong();
        if (closing.get()
                || !manager.isActive()
                || musicManagers.get(guildId) != manager) {
            startupRestoresInProgress.remove(guildId);
            return;
        }
        if (index >= ordered.size()) {
            if (accepted.get() == 0) {
                startupRestoresInProgress.remove(guild.getIdLong());
                startupRestoreFailures.incrementAndGet();
                updateSessionEvent("startup restore failed: guild=" + guild.getId()
                        + " no saved tracks could be loaded");
                log.warn("No saved tracks could be restored for guild {}", guild.getId());
                releaseSession(guild, false);
                return;
            }
            restoreHistorySequentially(
                    guild, manager, stored, accepted.get(), ordered.size(), 0, new ArrayList<>(), new AtomicInteger());
            return;
        }

        StoredSessionTrack saved = ordered.get(index);
        loadAndPlay(
                guild,
                saved.track().playbackIdentifier(),
                saved.requester(),
                true,
                result -> {
                    boolean loaded = result.status() == MusicLoadResult.Status.STARTED
                            || result.status() == MusicLoadResult.Status.QUEUED;
                    if (loaded) {
                        accepted.incrementAndGet();
                    }
                    boolean isSavedCurrent = index == 0 && stored.currentTrack() != null;
                    if (loaded
                            && result.status() == MusicLoadResult.Status.STARTED
                            && result.track() != null) {
                        if (isSavedCurrent && result.track().isSeekable()) {
                            result.track().setPosition(saved.safeResumePositionMillis());
                        }
                        if (stored.paused()) {
                            manager.getAudioPlayer().setPaused(true);
                        }
                    }
                    restoreTracksSequentially(
                            guild,
                            manager,
                            stored,
                            ordered,
                            index + 1,
                            accepted);
                });
    }

    private void restoreHistorySequentially(
            Guild guild,
            GuildMusicManager manager,
            StoredMusicSession stored,
            int acceptedTracks,
            int totalTracks,
            int index,
            List<TrackRequest> restoredHistory,
            AtomicInteger historyFailures) {
        long guildId = guild.getIdLong();
        if (closing.get() || !manager.isActive() || musicManagers.get(guildId) != manager) {
            startupRestoresInProgress.remove(guildId);
            return;
        }
        if (index >= stored.history().size()) {
            manager.getScheduler().restoreHistory(restoredHistory);
            startupHistoryTracksRestored.addAndGet(restoredHistory.size());
            startupHistoryTrackFailures.addAndGet(historyFailures.get());
            manager.getAudioPlayer().setPaused(stored.paused());
            startupRestoresInProgress.remove(guildId);
            startupRestoreSuccesses.incrementAndGet();
            updateSessionEvent("startup restore complete: guild=" + guild.getId()
                    + " accepted=" + acceptedTracks + "/" + totalTracks
                    + " history=" + restoredHistory.size() + "/" + stored.history().size());
            checkpointSessionSafely(manager);
            log.info("Persisted music session restored: guild={}, accepted={}/{}, history={}/{}, paused={}",
                    guild.getId(), acceptedTracks, totalTracks, restoredHistory.size(),
                    stored.history().size(), stored.paused());
            return;
        }

        StoredSessionTrack saved = stored.history().get(index);
        loadStoredRequest(guild, saved, restored -> {
            if (restored != null) {
                restoredHistory.add(restored);
            } else {
                historyFailures.incrementAndGet();
            }
            restoreHistorySequentially(
                    guild, manager, stored, acceptedTracks, totalTracks, index + 1,
                    restoredHistory, historyFailures);
        });
    }

    private void loadStoredRequest(
            Guild guild,
            StoredSessionTrack saved,
            Consumer<TrackRequest> resultConsumer) {
        String identifier = saved.track().playbackIdentifier();
        Object orderingKey = "session-history:" + guild.getIdLong();
        audioPlayerManager.loadItemOrdered(orderingKey, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                resultConsumer.accept(isPlayableCandidate(track)
                        ? TrackRequest.create(track, saved.requester(), List.of())
                        : null);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack selected = playlist.getSelectedTrack();
                if (selected == null && !playlist.getTracks().isEmpty()) {
                    selected = playlist.getTracks().get(0);
                }
                resultConsumer.accept(selected != null && isPlayableCandidate(selected)
                        ? TrackRequest.create(selected, saved.requester(), List.of())
                        : null);
            }

            @Override
            public void noMatches() {
                log.warn("Saved previous-track could not be resolved during recovery: guild={}, identifier={}",
                        guild.getId(), identifier);
                resultConsumer.accept(null);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                log.warn("Saved previous-track failed during recovery: guild={}, identifier={}, reason={}",
                        guild.getId(), identifier, SourceFailureFormatter.describe(identifier, exception));
                resultConsumer.accept(null);
            }
        });
    }

    private void recoverVoiceSession(Guild guild, String reason) {
        if (closing.get() || !sessionProperties.isVoiceRecoveryEnabled()) {
            return;
        }
        long guildId = guild.getIdLong();
        GuildMusicManager manager = musicManagers.get(guildId);
        if (manager == null || !manager.isActive() || !playbackExpected(manager)) {
            return;
        }
        Long targetId = voiceTargets.get(guildId);
        if (targetId == null) {
            var selfState = guild.getSelfMember().getVoiceState();
            if (selfState != null && selfState.getChannel() != null) {
                targetId = selfState.getChannel().getIdLong();
                voiceTargets.put(guildId, targetId);
            }
        }
        if (targetId == null) {
            recoveryFailures.incrementAndGet();
            updateSessionEvent("voice recovery impossible: guild=" + guild.getId() + " no target channel");
            checkpointSessionSafely(manager);
            releaseSession(guild, false);
            return;
        }
        AudioChannel target = resolveAudioChannel(guild, targetId);
        if (target == null) {
            recoveryFailures.incrementAndGet();
            updateSessionEvent("voice recovery impossible: guild=" + guild.getId()
                    + " target channel missing");
            checkpointSessionSafely(manager);
            releaseSession(guild, false);
            return;
        }

        boolean wasPaused = manager.getAudioPlayer().isPaused();
        VoiceRecoveryRun run = new VoiceRecoveryRun(manager, target, wasPaused, reason);
        if (voiceRecoveries.putIfAbsent(guildId, run) != null) {
            return;
        }
        manager.getAudioPlayer().setPaused(true);
        attemptVoiceRecovery(guild, run, 1);
    }

    private void attemptVoiceRecovery(Guild guild, VoiceRecoveryRun run, int attempt) {
        long guildId = guild.getIdLong();
        if (voiceRecoveries.get(guildId) != run
                || musicManagers.get(guildId) != run.manager()
                || !run.manager().isActive()) {
            voiceRecoveries.remove(guildId, run);
            return;
        }
        recoveryAttempts.incrementAndGet();
        updateSessionEvent("voice recovery attempt " + attempt + "/"
                + sessionProperties.getMaxRecoveryAttempts() + ": guild=" + guild.getId()
                + " reason=" + run.reason());
        log.warn("Voice recovery attempt {}/{}: guild={}, channel={}, reason={}",
                attempt,
                sessionProperties.getMaxRecoveryAttempts(),
                guild.getId(),
                run.target().getId(),
                run.reason());

        ensureVoiceConnection(guild, run.target(), false, true)
                .whenComplete((result, failure) -> {
                    if (closing.get()
                            || voiceRecoveries.get(guildId) != run
                            || musicManagers.get(guildId) != run.manager()
                            || !run.manager().isActive()) {
                        voiceRecoveries.remove(guildId, run);
                        return;
                    }
                    boolean connected = failure == null && result != null && result.connected();
                    if (connected) {
                        voiceRecoveries.remove(guildId, run);
                        if (!run.wasPaused()) {
                            run.manager().getAudioPlayer().setPaused(false);
                        }
                        recoverySuccesses.incrementAndGet();
                        voiceFrameDemandMissingSince.remove(guildId);
                        voiceWatchdogReported.remove(guildId);
                        updateSessionEvent("voice recovery complete: guild=" + guild.getId()
                                + " attempt=" + attempt);
                        checkpointSessionSafely(run.manager());
                        log.info("Voice session recovered: guild={}, channel={}, attempt={}",
                                guild.getId(), run.target().getId(), attempt);
                        return;
                    }
                    if (attempt < sessionProperties.getMaxRecoveryAttempts()) {
                        long delay = Math.multiplyExact(
                                sessionProperties.getRecoveryBackoff().toMillis(),
                                (long) attempt);
                        sessionLifecycleScheduler.schedule(
                                () -> attemptVoiceRecovery(guild, run, attempt + 1),
                                delay,
                                TimeUnit.MILLISECONDS);
                        return;
                    }
                    voiceRecoveries.remove(guildId, run);
                    recoveryFailures.incrementAndGet();
                    String details = failure != null
                            ? failure.getClass().getSimpleName() + ": " + failure.getMessage()
                            : result == null ? "missing connection result" : result.details();
                    updateSessionEvent("voice recovery exhausted: guild=" + guild.getId()
                            + " details=" + details);
                    run.manager().getAudioPlayer().setPaused(run.wasPaused());
                    checkpointSessionSafely(run.manager());
                    log.error("Voice recovery exhausted; preserving checkpoint: guild={}, attempts={}, details={}",
                            guild.getId(), sessionProperties.getMaxRecoveryAttempts(), details);
                    releaseSession(guild, false);
                });
    }

    private void checkpointAllSessionsSafely() {
        try {
            Instant now = Instant.now();
            intentionalDisconnectUntil.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
            for (StoredMusicSession stored : sessionRepository.sessions()) {
                if (!musicManagers.containsKey(stored.guildId())
                        && stored.expired(now, sessionProperties.getMaxAge())) {
                    removeCheckpointSafely(stored.guildId(), "expired inactive checkpoint");
                }
            }
            for (GuildMusicManager manager : musicManagers.values()) {
                long guildId = manager.getGuild().getIdLong();
                if (!startupRestoresInProgress.contains(guildId)
                        && !voiceRecoveries.containsKey(guildId)) {
                    checkpointSessionSafely(manager);
                }
            }
        } catch (RuntimeException exception) {
            log.error("Music session checkpoint sweep failed", exception);
        }
    }

    private void checkpointSessionSafely(GuildMusicManager manager) {
        if (manager == null || !manager.isActive()) {
            return;
        }
        try {
            StoredMusicSession snapshot = snapshotSession(manager);
            if (snapshot == null) {
                sessionRepository.remove(manager.getGuild().getIdLong());
            } else {
                sessionRepository.save(snapshot);
            }
        } catch (RuntimeException exception) {
            log.error("Cannot persist music session checkpoint for guild {}",
                    manager.getGuild().getId(), exception);
        }
    }

    private StoredMusicSession snapshotSession(GuildMusicManager manager) {
        Guild guild = manager.getGuild();
        long guildId = guild.getIdLong();
        Long channelId = voiceTargets.get(guildId);
        if (channelId == null) {
            var selfState = guild.getSelfMember().getVoiceState();
            if (selfState != null && selfState.getChannel() != null) {
                channelId = selfState.getChannel().getIdLong();
                voiceTargets.put(guildId, channelId);
            }
        }
        if (channelId == null || channelId <= 0L) {
            return null;
        }

        TrackRequest current = manager.getScheduler().getCurrentRequest();
        StoredSessionTrack currentStored = current == null
                ? null
                : StoredSessionTrack.from(current, current.track().getPosition()).orElse(null);
        List<StoredSessionTrack> queue = manager.getScheduler().queuedRequests().stream()
                .map(request -> StoredSessionTrack.from(request, 0L).orElse(null))
                .filter(Objects::nonNull)
                .limit(properties.getMaxQueueSize())
                .toList();
        List<StoredSessionTrack> history = manager.getScheduler().historyRequests().stream()
                .map(request -> StoredSessionTrack.from(request, 0L).orElse(null))
                .filter(Objects::nonNull)
                .limit(25)
                .toList();
        if (currentStored == null && queue.isEmpty()) {
            return null;
        }
        return new StoredMusicSession(
                guildId,
                channelId,
                System.currentTimeMillis(),
                manager.getAudioPlayer().isPaused(),
                manager.getAudioPlayer().getVolume(),
                manager.getScheduler().getRepeatMode(),
                currentStored,
                queue,
                history);
    }

    private void removeCheckpointSafely(long guildId, String reason) {
        try {
            sessionRepository.remove(guildId);
            updateSessionEvent("checkpoint removed: guild=" + guildId + " reason=" + reason);
        } catch (RuntimeException exception) {
            log.error("Cannot remove music session checkpoint for guild {}", guildId, exception);
        }
    }

    private static AudioChannel resolveAudioChannel(Guild guild, long channelId) {
        AudioChannel channel = guild.getVoiceChannelById(channelId);
        if (channel != null) {
            return channel;
        }
        return guild.getStageChannelById(channelId);
    }

    private static boolean hasHumanListener(Guild guild, long channelId) {
        return guild.getVoiceStates().stream()
                .anyMatch(state -> state.getChannel() != null
                        && state.getChannel().getIdLong() == channelId
                        && !state.getMember().getUser().isBot());
    }

    private static boolean playbackExpected(GuildMusicManager manager) {
        return manager.getAudioPlayer().getPlayingTrack() != null
                || manager.getScheduler().getCurrentRequest() != null
                || manager.getScheduler().queueSize() > 0;
    }

    private void updateSessionEvent(String event) {
        String safe = event == null ? "none" : event.replace('\n', ' ').replace('\r', ' ').trim();
        if (safe.length() > 700) {
            safe = safe.substring(0, 697) + "...";
        }
        lastSessionRecoveryEvent.set(Instant.now() + " " + safe);
    }

    public void stopAndRelease(Guild guild) {
        releaseSession(guild, true);
    }

    private void releaseSession(Guild guild, boolean removeCheckpoint) {
        long guildId = guild.getIdLong();
        GuildMusicManager manager = musicManagers.remove(guildId);
        radioStates.remove(guildId);
        cancelIdleDisconnect(guildId);
        voiceFrameDemandMissingSince.remove(guildId);
        voiceWatchdogNotBefore.remove(guildId);
        voiceWatchdogReported.remove(guildId);
        voiceRecoveries.remove(guildId);
        startupRestoresInProgress.remove(guildId);
        intentionalDisconnectUntil.put(guildId, Instant.now().plusSeconds(10L));
        voiceConnections.cancel(guild);
        safeCloseAudio(guild);
        voiceTargets.remove(guildId);
        if (manager != null) {
            manager.destroy();
        }
        if (removeCheckpoint) {
            removeCheckpointSafely(guildId, "session released");
        }
    }

    private void deliverQueueResult(
            Guild guild,
            GuildMusicManager musicManager,
            AudioTrack track,
            TrackRequester requester,
            List<AudioTrack> fallbackTracks,
            boolean recoveryRestore,
            Consumer<MusicLoadResult> resultConsumer) {
        if (!musicManager.isActive()) {
            log.info("Ignoring completed media load for closed guild session {}", guild.getId());
            resultConsumer.accept(MusicLoadResult.withoutTrack(MusicLoadResult.Status.SESSION_CLOSED));
            return;
        }

        TrackScheduler.QueueResult queueResult = recoveryRestore
                ? musicManager.getScheduler().queueRecovered(track, requester, fallbackTracks)
                : musicManager.getScheduler().queue(track, requester, fallbackTracks);
        MusicLoadResult.Status status = switch (queueResult.status()) {
            case STARTED -> MusicLoadResult.Status.STARTED;
            case QUEUED -> MusicLoadResult.Status.QUEUED;
            case REQUESTER_LIMIT -> MusicLoadResult.Status.REQUESTER_LIMIT;
            case QUEUE_FULL -> MusicLoadResult.Status.QUEUE_FULL;
            case TRACK_TOO_LONG -> MusicLoadResult.Status.TRACK_TOO_LONG;
            case STREAM_NOT_ALLOWED -> MusicLoadResult.Status.STREAM_NOT_ALLOWED;
        };

        if (status == MusicLoadResult.Status.REQUESTER_LIMIT
                || status == MusicLoadResult.Status.QUEUE_FULL
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

    private void handleMusicIdle(Guild guild) {
        GuildMusicManager manager = musicManagers.get(guild.getIdLong());
        if (manager != null && radioStates.containsKey(guild.getIdLong())) {
            triggerRadioRefill(guild, manager);
            return;
        }
        scheduleIdleDisconnect(guild);
    }

    private void triggerRadioRefill(Guild guild, GuildMusicManager manager) {
        long guildId = guild.getIdLong();
        RadioState state = radioStates.get(guildId);
        if (state == null || !manager.isActive() || !manager.isIdle() || state.refillInProgress()) {
            return;
        }
        long activityVersion = manager.getActivityVersion();
        StoredTrack seed = state.beginRefill(radioSeeds(guildId, state.mode(), state.owner().userId()));
        if (seed == null) {
            radioFailure(guild, manager, state, "Нет локальных seed-треков");
            return;
        }

        String query = MediaQueryResolver.YOUTUBE_SEARCH_PREFIX + radioQuery(seed);
        audioPlayerManager.loadItemOrdered("radio:" + guildId, query, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                finishRadioSearch(guild, manager, state, activityVersion, seed, List.of(track));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                finishRadioSearch(guild, manager, state, activityVersion, seed, searchCandidates(playlist, 10));
            }

            @Override
            public void noMatches() {
                radioFailure(guild, manager, state, "Поиск не вернул кандидатов");
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                radioFailure(guild, manager, state, SourceFailureFormatter.describe(query, exception));
            }
        });
    }

    private void finishRadioSearch(
            Guild guild,
            GuildMusicManager manager,
            RadioState state,
            long activityVersion,
            StoredTrack seed,
            List<AudioTrack> candidates) {
        long guildId = guild.getIdLong();
        if (radioStates.get(guildId) != state || !manager.isActive()) {
            state.cancelRefill();
            return;
        }
        if (manager.getActivityVersion() != activityVersion || !manager.isIdle()) {
            state.cancelRefill();
            return;
        }

        Set<String> excluded = new LinkedHashSet<>(state.recentTrackKeys());
        excluded.add(storedTrackKey(seed));
        musicLibraryRepository.history(guildId).stream()
                .limit(8)
                .map(PlayerManager::storedTrackKey)
                .forEach(excluded::add);

        AudioTrack selected = candidates == null ? null : candidates.stream()
                .filter(this::isPlayableCandidate)
                .filter(track -> !excluded.contains(trackKey(track).toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            radioFailure(guild, manager, state, "Все найденные кандидаты уже недавно звучали");
            return;
        }

        TrackRequester radioRequester = new TrackRequester(0L, "📻 Radio");
        TrackScheduler.QueueResult result = manager.getScheduler().queue(selected, radioRequester, List.of());
        if (result.status() != TrackScheduler.QueueStatus.STARTED
                && result.status() != TrackScheduler.QueueStatus.QUEUED) {
            radioFailure(guild, manager, state, "Кандидат отклонён queue policy: " + result.status());
            return;
        }
        state.completeRefill(seed.title(), selected.getInfo().title, trackKey(selected));
        cancelIdleDisconnect(guildId);
        log.info("Smart radio generated track: guild={}, mode={}, seed={}, track={}",
                guildId, state.mode(), seed.title(), selected.getInfo().title);
    }

    private void radioFailure(Guild guild, GuildMusicManager manager, RadioState state, String reason) {
        if (radioStates.get(guild.getIdLong()) != state) {
            state.cancelRefill();
            return;
        }
        int failures = state.failRefill();
        log.warn("Smart radio refill failed: guild={}, failures={}, reason={}", guild.getId(), failures, reason);
        if (failures >= 3) {
            radioStates.remove(guild.getIdLong(), state);
            log.warn("Smart radio disabled after three consecutive refill failures: guild={}", guild.getId());
            if (manager.isIdle()) {
                scheduleIdleDisconnect(guild);
            }
            return;
        }
        if (manager.isIdle()) {
            idleScheduler.schedule(() -> triggerRadioRefill(guild, manager), 750L, TimeUnit.MILLISECONDS);
        }
    }

    private List<StoredTrack> radioSeeds(long guildId, RadioMode mode, long userId) {
        if (mode == RadioMode.PERSONAL) {
            if (userId <= 0L) {
                return List.of();
            }
            return PersonalListeningInsights.discoverySeeds(
                    musicLibraryRepository.favorites(guildId, userId),
                    musicLibraryRepository.personalHistory(guildId, userId),
                    20);
        }
        return PersonalListeningInsights.discoverySeeds(
                List.of(),
                musicLibraryRepository.history(guildId),
                20);
    }

    private static String radioQuery(StoredTrack seed) {
        String title = seed.title() == null ? "" : seed.title().trim();
        String author = seed.author() == null ? "" : seed.author().trim();
        String query = author.isBlank() || "Неизвестно".equalsIgnoreCase(author)
                ? title
                : author + " " + title;
        return query.length() <= 100 ? query : query.substring(0, 100).trim();
    }

    private static String storedTrackKey(StoredTrack track) {
        return track == null || track.playbackIdentifier() == null
                ? "unknown"
                : track.playbackIdentifier().trim().toLowerCase(Locale.ROOT);
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
                log.error("{}: guild={}, recoveryEnabled={}, legacyEnforce={}",
                        reason,
                        guild.getId(),
                        sessionProperties.isVoiceRecoveryEnabled(),
                        properties.isVoiceWatchdogEnforce());
            }
            if (sessionProperties.isVoiceRecoveryEnabled()) {
                voiceConnections.recordTransportFailure(guild, reason);
                recoverVoiceSession(guild, reason);
                continue;
            }
            if (properties.isVoiceWatchdogEnforce()) {
                voiceConnections.recordTransportFailure(guild, reason);
                stopAndRelease(guild);
            }
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
        String uri = track.getInfo().uri;
        if (uri != null && !uri.isBlank()) {
            return uri.trim().toLowerCase(Locale.ROOT);
        }
        String identifier = track.getInfo().identifier;
        if (identifier != null && !identifier.isBlank()) {
            return identifier.trim().toLowerCase(Locale.ROOT);
        }
        return (String.valueOf(track.getInfo().title) + ':' + track.getDuration()).toLowerCase(Locale.ROOT);
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
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        log.info("Shutting down {} music sessions", musicManagers.size());
        checkpointAllSessionsSafely();
        for (GuildMusicManager manager : musicManagers.values()) {
            Guild guild = manager.getGuild();
            intentionalDisconnectUntil.put(guild.getIdLong(), Instant.now().plusSeconds(10L));
            voiceConnections.cancel(guild);
            safeCloseAudio(guild);
            manager.destroy();
        }
        musicManagers.clear();
        idleDisconnects.values().forEach(future -> future.cancel(false));
        idleDisconnects.clear();
        voiceTargets.clear();
        voiceRecoveries.clear();
        startupRestoresInProgress.clear();
        intentionalDisconnectUntil.clear();
        voiceFrameDemandMissingSince.clear();
        voiceWatchdogNotBefore.clear();
        voiceWatchdogReported.clear();
        radioStates.clear();
        idleScheduler.shutdownNow();
        playbackReadinessScheduler.shutdownNow();
        sessionLifecycleScheduler.shutdownNow();
        audioPlayerManager.shutdown();
    }

    private record VoiceRecoveryRun(
            GuildMusicManager manager,
            AudioChannel target,
            boolean wasPaused,
            String reason) {
    }

    private static final class RadioState {
        private static final int RECENT_LIMIT = 10;

        private final RadioMode mode;
        private final TrackRequester owner;
        private final ArrayDeque<String> recentTrackKeys = new ArrayDeque<>();
        private long generatedTracks;
        private int consecutiveFailures;
        private boolean refillInProgress;
        private int seedCursor;
        private String lastSeed = "—";
        private String lastTrack = "—";

        private RadioState(RadioMode mode, TrackRequester owner) {
            this.mode = mode;
            this.owner = owner;
        }

        synchronized RadioMode mode() {
            return mode;
        }

        synchronized TrackRequester owner() {
            return owner;
        }

        synchronized boolean refillInProgress() {
            return refillInProgress;
        }

        synchronized StoredTrack beginRefill(List<StoredTrack> seeds) {
            if (refillInProgress || seeds == null || seeds.isEmpty()) {
                return null;
            }
            refillInProgress = true;
            StoredTrack seed = seeds.get(Math.floorMod(seedCursor, seeds.size()));
            seedCursor++;
            return seed;
        }

        synchronized void cancelRefill() {
            refillInProgress = false;
        }

        synchronized void completeRefill(String seed, String track, String trackKey) {
            refillInProgress = false;
            consecutiveFailures = 0;
            generatedTracks++;
            lastSeed = seed == null ? "—" : seed;
            lastTrack = track == null ? "—" : track;
            String key = trackKey == null ? "unknown" : trackKey.toLowerCase(Locale.ROOT);
            recentTrackKeys.remove(key);
            recentTrackKeys.addFirst(key);
            while (recentTrackKeys.size() > RECENT_LIMIT) {
                recentTrackKeys.removeLast();
            }
        }

        synchronized int failRefill() {
            refillInProgress = false;
            return ++consecutiveFailures;
        }

        synchronized List<String> recentTrackKeys() {
            return List.copyOf(recentTrackKeys);
        }

        synchronized RadioSnapshot snapshot(boolean enabled) {
            return new RadioSnapshot(
                    enabled,
                    mode,
                    owner.userId(),
                    owner.displayName(),
                    generatedTracks,
                    consecutiveFailures,
                    refillInProgress,
                    lastSeed,
                    lastTrack);
        }
    }

}
