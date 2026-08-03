package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import ru.flawden.BascovDiscordBot.config.MusicProperties;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Изолированная музыкальная сессия одной Discord-гильдии.
 */
@Slf4j
public class GuildMusicManager {

    private final Guild guild;
    private final AudioPlayer audioPlayer;
    private final TrackScheduler scheduler;
    private final AudioPlayerSendHandler sendHandler;
    private final Runnable onActivity;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicLong activityVersion = new AtomicLong();

    public GuildMusicManager(
            AudioPlayerManager manager,
            Guild guild,
            MusicProperties properties,
            Runnable onActivity,
            Runnable onIdle) {
        this.guild = Objects.requireNonNull(guild, "guild");
        this.onActivity = Objects.requireNonNull(onActivity, "onActivity");
        this.audioPlayer = Objects.requireNonNull(manager, "manager").createPlayer();
        this.scheduler = new TrackScheduler(
                audioPlayer,
                properties.getMaxQueueSize(),
                properties.getMaxTrackDuration(),
                this::markActivity,
                onIdle);
        this.audioPlayer.addListener(this.scheduler);
        this.sendHandler = new AudioPlayerSendHandler(this.audioPlayer);
        log.info("Music session created for guild {}", guild.getId());
    }

    public Guild getGuild() {
        return guild;
    }

    public AudioPlayer getAudioPlayer() {
        return audioPlayer;
    }

    public TrackScheduler getScheduler() {
        return scheduler;
    }

    public AudioPlayerSendHandler getSendHandler() {
        return sendHandler;
    }

    public long markActivity() {
        long version = activityVersion.incrementAndGet();
        onActivity.run();
        return version;
    }

    public long getActivityVersion() {
        return activityVersion.get();
    }

    public boolean isIdle() {
        return active.get() && scheduler.isIdle();
    }

    public boolean isActive() {
        return active.get();
    }

    public void destroy() {
        if (!active.compareAndSet(true, false)) {
            return;
        }
        scheduler.clearQueue();
        audioPlayer.stopTrack();
        audioPlayer.destroy();
        log.info("Music session destroyed for guild {}", guild.getId());
    }
}
