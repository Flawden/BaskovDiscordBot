package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
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
    }

    /**
     * Добавляет трек в очередь.
     *
     * <p>Если аудиоплеер не воспроизводит текущий трек, новый трек добавляется в очередь.</p>
     *
     * @param track Трек, который будет добавлен в очередь.
     */
    public void queue(AudioTrack track) {
        if (!this.audioPlayer.startTrack(track, true)) {
            this.queue.offer(track);
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
        if (endReason.mayStartNext) {
            if (queue.isEmpty()) {
                guild.getAudioManager().closeAudioConnection();
            } else {
                player.playTrack(queue.poll());
            }
        }
    }

    /**
     * Запускает следующий трек из очереди, если таковой имеется.
     */
    public void nextTrack() {
        this.audioPlayer.startTrack(this.queue.poll(), false);
    }
}
