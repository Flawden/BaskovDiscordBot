package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Component;
import ru.flawden.BascovDiscordBot.config.MusicProperties;

import java.awt.Color;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Spring-managed lifecycle всех музыкальных сессий.
 */
@Slf4j
@Component
public class PlayerManager {

    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> idleDisconnects = new ConcurrentHashMap<>();
    private final DefaultAudioPlayerManager audioPlayerManager;
    private final ScheduledExecutorService idleScheduler;
    private final MusicProperties properties;

    public PlayerManager(MusicProperties properties) {
        this.properties = properties;
        this.audioPlayerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(this.audioPlayerManager);
        this.idleScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "baskov-music-idle-disconnect");
            thread.setDaemon(true);
            return thread;
        });
        log.info("PlayerManager initialized: maxQueue={}, maxTrack={}, idleTimeout={}",
                properties.getMaxQueueSize(),
                properties.getMaxTrackDuration(),
                properties.getIdleDisconnectTimeout());
    }

    public GuildMusicManager getMusicManager(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(), guildId -> {
            GuildMusicManager manager = new GuildMusicManager(
                    audioPlayerManager,
                    guild,
                    properties,
                    () -> cancelIdleDisconnect(guildId),
                    () -> scheduleIdleDisconnect(guild));
            guild.getAudioManager().setSendingHandler(manager.getSendHandler());
            return manager;
        });
    }

    public Optional<GuildMusicManager> findMusicManager(Guild guild) {
        return Optional.ofNullable(musicManagers.get(guild.getIdLong()));
    }

    public void loadAndPlay(TextChannel textChannel, String identifier) {
        GuildMusicManager musicManager = getMusicManager(textChannel.getGuild());
        musicManager.markActivity();
        log.info("Loading media for guild {}: {}", textChannel.getGuild().getId(), identifier);

        audioPlayerManager.loadItemOrdered(musicManager, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                announceQueueResult(textChannel, musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack selected = playlist.getSelectedTrack();
                if (selected == null && !playlist.getTracks().isEmpty()) {
                    selected = playlist.getTracks().get(0);
                }

                if (selected == null) {
                    sendError(textChannel, "❌ Ничего не найдено",
                            "Плейлист или результаты поиска не содержат доступных треков.");
                    musicManager.getScheduler().scheduleDisconnectIfIdle();
                    return;
                }

                announceQueueResult(textChannel, musicManager, selected);
            }

            @Override
            public void noMatches() {
                sendError(textChannel, "❌ Песня не найдена",
                        "Проверь название или ссылку и попробуй снова.");
                musicManager.getScheduler().scheduleDisconnectIfIdle();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                log.warn("Media load failed in guild {}: {}",
                        textChannel.getGuild().getId(), exception.getMessage());
                sendError(textChannel, "❌ Ошибка загрузки",
                        "Не удалось загрузить трек. Попробуй снова чуть позже.");
                musicManager.getScheduler().scheduleDisconnectIfIdle();
            }
        });
    }

    public void stopAndRelease(Guild guild) {
        GuildMusicManager manager = musicManagers.remove(guild.getIdLong());
        cancelIdleDisconnect(guild.getIdLong());
        guild.getAudioManager().closeAudioConnection();
        guild.getAudioManager().setSendingHandler(null);
        if (manager != null) {
            manager.destroy();
        }
    }

    private void announceQueueResult(TextChannel textChannel, GuildMusicManager manager, AudioTrack track) {
        if (!manager.isActive()) {
            log.info("Ignoring completed media load for closed guild session {}", textChannel.getGuild().getId());
            return;
        }
        TrackScheduler.QueueResult result = manager.getScheduler().queue(track);
        EmbedBuilder embed = new EmbedBuilder().setColor(Color.GREEN);

        switch (result.status()) {
            case STARTED -> {
                embed.setTitle("▶️ Воспроизведение началось");
                embed.setDescription(formatTrack(track));
            }
            case QUEUED -> {
                embed.setTitle("🎶 Добавлено в очередь");
                embed.setDescription(formatTrack(track)
                        + "\n**Позиция:** `" + result.queuePosition() + "`");
            }
            case QUEUE_FULL -> {
                embed.setColor(Color.RED);
                embed.setTitle("🚧 Очередь заполнена");
                embed.setDescription("В очереди уже `" + properties.getMaxQueueSize()
                        + "` треков. Дождись свободного места.");
                manager.getScheduler().scheduleDisconnectIfIdle();
            }
            case TRACK_TOO_LONG -> {
                embed.setColor(Color.RED);
                embed.setTitle("⏱️ Трек слишком длинный");
                embed.setDescription("Максимальная длительность трека: `"
                        + humanDuration(properties.getMaxTrackDuration()) + "`.");
                manager.getScheduler().scheduleDisconnectIfIdle();
            }
            case STREAM_NOT_ALLOWED -> {
                embed.setColor(Color.RED);
                embed.setTitle("📡 Поток не поддерживается");
                embed.setDescription("Прямые трансляции отключены, чтобы музыкальная сессия не зависала навсегда.");
                manager.getScheduler().scheduleDisconnectIfIdle();
            }
        }

        textChannel.sendMessageEmbeds(embed.build()).queue(
                ignored -> { },
                failure -> log.warn("Failed to send queue response to channel {}: {}",
                        textChannel.getId(), failure.getMessage()));
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

    private void sendError(TextChannel channel, String title, String description) {
        channel.sendMessageEmbeds(new EmbedBuilder()
                        .setTitle(title)
                        .setDescription(description)
                        .setColor(Color.RED)
                        .build())
                .queue(
                        ignored -> { },
                        failure -> log.warn("Failed to send load error to channel {}: {}",
                                channel.getId(), failure.getMessage()));
    }

    private static String formatTrack(AudioTrack track) {
        return "**" + track.getInfo().title + "** — " + track.getInfo().author;
    }

    private static String humanDuration(Duration duration) {
        long minutes = duration.toMinutes();
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        if (hours == 0) {
            return minutes + " мин";
        }
        if (remainingMinutes == 0) {
            return hours + " ч";
        }
        return hours + " ч " + remainingMinutes + " мин";
    }

    int activeSessionCount() {
        return musicManagers.size();
    }

    @PreDestroy
    public void close() {
        log.info("Shutting down {} music sessions", musicManagers.size());
        for (GuildMusicManager manager : musicManagers.values()) {
            Guild guild = manager.getGuild();
            guild.getAudioManager().closeAudioConnection();
            guild.getAudioManager().setSendingHandler(null);
            manager.destroy();
        }
        musicManagers.clear();
        idleDisconnects.values().forEach(future -> future.cancel(false));
        idleDisconnects.clear();
        idleScheduler.shutdownNow();
        audioPlayerManager.shutdown();
    }
}
