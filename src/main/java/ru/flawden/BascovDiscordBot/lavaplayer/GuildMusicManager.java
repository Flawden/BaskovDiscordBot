package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import ru.flawden.BascovDiscordBot.config.MusicProperties;
import ru.flawden.BascovDiscordBot.settings.GuildPreferences;
import ru.flawden.BascovDiscordBot.operations.VoiceDiagnostics;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

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
            GuildPreferences preferences,
            Runnable onActivity,
            Runnable onIdle,
            VoiceDiagnostics diagnostics,
            Consumer<TrackRequest> historyListener) {
        this.guild = Objects.requireNonNull(guild, "guild");
        this.onActivity = Objects.requireNonNull(onActivity, "onActivity");
        this.audioPlayer = Objects.requireNonNull(manager, "manager").createPlayer();
        GuildPreferences initialPreferences = Objects.requireNonNull(preferences, "preferences");
        this.audioPlayer.setVolume(initialPreferences.volume());
        this.scheduler = new TrackScheduler(
                audioPlayer,
                properties.getMaxQueueSize(),
                properties.getMaxTrackDuration(),
                initialPreferences.repeatMode(),
                this::markActivity,
                onIdle,
                new TrackScheduler.Diagnostics() {
                    @Override
                    public void trackStarted(String title) {
                        diagnostics.trackStarted(guild.getIdLong(), title);
                    }

                    @Override
                    public void sourceFailure(String title, String reason) {
                        diagnostics.sourceFailure(guild.getIdLong(), title, reason);
                    }

                    @Override
                    public void cleanup(String title) {
                        diagnostics.cleanup(guild.getIdLong(), title);
                    }

                    @Override
                    public void fallback(String fromTitle, String toTitle) {
                        diagnostics.fallback(guild.getIdLong(), fromTitle, toTitle);
                    }

                    @Override
                    public void staleCallback(String callback, String title) {
                        diagnostics.staleCallback(guild.getIdLong(), callback, title);
                    }
                },
                Objects.requireNonNull(historyListener, "historyListener"));
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
