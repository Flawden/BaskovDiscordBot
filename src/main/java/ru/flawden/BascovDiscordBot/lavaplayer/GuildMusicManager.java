package ru.flawden.BascovDiscordBot.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;

/**
 * Менеджер музыки для гильдии, обеспечивающий взаимодействие с
 * {@link AudioPlayer} и {@link TrackScheduler}.
 * Этот класс управляет воспроизведением музыки в гильдии и
 * предоставляет доступ к {@link AudioPlayerSendHandler} для отправки
 * аудиоданных в Discord.
 *
 * @author Flawden
 * @version 1.0
 */
public class GuildMusicManager {

    public final AudioPlayer audioPlayer;
    public final TrackScheduler scheduler;
    private final AudioPlayerSendHandler sendHandler;


    public GuildMusicManager(AudioPlayerManager manager) {
        this.audioPlayer = manager.createPlayer();
        this.scheduler = new TrackScheduler(this.audioPlayer);
        this.audioPlayer.addListener(this.scheduler);
        this.sendHandler = new AudioPlayerSendHandler(this.audioPlayer);
    }

    /**
     * Получает обработчик отправки аудио.
     *
     * @return экземпляр {@link AudioPlayerSendHandler}
     */
    public AudioPlayerSendHandler getSendHandler() {
        return this.sendHandler;
    }
}
