package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Класс, отвечающий за планирование и управление воспроизведением аудиотреков.
 * TrackScheduler управляет очередью треков и воспроизводит их, когда предыдущий трек заканчивается.
 *
 * @author Flawden
 * @version 1.0
 */
@Slf4j
public class TrackScheduler extends AudioEventAdapter {

    /** Аудиоплеер, использующийся для воспроизведения треков. */
    public final AudioPlayer audioPlayer;

    private final Guild guild;

    /** Очередь для хранения треков, ожидающих воспроизведения. */
    public final BlockingQueue<AudioTrack> queue;


    public TrackScheduler(AudioPlayer audioPlayer, Guild guild) {
        this.audioPlayer = audioPlayer;
        this.guild = guild;
        this.queue = new LinkedBlockingQueue<>();
        log.info("TrackScheduler created for AudioPlayer: {}", audioPlayer);
    }

    /**
     * Добавляет трек в очередь.
     *
     * <p>Если аудиоплеер не воспроизводит текущий трек, новый трек добавляется в очередь.</p>
     *
     * @param track Трек, который будет добавлен в очередь.
     */
    public void queue(AudioTrack track) {
        log.debug("Queuing track: {}", track.getInfo().title);
        if (!this.audioPlayer.startTrack(track, true)) {
            queue.offer(track);
            log.info("Track queued: {} (queue size: {})", track.getInfo().title, queue.size());
        } else {
            log.info("Track started playing: {}", track.getInfo().title);
        }
    }

    /**
     * Вызывается, когда текущий трек заканчивается.
     *
     * @param player Аудиоплеер, который воспроизводил трек.
     * @param track Трек, который только что закончился.
     * @param endReason Причина окончания воспроизведения трека.
     */
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        log.info("Track ended: {} (reason: {})", track.getInfo().title, endReason);
        if (endReason.mayStartNext) {
            nextTrack();
        } else {
            log.warn("Cannot start next track due to end reason: {}", endReason);
        }
    }

    /**
     * Запускает следующий трек из очереди, если таковой имеется.
     */
    public void nextTrack() {
        AudioTrack nextTrack = queue.poll();
        if (nextTrack == null) {
            log.warn("No next track in queue, stopping playback");
            audioPlayer.stopTrack();
        } else {
            log.info("Playing next track: {}", nextTrack.getInfo().title);
            audioPlayer.startTrack(nextTrack, false);
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        log.error("Track exception for {}: {}", track.getInfo().title, exception.getMessage(), exception);
        nextTrack();
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        log.error("Track stuck: {} (threshold: {}ms)", track.getInfo().title, thresholdMs);
        nextTrack();
    }
}
